# CodeBuddy AI 协作规则（vue3-java17-template）

> 本文件是 AI 助手在本仓库工作的强制约定。每次生成或修改代码前必须遵守。
> 详细的项目说明、启动方式、部署清单见 `README.md`。

## 1. 项目定位

这是一个**全栈开发模板**（Monorepo），新项目以此为基础起步：

- 前端：`apps/web-antd`（Vue 3.5 + TS + Vite 8 + Ant Design Vue 4 + Tailwind CSS 4）
- 后端：`apps/backend`（Spring Boot 4.1 + Java 17 + MyBatis-Plus 3.5.17 + Redis + MySQL 8）
- `packages/`、`internal/` 是 vue-vben-admin 的官方分层与工具链，**原则上是"库"，不是业务代码**

## 2. 修改范围的硬性约束

| 区域 | 约束 |
| --- | --- |
| `packages/**`、`internal/**` | **禁止修改**，除非是明确的框架级 bug 修复或用户显式要求。业务逻辑一律写在 `apps/` 下 |
| `apps/backend-mock/**` | 已停用，不要新增/修改；仅作接口参考阅读 |
| `apps/web-antd/package.json` | `name` 字段是 `@vben/web-antd`，被根 `package.json` 的 turbo filter（`dev:antd`/`build:antd`）引用，**禁止改名** |
| `apps/web-antd/src/api/request.ts` | 请求封装核心（code:0 拆包、401 刷新 token），修改前必须理解双 token 契约 |
| `apps/web-antd/src/adapter/**` | vben 表格/表单适配层，新增页面时优先复用，不要绕过它直接裸写 antd Table/Form |
| 根 `package.json` / `pnpm-workspace.yaml` | 新增前端依赖时版本必须用 `catalog:` 协议（在 `pnpm-workspace.yaml` 的 `catalog:` 中登记），不要写死版本号 |

## 3. 前后端契约（不可破坏）

1. 统一返回体 `Result{ code, message, data, timestamp }`，**成功码是 `0`**（不是 200）。
2. 后端新增接口必须：返回 `Result.ok(...)` 或抛 `BizException(ResultCode.XXX)`；禁止手写 `Map` 返回。
3. 前端 `requestClient` 已自动拆出 `data`，业务代码拿到的直接是 data，不要再 `.data.data`。
4. accessToken 失效 → 后端返回真实 HTTP 401 → 前端拦截器自动调 `POST /api/auth/refresh`；refresh 接口返回**裸 token 字符串**（不走 Result 包装），对应前端 `baseRequestClient`，改这两处必须成对修改。
5. 认证方案是 **Opaque Token + Redis**（`security/TokenService`）：token 为 256 位随机数，状态存 Redis（`auth:access:{token}` / `auth:refresh:{token}`，TTL 即有效期）。**禁止改回 JWT 或在 token 中携带业务信息**；登出/刷新 = 删除 Redis key。
6. 认证流程：白名单在 `application.yaml` 的 `yorvix.security.white-list`；新增公开接口要加白名单，否则拦截器 401。
7. 当前用户注入：Controller 方法参数 `@CurrentUser LoginUser user`，禁止在业务代码里手动解析请求头。
8. 业务错误码统一在 `ResultCode` 枚举登记（1001-1009 已占用），不要散落魔法数字。

## 4. 后端规范（apps/backend）

- 持久层是 **MyBatis-Plus 3.5.17**（`mybatis-plus-spring-boot4-starter` + `mybatis-plus-jsqlparser`）：Mapper 写在 `mapper/` 包（`extends BaseMapper<T>`），实体写在 `model/` 包（`@TableName` + `@TableId(type = IdType.AUTO)`，字段驼峰自动映射下划线列）。
- SQL 纪律：单表 CRUD 直接用 BaseMapper 方法；条件查询用 `LambdaQueryWrapper`；复杂 SQL 用 `@Select` 注解或 XML（放 `resources/mapper/`，mapper XML namespace 对应 Mapper 接口全限定名）。
- 分页：用 `selectPage(new Page<>(page, size), wrapper)`，分页插件已在 `config/MybatisPlusConfig` 配置（改动它前先确认 `mybatis-plus-jsqlparser` 依赖在位）；对外统一转成 `common/page/PageResult`。
- 实体纪律：`created_at/updated_at` 交由数据库默认值与 `ON UPDATE` 维护，实体字段留空；不要在实体上加 MyBatis-Plus 以外的持久化注解（项目已无 JPA）。
- 分层结构：`controller → service(接口 + Impl) → mapper`；请求 DTO 放 `request/`，响应 DTO 放 `response/`。
- 表结构**唯一来源**是 `src/main/resources/schema.sql`，且必须幂等（用 `CREATE TABLE IF NOT EXISTS`）；不要开启 MyBatis-Plus/DDL 自动建表。
- 依赖纪律：认证采用自研 Opaque Token + Redis（`security/TokenService`，零 JWT/安全框架依赖）+ PBKDF2 `PasswordEncoder`。引入 Spring Security、jjwt、Sa-Token 等前必须先询问用户。
- 配置一律环境变量注入（如 `DB_HOST`、`REDIS_HOST`、`REDIS_PASSWORD`），不要把密码/密钥硬编码进代码或 yaml 的新增行。
- 异常：业务错误抛 `BizException`，由 `GlobalExceptionHandler` 统一处理；不要在 Controller 里 try-catch 后自行拼 Result。

