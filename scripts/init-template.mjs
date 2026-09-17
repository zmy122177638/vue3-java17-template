#!/usr/bin/env node
/**
 * 模板初始化：把模板自带的标识一次性替换成你自己项目的标识。
 *
 * 为什么需要脚本：模板标识散落在 60+ 个文件里，包括
 *   · 包名 com.zmy.admin 与包目录
 *   · pom.xml 的 groupId / artifactId / name
 *   · @ConfigurationProperties(prefix = "admin") 与 YAML 中对应的 admin: 键
 *   · refresh Cookie 名、数据库名、docker-compose 的容器名与卷名
 *   · 前端 VITE_APP_NAMESPACE / VITE_APP_TITLE
 *   · README.md / CODEBUDDY.md 文档
 * 手工替换必漏，而漏掉「包名」或「YAML 前缀」的直接后果是启动即失败
 * （前者编译不过，后者配置绑定失败）。
 *
 * 用法：
 *   node scripts/init-template.mjs                                   # 交互式
 *   node scripts/init-template.mjs --group-id com.acme --artifact-id shop --yes
 *   node scripts/init-template.mjs --group-id com.acme --artifact-id shop --dry-run
 *
 * 建议在**全新 clone、尚未写业务代码**时执行，执行后立刻 `git diff` 复核。
 * 脚本不碰 .git，所以改错了可以直接 `git checkout .` 回滚。
 */

import { randomBytes } from 'node:crypto';
import {
  mkdir,
  readdir,
  readFile,
  rename,
  rm,
  stat,
  writeFile,
} from 'node:fs/promises';
import path from 'node:path';
import { argv, exit, stdin, stdout } from 'node:process';
import { createInterface } from 'node:readline/promises';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

/**
 * 模板自身的标识（改这些变量没有意义，它们是"被替换的目标"）。
 *
 * ⚠️ 这里的值必须与仓库实际内容**逐字符一致**，否则替换会静默不命中
 * （脚本不报错，只把仓库改成半成品）。执行前由 assertTemplateTargetsExist()
 * 强制校验；改动仓库标识（包名、namespace 等）时务必同步这里。
 */
const TEMPLATE = {
  /** 后端包名，同时决定包目录的最后两段 */
  pkg: 'com.zmy.yorvix',
  /** Maven groupId（pkg 的前缀） */
  groupId: 'com.zmy',
  /** 项目标识：包名末段、artifactId、DB 名、Cookie 名、容器/镜像名前缀 */
  artifact: 'yorvix',
  /** 前端应用标题（apps/web-antd/.env 的 VITE_APP_TITLE） */
  appTitle: 'Vue3 Java17 Admin',
  /** 前端 localStorage 命名空间（apps/web-antd/.env 的 VITE_APP_NAMESPACE） */
  namespace: 'vue3-java17-admin',
  /** 后端包目录（相对仓库根） */
  pkgDir: 'apps/backend/src/main/java/com/zmy/yorvix',
  /** 后端启动类文件名 */
  appClass: 'YorvixApplication.java',
  /**
   * .env 中 store 加密密钥的「当前值」，初始化时会被替换成新的随机值。
   * ⚠️ 必须与 apps/web-antd/.env 里的 VITE_APP_STORE_SECURE_KEY 逐字符一致，
   * 否则替换不命中（由 assertTemplateTargetsExist 兜底拦下）。
   * 该值不是秘密（VITE_ 前缀会打进产物），它只需要"每个项目各不相同"。
   */
  placeholderKey: 'CI3RyONYtM78pwmHkgMNL9v7jv9S6fnj',
};

/** 这些目录/文件不参与替换（依赖、产物、锁文件） */
const SKIP_DIRS = new Set([
  '.git',
  '.idea',
  '.turbo',
  '.vscode',
  'coverage',
  'dist',
  'node_modules',
  'target',
]);
const SKIP_FILES = new Set([
  '.DS_Store',
  '.eslintcache',
  'dist.zip',
  // 本脚本自身不参与替换：它是「改名工具」而不是项目代码。
  // 改掉它里面的 TEMPLATE 常量会让脚本失去可维护性（也无法再次自检）。
  'init-template.mjs',
  'package-lock.json',
  'pnpm-lock.yaml',
]);
const BINARY_EXT = new Set([
  '.class',
  '.gif',
  '.ico',
  '.jar',
  '.jpeg',
  '.jpg',
  '.png',
  '.ttf',
  '.webp',
  '.woff',
  '.woff2',
  '.zip',
]);

const GROUP_ID_RE = /^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$/;
const ARTIFACT_ID_RE = /^[a-z][a-z0-9]*$/;

function parseArgs(args) {
  const options = {};
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === '--yes' || arg === '-y') options.yes = true;
    else if (arg === '--dry-run') options.dryRun = true;
    else if (arg === '--help' || arg === '-h') options.help = true;
    else if (arg.startsWith('--')) {
      const key = arg
        .slice(2)
        .replaceAll(/-([a-z])/g, (_, c) => c.toUpperCase());
      options[key] = args[++i];
    }
  }
  return options;
}

function printHelp() {
  stdout.write(`
用法: node scripts/init-template.mjs [选项]

  --group-id <id>     Maven groupId，至少两段，如 com.acme
  --artifact-id <id>  项目标识（小写字母数字），同时用作包名末段与 Maven artifactId，如 shop
  --app-title <title> 前端应用标题，如 "Acme Admin"
  --namespace <ns>    前端 localStorage 命名空间，如 acme-admin
  --yes, -y           跳过确认
  --dry-run           只打印将要发生的改动，不写盘
  --help, -h          显示本帮助

未提供的项会以交互方式询问。
`);
}

async function ask(question, { defaultValue, validate, errorMessage }) {
  // 非交互环境（CI、管道、被重定向的 stdin）下没有输入源，继续等会**永久挂住**——
  // 挂起的进程比一条清晰的报错难排查得多，所以这里直接取默认值或明确失败
  if (!stdin.isTTY) {
    if (defaultValue) return defaultValue;
    throw new Error(
      `非交互环境下必须通过命令行参数提供「${question}」（见 --help）`,
    );
  }
  const rl = createInterface({ input: stdin, output: stdout });
  try {
    for (;;) {
      const hint = defaultValue ? `（默认 ${defaultValue}）` : '';
      const input = await rl.question(`${question}${hint}: `);
      const answer = input.trim() || defaultValue;
      if (!answer) continue;
      if (!validate || validate(answer)) return answer;
      stdout.write(`${errorMessage}\n`);
    }
  } finally {
    rl.close();
  }
}

async function collectFiles(dir, files = []) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (SKIP_DIRS.has(entry.name)) continue;
      await collectFiles(full, files);
    } else if (entry.isFile()) {
      if (SKIP_FILES.has(entry.name)) continue;
      if (BINARY_EXT.has(path.extname(entry.name).toLowerCase())) continue;
      files.push(full);
    }
  }
  return files;
}

async function exists(target) {
  try {
    await stat(target);
    return true;
  } catch {
    return false;
  }
}

/**
 * 替换前自检：TEMPLATE 里声明的标识必须真的能在仓库中找到。
 *
 * <p><b>为什么必须有</b>：常量写错时替换会「静默不命中」——脚本不报错、照样写盘，
 * 最后得到的是半改状态。例如包名常量漂移成 {@code com.zmy.admin} 时，
 * {@code com.zmy.admin} 无人匹配、{@code com.zmy} 却被替换，于是包声明变成
 * {@code com.acme.yorvix} 而目录仍是 com/zmy/yorvix —— 编译不过，且报错完全不指向这里。
 * 宁可在此直接失败，也不要产出半成品仓库。
 */