## 5. 前端规范（apps/web-antd）

- 路径别名：用 `#/*`（对应 `apps/web-antd/src/*`），这是 package.json `imports` 配置，不要用 `@/`。
- 新增页面四件套：`src/api/<domain>.ts` + `src/views/<domain>/` + `src/router/routes/modules/<domain>.ts` + 需要时在 `locales/langs/` 补 i18n 词条。路由模块会被自动收集，无需手动注册。
- 全局状态（access/user/tab）来自 `@vben/stores`；应用级业务 store 写在 `src/store/`（Pinia setup 风格）。
- API 写法：`requestClient.get/post/put/delete`；文件上传下载等需要原始响应的场景用 `baseRequestClient`。
- 文案：用户可见文案优先走 `$t()` 国际化（`src/locales/langs/` 下 zh-CN 与 en-US 成对添加）。
- 图标使用 `@vben/icons` 导出的 lucide 图标组件。
- 通知中心接入点在 `src/layouts/basic.vue` 的 `notifications`（当前为空数组），接真实消息接口后在此填充，勿写入硬编码演示数据。
- 语言包位于 `src/locales/langs/{zh-CN,en-US}/`，由 glob 动态加载，新增 json 文件即生效，两种语言需成对添加。

## 6. UI 生成规则（强制）

每次生成或修改任何 UI 组件/页面/样式，必须同时满足：

1. **自适应**：布局覆盖 375px 移动端到 1440px+ 桌面端。用 Tailwind 响应式前缀（`sm:/md:/lg:/xl:`）、`grid-cols-1 md:grid-cols-2 xl:grid-cols-3` 降列、`flex-wrap`、`w-full + max-w-*`，窄屏不溢出。
2. **dark 模式**：颜色优先用语义色类（`text-foreground`、`text-muted-foreground`、`bg-card`、`bg-background`、`bg-muted`、`border-border`），避免只写亮色 hex。必须硬编码时配 `dark:` 变体，保证深底对比度 ≥ 4.5:1。
3. `bg-[linear-gradient(...)]` 是 background-image，dark 下需单独 `dark:bg-[linear-gradient(...)]` 覆盖；半透明背景在 dark 下适当提高不透明度。
4. 中后台页面遵循 vben 布局体系（`BasicLayout` + 菜单路由），不要自建全局壳子。

## 7. 代码风格与提交

- 格式化由仓库的 `@vben/eslint-config` / oxlint / oxfmt 托管，不要手写 `.prettierrc` 或引入新格式化工具。
- 提交信息遵循 Angular 约定：`feat(xxx): ...`、`fix(xxx): ...`（`pnpm commit` 交互式生成）。scope 用业务域（auth、order、system、frontend、backend 等）。
- 完成代码后主动运行：`pnpm lint`（前端改动）、`pnpm check:type`（涉及 TS 类型时）；后端改动提示用户 `./mvnw compile` 验证。

## 8. 常用命令速查

```bash
pnpm dev:antd            # 前端 dev（5666）
pnpm dev:backend         # 后端 dev（4838）
pnpm dev:backend:infra   # MySQL（docker compose）
pnpm build:antd          # 前端构建
pnpm lint / pnpm format  # 检查 / 格式化
pnpm check               # 循环依赖+依赖+类型+拼写
```

## 9. 典型任务的标准做法

- **新增业务模块**：按 README「新增业务模块指南」前后端成对实现，先后端（接口契约）后前端。
- **新增系统管理页（CRUD）**：表格用 `#/adapter/vxe-table` 适配器 + 后端 `PageQuery`/`PageResult` 分页契约（后端内部用 MyBatis-Plus `selectPage`）；表单用 `#/adapter/form.ts` 的适配器。
- **排查 401**：先确认接口是否该加白名单 → 再用 `redis-cli` 查 `auth:access:{token}` 是否存在（登出/过期即删除）→ 最后查用户状态。
- **升级框架**：`packages/`、`internal/` 改动需对照 vue-vben-admin 上游（v5.x），并在提交信息中注明。