async function assertTemplateTargetsExist() {
  const files = await collectFiles(ROOT);
  const contents = [];
  for (const file of files) {
    contents.push(await readFile(file, 'utf8'));
  }
  const joined = contents.join('\n');

  const missing = [];
  // 文本类标识：必须能在某个文件里原样找到
  const textKeys = [
    'pkg',
    'groupId',
    'artifact',
    'appTitle',
    'namespace',
    'placeholderKey',
  ];
  for (const key of textKeys) {
    if (!joined.includes(TEMPLATE[key]))
      missing.push(`${key} = ${TEMPLATE[key]}`);
  }
  // 路径类标识：包目录与启动类必须真实存在
  if (!(await exists(path.join(ROOT, TEMPLATE.pkgDir)))) {
    missing.push(`pkgDir = ${TEMPLATE.pkgDir}（目录不存在）`);
  } else if (
    !(await exists(path.join(ROOT, TEMPLATE.pkgDir, TEMPLATE.appClass)))
  ) {
    missing.push(`appClass = ${TEMPLATE.appClass}（文件不存在）`);
  }

  if (missing.length > 0) {
    throw new Error(
      `以下模板标识在仓库中找不到，替换会静默失效：\n  ${missing.join('\n  ')}\n` +
        '请先让 TEMPLATE 常量与仓库实际内容一致（见该常量的注释），再执行本脚本。',
    );
  }
}

/**
 * 替换后复核：仍含原标识的文件清单。
 * 只用于提示人工确认，不影响退出码（文档里描述历史改名过程的文字属正常残留）。
 */
async function findLeftoverArtifactFiles() {
  const files = await collectFiles(ROOT);
  const leftovers = [];
  for (const file of files) {
    const content = await readFile(file, 'utf8');
    if (
      content.includes(TEMPLATE.artifact) ||
      content.includes(pascalOf(TEMPLATE.artifact))
    ) {
      leftovers.push(path.relative(ROOT, file));
    }
  }
  return leftovers;
}

/** 首字母转大写：yorvix -> Yorvix */
function pascalOf(value) {
  return value.charAt(0).toUpperCase() + value.slice(1);
}

/**
 * 构建替换规则。
 *
 * <p><b>顺序重要</b>：必须先替换最长的 {@code com.zmy.yorvix}，再替换 {@code com.zmy}，
 * 最后才轮到独立的 {@code yorvix}——否则 {@code com.zmy.yorvix} 会先被拆成
 * {@code com.zmy.shop}。{@code Yorvix} 与 {@code yorvix} 大小写敏感、互不干扰。
 *
 * <p><b>刻意不替换的标识</b>（历史版本曾在这里，已移除）：
 * {@code ADMIN} 与 {@code admin}。它们属于代码语义而非项目标识——
 * {@code ADMIN} 是 RBAC 内置角色码（{@code @RequiresRoles} 与种子数据共用），
 * {@code admin} 是种子账号名，跟着项目改名会破坏内置角色语义与登录说明
 * （见 CODEBUDDY.md「ADMIN 为内置角色，禁止删除/改码逻辑」）。
 */
function buildReplacements({ groupId, artifactId, appTitle, namespace }) {
  const pascal = pascalOf(artifactId);
  return [
    [TEMPLATE.pkg, `${groupId}.${artifactId}`],
    [TEMPLATE.groupId, groupId],
    // 项目标识：一条覆盖 spring.application.name、yorvix: 配置前缀、
    // @ConfigurationProperties 的 prefix、refresh Cookie 名、compose 容器名与卷名、
    // 数据库名、镜像名；下一行负责 YorvixApplication 这类首字母大写的形态
    [TEMPLATE.artifact, artifactId],
    [pascalOf(TEMPLATE.artifact), pascal],
    [TEMPLATE.appTitle, appTitle],
    [TEMPLATE.namespace, namespace],
    // .env 里的前端 store 加密密钥：用随机值替掉占位符，避免使用者忘记改
    [TEMPLATE.placeholderKey, randomBytes(24).toString('base64url')],
  ];
}

async function replaceInFiles(replacements, dryRun) {
  const files = await collectFiles(ROOT);
  const changed = [];
  for (const file of files) {
    const original = await readFile(file, 'utf8');
    let updated = original;
    for (const [from, to] of replacements) {
      if (updated.includes(from)) updated = updated.split(from).join(to);
    }
    if (updated === original) continue;
    changed.push(path.relative(ROOT, file));
    if (!dryRun) await writeFile(file, updated, 'utf8');
  }
  return changed;
}

/**
 * 需要同步改名的包根目录。
 *
 * <p><b>为什么 main 与 test 都要处理</b>：{@code src/test/java} 下有同样深度的包目录
 * （测试类与被测类同包）。只移动 main 时，测试文件的 package 声明已被替换成新包名，
 * 而目录仍停在旧路径——Maven 编译仍能通过（javac 不校验源文件路径与包名一致），
 * 但 IDE 会报 "package name does not correspond to file path"，
 * 且目录结构会误导后来人。
 */
const PACKAGE_ROOTS = [
  'apps/backend/src/main/java',
  'apps/backend/src/test/java',
];

/** 在所有包根上执行「TEMPLATE.pkg → 目标包名」的目录重命名（不存在的包根会被跳过） */
async function movePackageDirs({ groupId, artifactId }, dryRun) {
  const from = TEMPLATE.pkg.split('.');
  const to = [...groupId.split('.'), artifactId];
  const moved = [];
  for (const root of PACKAGE_ROOTS) {
    const source = path.join(ROOT, root, ...from);
    if (!(await exists(source))) continue;
    const target = path.join(ROOT, root, ...to);
    if (!dryRun) {
      await mkdir(path.dirname(target), { recursive: true });
      await rename(source, target);
      // 清理 com/zmy 这类残留空目录
      await pruneEmptyDirs(path.join(ROOT, root));
    }
    moved.push({
      source: path.join(root, ...from),
      target: path.join(root, ...to),
    });
  }
  return moved;
}

/** 清理空目录时可忽略的噪音文件：macOS 的 .DS_Store 会让"空目录"看起来非空 */
const NOISE_FILES = new Set(['.DS_Store']);

async function pruneEmptyDirs(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const full = path.join(dir, entry.name);
    await pruneEmptyDirs(full);
    // 「只含 .DS_Store」同样视为空目录：否则 macOS 上重命名包之后
    // 会留下一串只含 .DS_Store 的旧包路径（形如 com/zmy/admin/...）。
    const names = await readdir(full);
    const remaining = names.filter((name) => !NOISE_FILES.has(name));
    if (remaining.length === 0) await rm(full, { recursive: true });
  }
}

async function renameApplicationClass(artifactId, dryRun) {
  const pascal = artifactId.charAt(0).toUpperCase() + artifactId.slice(1);
  const packageSegments = await readPackageDir();
  const dir = path.join(ROOT, 'apps/backend/src/main/java', ...packageSegments);
  const source = path.join(dir, TEMPLATE.appClass);
  const target = path.join(dir, `${pascal}Application.java`);
  if (source === target) return null;
  if (!dryRun) await rename(source, target);
  return { source: TEMPLATE.appClass, target: `${pascal}Application.java` };
}

/** 包目录在 movePackageDir 之后才确定，这里按当前实际位置查找 */
async function readPackageDir() {
  const base = path.join(ROOT, 'apps/backend/src/main/java');
  const found = await findPackageDirWithAppClass(base);
  if (found) return found;
  return TEMPLATE.pkgDir.split('/').slice(-2);
}

/**
 * 递归查找"含 *Application.java 的目录"，返回相对 base 的路径段。
 *
 * <p><b>为什么必须递归而不是写死层数</b>：包名是 com.zmy.yorvix 这样的三层。
 * 早先的实现只取两层（返回 ['com','zmy']），于是启动类路径被算成
 * java/com/zmy/YorvixApplication.java——文件不存在，重命名必然失败且提示晦涩。
 * 以「目录下存在 *Application.java」为唯一判据后，包有几层都不受影响。
 */
async function findPackageDirWithAppClass(dir, segments = []) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const next = [...segments, entry.name];
    const full = path.join(dir, entry.name);
    const names = await readdir(full);
    if (names.some((name) => name.endsWith('Application.java'))) return next;
    const deeper = await findPackageDirWithAppClass(full, next);
    if (deeper) return deeper;
  }
  return null;
}

async function main() {
  const options = parseArgs(argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const groupId =
    options.groupId ??
    (await ask('后端包名前缀 groupId（如 com.acme）', {
      validate: (v) => GROUP_ID_RE.test(v),
      errorMessage: '格式不合法：需为小写点分段，至少两段，如 com.acme',
    }));

  const artifactId =
    options.artifactId ??
    (await ask('项目标识 artifactId（如 shop）', {
      validate: (v) => ARTIFACT_ID_RE.test(v),
      errorMessage:
        '格式不合法：仅小写字母与数字，且以字母开头（如 shop、myapp）',
    }));

  const appTitle =
    options.appTitle ??
    (await ask('前端应用标题', {
      defaultValue: `${artifactId} Admin`,
    }));

  const namespace =
    options.namespace ??
    (await ask('前端 localStorage 命名空间', {
      defaultValue: `${artifactId}-admin`,
    }));

  if (!GROUP_ID_RE.test(groupId) || !ARTIFACT_ID_RE.test(artifactId)) {
    stdout.write('groupId / artifactId 格式不合法，已中止。\n');
    exit(1);
  }

  const context = { groupId, artifactId, appTitle, namespace };
  const pkg = `${groupId}.${artifactId}`;
  const pascal = pascalOf(artifactId);

  // 先自检再打印/写盘：常量与实际对不上时立刻失败，避免产出半改仓库
  await assertTemplateTargetsExist();

  stdout.write(`
即将把模板标识替换为：
  groupId      ${TEMPLATE.groupId} -> ${groupId}
  项目标识     ${TEMPLATE.artifact} -> ${artifactId}
  后端包名     ${TEMPLATE.pkg} -> ${pkg}
  应用标题     ${TEMPLATE.appTitle} -> ${appTitle}
  命名空间     ${TEMPLATE.namespace} -> ${namespace}
  包目录       ${TEMPLATE.pkgDir} -> apps/backend/src/main/java/${groupId.replaceAll('.', '/')}/${artifactId}
  启动类       ${TEMPLATE.appClass} -> ${pascal}Application.java
  （ADMIN 角色码与 admin 账号名刻意保留，不参与替换）
${options.dryRun ? '\n[dry-run] 只打印，不写盘\n' : ''}`);

  if (!options.yes && !options.dryRun) {
    const confirm = await ask('确认执行？输入 yes 继续', {
      defaultValue: 'no',
    });
    if (confirm.toLowerCase() !== 'yes') {
      stdout.write('已取消。\n');
      return;
    }
  }

  const replacements = buildReplacements(context);
  const changed = await replaceInFiles(replacements, options.dryRun);
  const movedDirs = await movePackageDirs(context, options.dryRun);
  const movedClass = await renameApplicationClass(artifactId, options.dryRun);
  // dry-run 时磁盘上仍是原标识，复核没有意义，直接跳过
  const leftovers = options.dryRun ? [] : await findLeftoverArtifactFiles();

  stdout.write(`
完成${options.dryRun ? '（dry-run，未写盘）' : ''}：
  改写文件 ${changed.length} 个
${changed.map((f) => `    · ${f}`).join('\n')}
${movedDirs.map((d) => `  移动目录 ${d.source} -> ${d.target}\n`).join('')}${movedClass ? `  重命名   ${movedClass.source} -> ${movedClass.target}\n` : ''}${
    leftovers.length > 0
      ? `\n⚠️ 仍含原标识「${TEMPLATE.artifact}」的文件（请人工确认是否漏改）：\n${leftovers.map((f) => `    · ${f}`).join('\n')}\n`
      : ''
  }
下一步：
  1. git diff 复核改动（改错了用 git checkout . 回滚，脚本不碰 .git）
  2. 按 README「快速开始」重新构建：后端 ./mvnw clean package、前端 pnpm install
  3. 若库里已有旧数据，注意数据库名随之变化（DB_NAME 默认值已改为 ${artifactId}）
`);
}

try {
  await main();
} catch (error) {
  stdout.write(
    `\n执行失败：${error instanceof Error ? error.message : String(error)}\n`,
  );
  exit(1);
}
