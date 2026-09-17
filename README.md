# Vue3 + Java17 全栈开发模板

基于 [vue-vben-admin v5](https://doc.vben.pro/) + Spring Boot 4（Java 17）的 Monorepo 全栈脚手架。前端开箱即得中后台管理系统（登录认证、动态路由、权限、国际化、主题），后端提供一套零第三方安全依赖的最小认证骨架（Opaque Token + Redis），可直接作为新项目的起点。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 前端框架 | Vue 3.5 + TypeScript + Vite 8（rolldown）+ Turbo + pnpm Monorepo |
| UI 库 | Ant Design Vue 4.x + Tailwind CSS 4 |
| 状态管理 | Pinia（全局 store 在 `@vben/stores`，应用级 store 在 `apps/web-antd/src/store`） |
| 后端框架 | Spring Boot 4.1.x（Java 17）、MyBatis-Plus 3.5.17（`mybatis-plus-spring-boot4-starter`）、Spring Validation |
| 数据库 | MySQL 8.4（Docker Compose 启动） |
| 缓存/会话 | Redis 7（存储 Opaque Token 会话） |
| 认证方案 | Opaque Token + Redis 双 token：accessToken（2h）+ refreshToken（7d，HttpOnly Cookie），无 JWT、无签名密钥 |
| 工程化 | ESLint + oxlint + oxfmt + stylelint、lefthook、commitlint、cspell |

## 目录结构

```
.
├── apps/
│   ├── web-antd/            # 前端应用（唯一业务入口）
│   │   ├── .env*            # 环境变量（见下文）
│   │   ├── vite.config.ts   # dev proxy: /api -> http://localhost:4838/api
│   │   └── src/
│   │       ├── api/         # API 封装（core/ 认证与用户、system/ 系统管理、request.ts）
│   │       ├── adapter/     # vben 表格/表单适配层（vxe-table.ts / form.ts）
│   │       ├── components/  # 业务组件（i18n-input.vue：菜单多语言 title 录入）
│   │       ├── layouts/     # 布局（basic.vue / auth.vue）
│   │       ├── locales/     # 语言包（langs/{zh-CN,en-US}/ 成对添加）
│   │       ├── router/      # 路由 + 权限守卫（guard.ts / access.ts / home-path.ts）
│   │       ├── store/       # 应用级 Pinia store（auth.ts）
│   │       ├── views/       # 页面：_core（核心页）/ dashboard（本地固定路由）/ system（系统管理）；业务页按域建目录
│   │       │   └── system/  # 用户/角色/菜单/日志管理（vxe-table 适配器 + 分页 + 抽屉表单）
│   ├── backend/             # Java 后端（Spring Boot，端口 4838）
│   │   ├── docker-compose.yml   # MySQL 8.4 + Redis 7
│   │   ├── .env.example         # 环境变量清单（Spring Boot 不自动加载，用法见文件头）
│   │   └── src/main/
│   │       ├── java/com/zmy/yorvix/
│   │       │   ├── common/      # Result / ResultCode(i18n key) / BizException / GlobalExceptionHandler / 分页(PageQuery,PageResult) / permission(AuthCodes) / i18n(Messages)
│   │       │   ├── config/      # TokenProperties / MenuProperties / MybatisPlusConfig / MessageSourceConfig / WebMvcConfig / JacksonConfig / TraceIdFilter / OpenApiConfig / DataInitializer / StartupChecks
│   │       │   ├── controller/  # AuthController / MenuController / UserInfoController（+ system/：用户/角色/菜单/日志）
│   │       │   ├── model/ mapper/   # 实体与 Mapper：sys/（SysUser，认证链路高频使用）+ system/（其余治理实体）
│   │       │   ├── request/ response/  # DTO 按域分：auth/ + system/
│   │       │   ├── security/    # TokenService（Opaque Token + Redis）/ WhiteList / AuthInterceptor / AuthContextFilter / AuthContext / LoginUser / CurrentUserArgumentResolver / LoginAttemptGuard / OperationLogInterceptor / ClientInfoResolver / PasswordEncoder / @RequiresRoles / @RequiresPermissions
│   │       │   ├── service/     # 按权限域分：system（ADMIN 独占 + AuditLogService）/ user（本人资料）/ auth（认证）
│   │       │   └── util/annotation/  # @CurrentUser（Controller 参数注入当前登录用户）
│   │       └── resources/
│   │           ├── application.yaml / application-prod.yaml
│   │           ├── i18n/messages*.properties  # 后端消息（错误提示 + 校验消息）
│   │           └── db/migration/  # Flyway 迁移（表结构唯一来源；V1__baseline.sql 为基线，禁止修改）
│   └── backend-mock/        # Nitro mock 服务（已停用，保留作为接口参考实现）
├── packages/                # vben 分层共享包（@core / effects / business），一般不需要修改
├── internal/                # 构建工具链（vite-config、各 lint 配置、tsconfig、tailwind-config）
├── scripts/                 # 脚本工具
├── turbo.json / pnpm-workspace.yaml
└── CODEBUDDY.md             # AI 协作规则（重要，开发前必读）
```

## 环境要求

- Node.js `^22.18.0 || ^24.12.0`，pnpm `>= 11`（启用 corepack）
- JDK 17、Maven（后端自带 `mvnw`）
- Docker（用于启动 MySQL）

## 初始化：换成你自己的项目标识

模板标识（包名 `com.zmy.yorvix`、artifactId、应用标题、localStorage 命名空间）散落在 60+ 个文件里，手工替换必漏——**漏掉包名编译不过，漏掉 YAML 前缀则启动即报配置绑定失败**。用初始化脚本一次改完：

```bash
# 交互式
node scripts/init-template.mjs

# 或直接给参数（便于脚本化）
node scripts/init-template.mjs --group-id com.acme --artifact-id shop --yes

# 先看看会改哪些文件，不写盘
node scripts/init-template.mjs --group-id com.acme --artifact-id shop --dry-run
```

脚本会：替换全部文本标识 → 重命名后端包目录 → 重命名启动类 → 给 `.env` 的 store 密钥填入随机值。它**不碰 `.git`**，改错了 `git checkout .` 即可回滚。建议在全新 clone、尚未写业务代码时执行。

若要手动替换，至少覆盖这些位置（`grep -ri yorvix .` 能列全）：

| 位置 | 内容 |
| --- | --- |
| `pom.xml` | `groupId` / `artifactId` / `name` |
| 包目录与 `package` 声明 | `com/zmy/yorvix/` |
| `application.yaml` | `spring.application.name`、`yorvix:` 前缀（**必须**与 `TokenProperties`/`MenuProperties` 的 `prefix` 一致） |
| `AuthController.REFRESH_COOKIE` | `yorvix_refresh_token` |
| `docker-compose.yml` / `Dockerfile` | 容器名、卷名、库名、镜像名 |
| 前端 `.env*` | `VITE_APP_TITLE`、`VITE_APP_NAMESPACE`、`VITE_APP_STORE_SECURE_KEY` |
| `README.md` / `CODEBUDDY.md` | 文档中的示例名 |

## 快速开始

```bash
# 1. 安装依赖（仅首次）
pnpm install

# 2. 启动 MySQL（后端基础设施）
pnpm dev:backend:infra

# 3. 启动后端（端口 4838）
pnpm dev:backend

# 4. 另开终端，启动前端（端口 5666）
pnpm dev:antd
```

访问 `http://localhost:5666`，默认种子账号（可通过 `yorvix.init.seed-enabled=false` 关闭）：

| 账号             | 角色                      | 权限效果                   |
| ---------------- | ------------------------- | -------------------------- |
| `admin / 123456` | ADMIN（内置管理员）+ 超管 | 全部菜单；所有接口全量放行 |

模板**不预置业务角色与业务菜单**——`ADMIN` 是代码层全量放行、看不到授权效果。要验证 RBAC（菜单可见性、按钮权限码、接口 403），请按「新增业务模块指南」自己建一个普通角色。

## 环境变量

### 前端 `apps/web-antd/.env*`

| 变量 | 说明 |
| --- | --- |
| `VITE_GLOB_API_URL` | API 基地址。开发为 `/api`（走 vite proxy）；生产为 `/api`（需 Nginx 反代） |
| `VITE_NITRO_MOCK` | dev 时是否拉起 mock 服务，已固定 `false`（使用真实后端） |
| `VITE_ROUTER_HISTORY` | 路由模式，生产默认 `history`（Nginx 需配 try_files） |
| `VITE_APP_STORE_SECURE_KEY` | 全局 store 持久化加密密钥，**新项目必须更换** |
| `VITE_APP_TITLE` / `VITE_APP_NAMESPACE` | 应用标题与命名空间 |

> 上表只列常用变量，前端的实际取值以 `apps/web-antd/.env`、`.env.development`、`.env.production`、`.env.analyze` 为准（Vite 按环境自动加载）。后端完整清单（含每个变量的用途与坑）见 `apps/backend/.env.example`；注意 Spring Boot **不会**自动加载 `.env` 文件，其用法见该文件头注释。

### 后端 `apps/backend/src/main/resources/application.yaml`

| 配置 | 说明 | 默认值 |
| --- | --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | 数据库连接（环境变量注入） | localhost / 3306 / yorvix |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接（令牌会话存储） | localhost / 6379 / 空 |
| `yorvix.token.expire-minutes` | accessToken 有效期（同时是 Redis TTL） | 120 |
| `yorvix.token.refresh-expire-minutes` | refreshToken 有效期 | 10080（7 天） |
| `yorvix.security.white-list` | 免认证接口白名单。**精确匹配**，要放开整棵子树需显式写 `/**`（如 `/api/public/**`）。前三项（login/refresh/logout）缺失会导致系统不可用，`StartupChecks` 会拒绝启动 | /api/auth/login、refresh、logout、/error |
| `CORS_ALLOWED_ORIGINS` | 允许跨域的来源（逗号分隔）。**留空 = 不注册 CORS，只允许同源请求** | dev `*`；prod 留空 |
| `COOKIE_SECURE` | refresh Cookie 是否带 `Secure` 标志（HTTPS 必须开启） | dev false；prod true |
| `SEED_ENABLED` | 是否创建种子账号 `admin`（超管） | dev true；prod false |
| `SEED_ADMIN_PASSWORD` | 种子超管账号的初始密码 | 123456（仅供本地开发） |
| `VERIFY_SCHEMA` | 启动时是否校验库表结构与实体一致 | true |
| `MENU_HOME_PATH` / `MENU_ADMIN_ONLY_PATHS` | 登录后统一首页 / ADMIN 独占菜单前缀 | 空 / `/system` |
| `LOGIN_MAX_ATTEMPTS` / `LOGIN_LOCK_MINUTES` | 同一账号或同一 IP 的连续登录失败上限 / 锁定时长（分钟），任一维度达到上限即拒绝登录 | 5 / 15 |
| `CLIENT_IP_HEADER` | 取真实客户端 IP 的请求头名（如 Nginx 的 `X-Real-IP`），**留空 = 用 `remoteAddr`**。见下方「安全提示」 | 空 |

### 环境配置分层（本地开发 vs 生产）

`application.yaml` 是**面向开箱即用的本地开发**默认值；生产必须激活 prod profile：

```bash
SPRING_PROFILES_ACTIVE=prod java -jar app.jar
```

`application-prod.yaml` 会关闭种子账号、关闭接口文档（`springdoc.*.enabled=false`）、把日志收紧到 info，并要求显式配置跨域来源。**忘记激活时不会静默出事**——启动日志会打出醒目告警并列出应设置的变量：

```
============ 检测到「本地开发默认配置」仍在生效 ============
  · 种子数据初始化已开启：连上全新库会创建 admin 账号（生产请设 SPRING_PROFILES_ACTIVE=prod 或 SEED_ENABLED=false）
  · 种子账号仍使用默认密码，请勿用于生产（可用 SEED_ADMIN_PASSWORD 注入强口令）
  · CORS 允许任意来源且携带凭证：仅适用于本地联调（...）
  · refresh Cookie 未启用 Secure 标志：仅适用于 http 本地开发（...）
```

> ℹ️ 表结构由 Flyway 在启动时自动迁移（dev/prod 都是），**不再需要手工建表**。迁移文件位于 `src/main/resources/db/migration/`。

### 表结构演进（Flyway）

表结构的**唯一来源**是 Flyway 迁移文件（`src/main/resources/db/migration/`），应用启动时自动执行：

| 文件 | 说明 |
| --- | --- |
| `V1__baseline.sql` | 基线：全部 `sys_*` 表。**发布后禁止修改**（Flyway 校验 checksum，改动会直接拒绝启动），语句保持 `CREATE TABLE IF NOT EXISTS` 幂等 |
| `V2__xxx.sql` / `V3__xxx.sql` … | 之后每一次结构变更**新增一个文件**（`ALTER TABLE ... ADD COLUMN` 等），不要改历史文件 |

存量库（表已存在但没有 `flyway_schema_history`）的兼容方式：`baseline-on-migrate=true` + `baseline-version=0` 先打一个版本 0 的基线，再执行 V1；V1 内的语句是幂等的，因此已有的表原样跳过、缺失的表（如审计日志表）被补齐。

> 注意 `baseline-version` 必须是 `0`（不是默认的 `1`），否则 V1 会被判定为"低于基线"而跳过。

### 启动自检（结构不一致时拒绝启动）

Flyway 管住了迁移，`StartupChecks` 作为最后一道防线：启动时比对 MyBatis-Plus 实体与实际库表，缺表/缺列直接**中断启动**并给出指引（避免"登录正常、登录后所有接口 500"这类极难定位的运行期故障）：

```
================ 表结构自检未通过，应用拒绝启动 ================
  - 表 sys_user 缺少列: [token_version]
```

常见原因：改了实体但没写新的迁移文件（补一个 `V2__xxx.sql` 即可）、手工改过数据库、或删掉了 `flyway_schema_history`。跳过方式：`VERIFY_SCHEMA=false`（不推荐）。注意自检覆盖的是**列**，不校验索引。

### 健康检查与接口文档

| 路径 | 说明 |
| --- | --- |
| `GET /actuator/health` | 存活/就绪探针（只暴露 `health,info`，且 `show-details=never` 不泄露组件细节） |
| `GET /actuator/info` | 应用信息 |
| `GET /swagger-ui.html` / `GET /v3/api-docs` | 可交互接口文档（含 Authorize 按钮，填 accessToken 即可调试） |

> ⚠️ `/actuator/**`、`/swagger-ui.html`、`/v3/api-docs` **不在 `/api/**` 下，因此不受 `AuthInterceptor` 保护**。生产 profile 已整体关闭接口文档；健康检查保持开启（探针需要），但不展示细节。

## 认证机制

Opaque Token + Redis 设计（token 本体是 256 位随机数的 base64url 编码，不携带业务信息，状态全部在 Redis），前后端契约如下：

1. `POST /api/auth/login` → 返回 `Result{code:0, data:{accessToken, refreshToken, expiresIn, user}}`；同时 refreshToken 写入 HttpOnly Cookie（`yorvix_refresh_token`，path=/api/auth）。
2. Redis 存储映射：`auth:access:{token}` / `auth:refresh:{token}` → `{userId, username, nickname, tokenVersion}`，TTL 即令牌有效期，过期自动清除。
3. 前端存 accessToken，请求自动带 `Authorization: Bearer <token>`；拦截器以「Redis 中是否存在对应 key」判定有效性（登出即删除，跨实例立即生效）。
4. accessToken 失效返回真实 HTTP 401 → 前端拦截器自动 `POST /api/auth/refresh`（Cookie 携带 refreshToken，后端返回**裸 token 字符串**，非 Result 包装），刷新时**轮换**旧 refreshToken（旧的立即删除）。
5. 登出 `POST /api/auth/logout` → 删除 access/refresh token 对应的 Redis key（无需黑名单表，无定时清理任务）。**前端必须在请求头带上当前 accessToken**（`api/core/auth.ts` 的 `logoutApi` 已带）：接口本身在白名单里（令牌过期也要能登出），但"删除服务端令牌"依赖请求头里的令牌，漏带就会退化成"界面登出了、旧 accessToken 仍有效到 TTL 结束"。
6. 认证链路：白名单 → Redis 校验 → 用户存在/启用校验 → **令牌版本校验** → 写入 ThreadLocal `AuthContext`，业务代码可用 `@CurrentUser` 注入当前用户。
7. **批量踢下线**：把 `sys_user.token_version` +1 即作废该用户**全部**令牌，不必等 TTL 过期。触发点有三处：管理员重置密码、本人修改密码（改完需用新密码重新登录）、账号被禁用。校验同时发生在拦截器与 `POST /api/auth/refresh` 两处，因此旧 refreshToken 也无法换出新 accessToken。

> 相比 JWT 的优势：令牌可随时吊销（删除 key 即刻全网生效）、可一次性踢掉某用户全部会话、无签名密钥泄露风险；代价是每次请求多一次 Redis 查询（单次 GET，可接受）。令牌版本校验复用拦截器本来就要查的用户行，不产生额外 I/O。

### 登录失败限流

登录是唯一的匿名写入口，而口令校验刻意昂贵（PBKDF2 十万次迭代）——不限流等于把 CPU 与数据库连接交给攻击者。`security/LoginAttemptGuard` 按**两个维度**计数，任一维度达到 `LOGIN_MAX_ATTEMPTS`（默认 5）即拒绝登录，锁定时长 `LOGIN_LOCK_MINUTES`（默认 15 分钟）：

| 维度    | Redis key                         | 拦的是                   |
| ------- | --------------------------------- | ------------------------ |
| 账号    | `auth:login:fail:user:{username}` | 针对某个账号的持续爆破   |
| 来源 IP | `auth:login:fail:ip:{ip}`         | 换账号名继续试的撞库行为 |

- 限流检查在**查库与校验口令之前**执行，否则挡不住 CPU 消耗；
- 计数在首次失败时写入 TTL（固定窗口，不会因持续尝试而无限顺延），登录成功即清零；
- 被限流时返回业务码 `1010`（HTTP 200，避免触发前端的令牌刷新链路），文案为「登录失败次数过多，请 N 分钟后重试」。

**安全提示**：`CLIENT_IP_HEADER` 只有在应用确实位于**可信反向代理**之后才可配置（如 Nginx 的 `X-Real-IP`）。留空时用 `remoteAddr`（永远可信）；配置后取该头的**最后一个**值——`X-Forwarded-For` 由代理追加对端地址，取第一个恰是请求方可以伪造的那个（见 `security/ClientInfoResolver` 的注释）。

### 审计日志

| 表 | 写入方 | 记录口径 |
| --- | --- | --- |
| `sys_login_log` | `AuthServiceImpl` | 登录成功 / 凭据错误 / 账号禁用 / 被限流 / 登出，含账号、IP、User-Agent、`trace_id` |
| `sys_oper_log` | `security/OperationLogInterceptor` | `/api/system/**` 下的非 GET 请求，**成功与失败都记**（含耗时、HTTP 状态、失败原因的 i18n key、操作对象、`trace_id`） |

- 两张表都记录 **`trace_id`**（与 `X-Request-Id`、应用日志里的 `[traceId]` 同值，从 MDC 取），排查时直接拿它就能把一条审计记录与那次请求的完整日志对上——不必再用时间戳猜；
- `sys_oper_log.target_id` 是审计要求的**客体标识**：操作对象的主键（`module=users` + `target_id=3` 即"用户 3"），由 `OperationLogInterceptor` 从路径解析（`/api/system/users/3/status` → `3`）；新建、或 `/menus/sort` 这类无 id 的动作为空。**对象类型复用 `module` 列**，不另设 `target_type` 冗余列。新增写接口时保持 `/api/system/{资源}/{id}/...` 的路径形态，`target_id` 才能被正确解析；
- 查询页：系统管理 → 日志管理 →「登录日志 / 操作日志」（ADMIN 独占，只读，接口层不提供写入能力）；
- 写入采用**独立事务**（`REQUIRES_NEW`）：业务失败回滚时审计记录仍然保留——"谁能把越权尝试的痕迹一起回滚掉"正是审计要防的事；
- 判定业务成败的桥接点：`GlobalExceptionHandler` 把失败原因的 i18n key 写入请求属性，`OperationLogInterceptor` 据此区分"改成了"与"业务校验不通过"（后者是 HTTP 200 + 业务码，仅看状态码区分不出来）；
- 审计写入失败**不阻断业务**，但会打 ERROR 日志（需纳入告警，否则"审计表悄悄停止增长"没人发现）。

### 账号枚举防护

登录失败时，**账号不存在**与**密码错误**返回完全一致的响应（相同的 code 与文案），且账号不存在时也会用一条随机哑散列做等价计算（`AuthServiceImpl#dummyPasswordHash`），消除响应时间差异。"账号已被禁用"的提示放在口令校验**之后**——此时调用方已证明自己知道口令，提示不再泄露信息。

### 为什么禁用要递增令牌版本

拦截器已经会校验 `status`，但那只在**禁用期间**生效：重新启用后，禁用前签发的令牌会"复活"。递增令牌版本让它们永久失效，因此禁用与重新启用是两次独立的状态变更，不会意外恢复旧会话。

## 权限系统（RBAC + 菜单动态下发）

访问控制分**三个域**，接口路径与鉴权方式一一对应：

| 域 | 路径 | 鉴权方式 | 参与 RBAC 授权 |
| --- | --- | --- | --- |
| 系统管理域 | `/api/system/**` | 类级 `@RequiresRoles("ADMIN")` 一刀切 | 否（授权树里不展示） |
| 业务域 | 业务路径（如 `/api/reports`） | 方法级 `@RequiresPermissions("Report:Export")` | 是（菜单可见性 + 按钮权限码） |
| 本人域 | `/api/user/**` | 只需登录；操作对象恒为当前登录用户 | 否 |

两个注解都是**接口层的安全边界**。前端 `v-access:code` / `VbenTableAction.auth` 只是同一份权限码的 UI 呈现——隐藏按钮不等于拒绝请求，手工构造请求同样会被后端拦下（403）。

### 模式与数据模型

- 路由模式为 **mixed**：Dashboard 等固定页留在前端本地路由；业务/系统菜单存数据库由 `GET /api/menu/all` 按角色下发（与本地路由合并）
- 4 张表：`sys_role` / `sys_user_role` / `sys_menu`（type = CATALOG 目录 / MENU 页面 / BUTTON 按钮；title 为多语言 JSON） / `sys_role_menu`
- `ADMIN` 为内置管理员角色：代码层全量放行（不查授权表），不可删除、code 不可改；其余角色纯数据驱动（角色管理页新增即可，零代码）
- **ADMIN 独占菜单**（`/api/system/**` 对应的菜单）不可被授权。范围由 `yorvix.menu.admin-only-paths` 声明（默认 `/system`，前缀匹配、含全部后代），后端在下发的菜单树里标记 `MenuNode.adminOnly`，角色授权树据此隐藏；写授权时还会兜底剔除，防止手工构造的请求写入无效数据
- **按钮权限码只服务业务域，且是真正的接口边界**：业务接口标注 `@RequiresPermissions("Report:Export")`，拦截器**懒加载**——只有命中该注解的接口才查一次权限码，未标注的接口零额外开销——不满足返回真实 HTTP 403。系统管理域不使用权限码（ADMIN 独占 + 代码层全量放行，在那里声明 `System:User:*` 永远不会参与判定），因此模板的种子数据不含任何按钮权限码、系统管理页面也不挂 `auth`
- 权限码常量集中在 `common/permission/AuthCodes`，被 `@RequiresPermissions` 与种子数据共用，避免"注解与种子写得不一致"导致明明授权了却一直 403
- 角色授权写入是全量替换，后端会**去重 → 校验菜单存在 → 逐级补全祖先目录 → 剔除 ADMIN 独占菜单**。补全祖先是必需的：菜单 `path` 多为相对父级（如 `user`），缺父目录时下发会把子菜单当顶级路由，而 `path` 语义仍是相对的，前端会注册到错误位置

### 接口契约

| 接口 | 说明 |
| --- | --- |
| `GET /api/menu/all` | 按角色过滤的菜单树（vben `RouteRecordStringComponent` 结构），title 按 `Accept-Language` 下发对应语言 |
| `GET /api/auth/codes` | **业务域**按钮权限码集合（BUTTON 型菜单 `auth_code`，如 `Report:Export`），前端 `v-access:code` / `VbenTableAction.auth` 数据源。系统管理域不参与按钮级权限 |
| `GET /api/system/menus/tree` | 管理端全量菜单树（含按钮与禁用项），每个节点带 `adminOnly` 标记 |
| `GET /api/user/info` | 真实角色 + 动态 homePath（菜单树 sort 最小的叶子） |
| `GET/PUT /api/user/profile` | **本人资料**读写（昵称/邮箱/手机号）；不接受目标用户 ID，因此不存在"改别人资料"的越权面 |
| `/api/system/{users,roles,menus}` | 用户/角色/菜单管理 CRUD（`@RequiresRoles("ADMIN")` 保护），分页走 `PageQuery/PageResult`；菜单另有 `PUT /api/system/menus/sort` 拖拽排序、`GET/PUT /{id}/menus` 角色授权 |
| `/api/system/logs/{login,oper}/page` | 登录日志与操作日志分页查询（ADMIN 独占，只读，接口层不提供写入能力） |

### 管理端页面（参考 playground 同款交互）

- 用户管理：分页表格 + 抽屉表单（分配角色多选）+ 状态开关 + 重置密码抽屉（两次输入 + 6-64 位校验）；删除保护（不可删自己/超管，管理员之间互不可操作）
- 角色管理：分页表格 + 抽屉表单（编码创建后不可修改）；**菜单授权树形勾选抽屉**（`PUT /{id}/menus` 全量替换，树中不展示 ADMIN 独占菜单）
- 菜单管理：树形表格（目录/菜单/按钮/内嵌/外链），编辑抽屉含**多语言 title 录入组件**（`#/components/i18n-input.vue`，值即多语言 JSON）、上级菜单树选择、图标选择器、组件路径自动补全、徽标与隐藏开关等高级设置

### 新增受控资源的三步动作

1. **后端**：系统管理类接口标 `@RequiresRoles("ADMIN")`；**业务接口标 `@RequiresPermissions(...)`**（权限码常量登记到 `common/permission/AuthCodes`）
2. **前端**：`views/` 下放页面文件（component 路径即视图路径，如 `/your-domain/list/index`），按钮挂 `v-access:code` / `VbenTableAction.auth`
3. **数据**：菜单管理页新增菜单 + BUTTON 子节点（填 `auth_code`）并授权给目标角色；需要开箱即有数据时，再在 `DataInitializer` 里补一个幂等的 `seedXxx` 方法

## 前后端契约（硬性约定）

- 统一返回格式：`{ code, message, data, timestamp }`，**成功码为 `0`**（`ResultCode.SUCCESS`）。
- 业务错误码：1001 登录失败、1002 账号禁用、1003 token 无效、1005 原密码错误、1009 用户不存在、1010 登录被限流；HTTP 对齐码 400/401/403/404/405/415/500。
- `BizException` 抛出后由 `GlobalExceptionHandler` 统一转换为 Result，并**同步设置真实 HTTP 状态码**（与 HTTP 同值的返回码 + `TOKEN_INVALID`）。业务码（1000 起）一律保持 HTTP 200：前端的 401 会触发"刷新令牌/重新登录"链路，而"密码错误""被限流"绝不能走那条链路。
- **`ResultCode` 与 `BizException` 存的是 i18n key，不是文案**：`throw new BizException(ResultCode.BAD_REQUEST, "error.username.exists")`，带参数则 `(..., "error.role.disabled", roleName)`。文案在响应生成时按请求头 `Accept-Language` 解析，因此**前端不需要维护"错误码 → 文案"映射表**。
- 消息资源：`src/main/resources/i18n/messages.properties`（默认中文，同时是未知语言的兜底）与 `messages_en_US.properties`。DTO 校验消息由 Bean Validation 标准约束 key 提供，**注解上不要写 `message = "中文"`**；只有枚举型约束等确实需要自定义文案时才写 `message = "{your.key}"`。新增语言 = 复制一份 properties，无需改代码。
- 时间字段统一输出 `yyyy-MM-dd HH:mm:ss`，由 `config/JacksonConfig` 为 `LocalDateTime` 显式注册（注意 `spring.jackson.date-format` 对 `LocalDateTime` **不生效**，别被那行配置误导）。
- 每个请求都有 `X-Request-Id`（调用方可透传，后端生成时回写到响应头），写入 MDC；日志格式含 `[traceId]`，排查时用它把同一次请求的日志串起来。
- 表结构唯一来源是 `src/main/resources/db/migration/` 下的 Flyway 迁移，启动时自动执行。**加字段 = 新增一个 `V2__xxx.sql`**，不要改 `V1__baseline.sql`（checksum 校验会让应用拒绝启动）。
- 前端分页请求/响应 DTO：`common/page/PageQuery` / `PageResult`（已就绪）。

### 存量库升级（从早期版本迁移才需要）

> **全新库直接跳过本节**：`V1__baseline.sql` 已包含全部表结构，Flyway 启动时自动建好。

`V1__baseline.sql` 用 `CREATE TABLE IF NOT EXISTS`，**只建表、不给已存在的表补列**。若你的库来自早期版本（曾用 `schema.sql` 手工建表）且 `StartupChecks` 报了缺列/缺索引，按下面清单补齐一次即可。**补齐后，新增结构变更一律新建迁移文件（`V2__xxx.sql`），不要再往本节追加 `ALTER`。**

<details>
<summary><b>展开：存量库手工补齐清单</b></summary>

#### sys_menu 菜单扩展列（五类菜单）

菜单支持 `CATALOG/MENU/BUTTON/EMBEDDED/LINK` 五类，以及激活图标、徽标、隐藏开关等 meta 字段后，`sys_menu` 新增下列字段（新库由基线迁移直接建好；存量库若缺列则执行一次，重复执行前先确认列不存在）：

```sql
ALTER TABLE sys_menu
  ADD COLUMN active_icon           VARCHAR(64)  DEFAULT NULL COMMENT '激活时图标（lucide 图标名）',
  ADD COLUMN active_path           VARCHAR(128) DEFAULT NULL COMMENT '作为菜单高亮依据的目标路由路径',
  ADD COLUMN link_src              VARCHAR(255) DEFAULT NULL COMMENT '外链/内嵌地址（http(s)）',
  ADD COLUMN badge_type            VARCHAR(16)  DEFAULT NULL COMMENT '徽标类型 dot红点 normal文字',
  ADD COLUMN badge                 VARCHAR(64)  DEFAULT NULL COMMENT '徽标内容（badge_type=normal 时生效）',
  ADD COLUMN badge_variants        VARCHAR(32)  DEFAULT NULL COMMENT '徽标颜色 default/destructive/primary/success/warning',
  ADD COLUMN hide_in_menu          TINYINT      NOT NULL DEFAULT 0 COMMENT '菜单中隐藏 1是 0否',
  ADD COLUMN hide_children_in_menu TINYINT      NOT NULL DEFAULT 0 COMMENT '菜单中隐藏子级 1是 0否',
  ADD COLUMN hide_in_breadcrumb    TINYINT      NOT NULL DEFAULT 0 COMMENT '面包屑中隐藏 1是 0否',
  ADD COLUMN hide_in_tab           TINYINT      NOT NULL DEFAULT 0 COMMENT '标签栏中隐藏 1是 0否';
```

#### sys_user 超管标识列

超管（`is_super=1`）代码层全量放行、不依赖角色绑定，且不出现在用户管理列表中；新库由基线迁移直接建好，存量库若缺列则执行一次：

```sql
ALTER TABLE sys_user
  ADD COLUMN is_super TINYINT NOT NULL DEFAULT 0 COMMENT '是否超管 1是 0否（仅种子/改库维护，不出现在用户管理）';
UPDATE sys_user SET is_super = 1 WHERE username = 'admin';
```

#### sys_user 令牌版本列

用于「重置密码 / 改密 / 禁用后踢下线」（详见「认证机制」）。默认 0，加列**不会**把在线用户踢下线（旧令牌载荷缺该字段时按 0 处理，与默认值一致）：

```sql
ALTER TABLE sys_user
  ADD COLUMN token_version INT NOT NULL DEFAULT 0 COMMENT '令牌版本：递增即让该用户已签发的全部令牌失效（改密/重置密码/禁用时 +1）';
```

#### 授权表索引（sys_user_role / sys_role_menu）

`sys_user_role` 的 `uk_user_role (user_id, role_id)` 与 `sys_role_menu` 的 `uk_role_menu (role_id, menu_id)` 都只能按**前缀列**命中：按 `role_id` 统计用户绑定、按 `menu_id` 清理菜单授权时原本会全表扫描。新库由基线迁移直接建好，存量库若缺索引则执行一次（MySQL 8 不支持 `ADD INDEX IF NOT EXISTS`，重复执行会报 1061 重复键名）：

```sql
ALTER TABLE sys_user_role ADD INDEX idx_user_role_role_id (role_id);
ALTER TABLE sys_role_menu ADD INDEX idx_role_menu_menu_id (menu_id);
```

#### 存量库清理：移除曾内置的订单示例数据

模板早期版本曾内置一个订单示例（`biz_order` 表 + `Order` 菜单树 + `USER` 角色 + `user` 账号）。若你的库由那个版本启动过，**代码删掉不等于数据删掉**——种子逻辑只增不删，残留数据会让菜单/角色列表里出现无主的示例项。按需执行：

```sql
-- 1) 授权绑定（先删，避免留下指向已删菜单/角色的孤立行）
DELETE rm FROM sys_role_menu rm
  JOIN sys_menu m ON m.id = rm.menu_id
 WHERE m.name IN ('Order', 'OrderList', 'OrderQuery', 'OrderCreate', 'OrderUpdate', 'OrderDelete');

-- 2) 菜单树（含 4 个按钮）
DELETE FROM sys_menu
 WHERE name IN ('Order', 'OrderList', 'OrderQuery', 'OrderCreate', 'OrderUpdate', 'OrderDelete');

-- 3) 示例角色与账号
DELETE ur FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id WHERE r.code = 'USER';
DELETE FROM sys_role WHERE code = 'USER';
DELETE FROM sys_user WHERE username = 'user';

-- 4) 示例表
DROP TABLE IF EXISTS biz_order;
```

清理后重启即可：对应实体已随代码删除，`StartupChecks` 不会再校验它们。

</details>

### 登录首页（全局统一配置）

登录后的首页由 `yorvix.menu.home-path` 统一控制，**无需按角色配置**：

```yaml
yorvix:
  menu:
    # 留空（默认）= 不干预，回退“自动取该用户第一个可访问页面”
    # 需要统一首页时填具体地址，如 /dashboard
    home-path: ${MENU_HOME_PATH:}
```

取值优先级（在**前端**完成计算：`apps/web-antd/src/router/home-path.ts`，因为这里同时掌握真实路由表与菜单顺序）：

1. 全局配置 `yorvix.menu.home-path`（留空则跳过）；
2. 菜单树前序遍历中第一个可落地页面（节点 `path` 已由 `generateMenus` 解析为最终绝对地址；外链菜单会被跳过）；
3. 都没有则回落 `preferences.app.defaultHomePath`。

后端只下发"是否有菜单 + 全局配置"，不再做任何路径拼装。注意：全局配置不做可见性校验（它通常指向前端本地固定路由，后端菜单表里查不到），因此填写的地址需保证所有用户都能访问（`/dashboard` 是前端本地固定路由，任何登录用户可见），否则会出现 404/403；启动时会校验格式（必须以 `/` 开头且不含空格）。

### ADMIN 独占菜单（yorvix.menu.admin-only-paths）

系统管理域不参与 RBAC 授权，这个范围由配置声明（前缀匹配，命中节点的**全部后代**一并独占）：

```yaml
yorvix:
  menu:
    # 逗号分隔的路由地址前缀；默认 /system
    admin-only-paths: ${MENU_ADMIN_ONLY_PATHS:/system}
```

后端在两处复用同一配置：下发菜单树时给 `MenuNode.adminOnly` 打标记（前端角色授权树据此隐藏这些节点），写入角色授权时兜底剔除。**新增 ADMIN 独占的顶级菜单（如 `/monitor`）必须在此追加**，否则会出现"菜单能勾选授权、被分配者一访问接口就 403"的难排查问题。

存量库中已存在的 `System:*` 按钮权限码不会被自动清理（种子逻辑只增不删），它们不再参与任何判定；如需清理可执行：

```sql
DELETE FROM sys_role_menu WHERE menu_id IN (SELECT id FROM sys_menu WHERE type = 'BUTTON' AND auth_code LIKE 'System:%');
DELETE FROM sys_menu WHERE type = 'BUTTON' AND auth_code LIKE 'System:%';
```

### 账号与权限模型（超管 / 管理员 / 普通用户）

| 主体 | 产生方式 | 权限来源 |
| --- | --- | --- |
| 超管（`is_super=1`，默认 `admin`） | **仅种子 / 改库维护**，界面无入口 | 代码层全量放行，`getRoleCodes` 自动补 `ADMIN` |
| 管理员（持有内置 `ADMIN` 角色） | **仅超管**在用户管理创建账号并分配 `ADMIN` | 代码层全量放行，不读 `sys_role_menu` |
| 普通用户（持有自定义角色） | 超管 / 管理员创建并分配角色 | `sys_role_menu` 授权驱动 |

约束：

- `ADMIN` 为内置角色，不可创建/修改/删除，**仅超管可将其分配给用户**（接口 `GET /api/system/users/assignable-roles` 对非超管不返回该角色）；
- 超管账号不可被任何人操作（不可禁用/删除/改角色），本人改密码走个人中心（`PUT /api/auth/password`）；
- 持有 `ADMIN` 角色的用户仅超管可操作（管理员之间互不可操作）；
- 创建/编辑用户必须分配至少一个启用角色，避免出现“能登录但无任何权限”的账号。

## 新增业务模块指南

模板**不预置任何业务模块**（种子数据只含系统管理域）。新增业务按「先后端 → 再菜单授权 → 最后前端页面」的顺序走——前端页面的路由地址来自菜单数据，反着做会有一段"页面在但访问不到"的空窗。

### 一、后端

1. **分域建包**：`model/mapper/service/controller/request/response` 下按域建子包；业务数据建议用 `biz`（表名用 `biz_` 前缀），与平台治理数据（`sys_` / `system`）在日志、慢 SQL、误删排查时一眼区分；
2. **实体与 Mapper**：`@TableName("biz_xxx")` + `@TableId(type = IdType.AUTO)`，Mapper `extends BaseMapper<T>`；`created_at/updated_at` 交给数据库默认值与 `ON UPDATE` 维护（实体字段留空）；
3. **表结构**：新增迁移文件 `src/main/resources/db/migration/V2__your_module.sql`（建表用 `CREATE TABLE IF NOT EXISTS`，加列用 `ALTER TABLE ... ADD COLUMN`）。**不要修改 `V1__baseline.sql`**——Flyway 会校验 checksum 并拒绝启动。启动时自动执行，`StartupChecks` 会校验实体与库表一致；
4. **DTO 分层**：请求放 `request/`、响应放 `response/`，**不要直接返回实体**——否则"加一个表字段"就变成"改一次对外契约"；
5. **分页 + 搜索 + 排序**：`PageQuery.toMpPage(SORTABLE_FIELDS)`；排序白名单是安全边界（列名会拼进 `ORDER BY`），前端列的 `sortable: true` 必须与白名单一致，否则点了列头没反应；
6. **业务校验放 Service**（唯一性、状态机等），Controller 只做参数绑定与鉴权；
7. **鉴权**：业务接口逐方法标 `@RequiresPermissions`，权限码常量登记到 `common/permission/AuthCodes`；
8. **错误消息**：抛 `BizException(ResultCode.XXX)`，需要自定义文案时用 `new BizException(ResultCode.BAD_REQUEST, "error.your.key", args...)`，文案写进 `i18n/messages*.properties`（zh/en 成对加）。**不要在 Java 里写中文字面量**；
9. **补测试**：凡是"错了也不报错、只是行为悄悄不对"的逻辑（授权规范化、白名单、状态机）都要有单测，`./mvnw test` 必须全绿。

### 二、菜单与授权（这一步决定前端页面能不能被访问）

业务页面**不需要**写前端路由文件：菜单数据里的 `component` 字段就是视图路径（如 `/your-domain/list/index`）， `router/access.ts` 的 `import.meta.glob('../views/**/*.vue')` 负责把它映射到视图文件。只有"与权限无关、所有用户都该看到"的固定页才放 `src/router/routes/modules/`。

在「菜单管理」里建出这棵树：

```
/your-domain（目录，component 留空）
  └── /your-domain/list（菜单，component 填 /your-domain/list/index）
        ├── YourDomain:Query   按钮
        ├── YourDomain:Create  按钮
        └── YourDomain:Delete  按钮
```

再到「角色管理 → 菜单授权」把菜单与按钮授权给目标角色。**注意 `ADMIN` 角色无需授权**（代码层全量放行），所以要验证授权效果必须用一个非 ADMIN 角色——`admin` 登录时永远看不到"少授权"的现象。

需要开箱即有数据，就在 `DataInitializer` 里补一个 `seedXxx` 方法：**先查后插是硬要求**，否则重复启动会撞唯一键、导致启动失败。

### 三、前端

1. `src/api/<domain>/xxx.ts`：用 `requestClient` 封装接口（自动拆 `data`、带 token、统一错误提示）；分页响应映射成 vxe 需要的 `{ items, total }`；
2. `src/views/<domain>/list/`：`index.vue`（表格 + 工具栏）、`data.ts`（列与表单 schema）、`modules/*.vue`（抽屉/弹窗），可参照 `views/system/user/` 的组织方式；
3. **按钮权限**：普通按钮挂 `v-access:code`，操作列用 `VbenTableAction.auth`，字符串与后端 `AuthCodes` 一致。权限码在登录时一次性拉取（`/api/auth/codes`），**改授权后需重新登录才刷新**；
4. **语言包**：`src/locales/langs/{zh-CN,en-US}/<domain>.json` **成对添加**（文件名即 `$t` 的命名空间），文案一律走 `$t()`，不要硬编码中文。

## 构建与部署

```bash
# 前端构建（产物在 apps/web-antd/dist）
pnpm build:antd

# 后端构建
cd apps/backend && ./mvnw -DskipTests package
```

### Docker 镜像

前端（构建上下文**必须是仓库根目录**，产物路径为 `apps/web-antd/dist`）：

```bash
pnpm build:docker
# 等价于：docker build -f scripts/deploy/Dockerfile -t yorvix-web .
```

后端（构建上下文是 `apps/backend`）：

```bash
docker build -t yorvix-api apps/backend
```

后端镜像已内置 `SPRING_PROFILES_ACTIVE=prod`，请确保提供 `DB_*` / `REDIS_*` 环境变量；表结构由 Flyway 在容器启动时自动迁移，无需手工建表（迁移失败会直接拒绝启动，不会带着不确定的结构运行）。

### CI

`.github/workflows/ci.yml` 分两个 job，覆盖本地可复现的全部检查：

| Job | 步骤 |
| --- | --- |
| 前端 | `pnpm install --frozen-lockfile` → `pnpm lint` → `pnpm check:type` → `pnpm build:antd` |
| 后端 | `./mvnw -B clean test`（单元测试不依赖数据库/Redis，无需起服务容器） |

### Nginx 生产配置示例

> 打进前端镜像的 `scripts/deploy/nginx.conf` 已内置 `/api` 反代与 gzip（`gzip_static` 直接下发预压缩产物），反代目标由构建参数 `API_UPSTREAM` 注入（默认 `yorvix-api:4838`）。下面是**手工在宿主机部署**（前端静态文件由宿主机 nginx 托管）时的参考配置。

```nginx
server {
    listen 80;
    root /opt/app/dist;
    index index.html;

    # 前端 history 路由
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 反代到 Spring Boot
    location /api/ {
        proxy_pass http://127.0.0.1:4838/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

生产部署检查清单：

- [ ] **激活 prod profile**：`SPRING_PROFILES_ACTIVE=prod`（关闭种子账号、关闭接口文档 `springdoc.*.enabled=false`、日志收紧到 info、开启严格模式）
- [ ] **前端容器必须能解析到后端**：镜像构建参数 `API_UPSTREAM` 默认取后端容器名 `yorvix-api:4838`，要求前后端**在同一 Docker 网络**；容器名/服务名不同时用 `--build-arg API_UPSTREAM=<host>:4838` 重新构建；**先创建后端容器再创建前端容器**（nginx 启动时即解析上游主机名，解析不到会启动失败）
- [ ] 数据库使用**专用最小权限账号**（勿用 root）；prod 下 `DB_HOST`/`DB_USERNAME`/`DB_PASSWORD` 无默认值，缺失会拒绝启动
- [ ] 确认 `DB_SSL_MODE` 与目标库 TLS 状态匹配（prod 默认 `REQUIRED`；库未配证书时显式设 `DISABLED` 并尽快补证书）
- [ ] 表结构由 Flyway 启动时自动迁移（dev/prod 一致），**无需手工建表**；发布前确认新结构的迁移文件已随包一起提交
- [ ] 注入 `DB_HOST` / `DB_USERNAME` / `DB_PASSWORD`
- [ ] 注入 `REDIS_HOST` / `REDIS_PASSWORD`（Redis 存放会话令牌，务必设密码或内网隔离）
- [ ] HTTPS 部署时确认 `COOKIE_SECURE=true`（prod profile 默认已开）
- [ ] CORS 保持留空（Nginx 同源反代即无需跨域）；确需跨域则设 `CORS_ALLOWED_ORIGINS` 为受信域名，**不要用 `*`**
- [ ] 若应用位于 Nginx 之后：确认 `CLIENT_IP_HEADER` 与该代理实际写入的头一致（否则登录限流会把所有人算作同一个 IP）
- [ ] 存活/就绪探针接 `GET /actuator/health`（`/actuator/**` 不在鉴权范围，但只暴露 health/info 且不展示细节）
- [ ] 确认接口文档在生产不可访问（`/swagger-ui.html`、`/v3/api-docs` 应返回 404）
- [ ] 更换 `VITE_APP_STORE_SECURE_KEY`
- [ ] 启动日志中**不应出现**「检测到本地开发默认配置」告警
- [ ] 前端 `.env.production` 修改 `VITE_APP_TITLE` / favicon 等品牌信息

## 常用命令

| 命令 | 说明 |
| --- | --- |
| `pnpm dev:antd` / `pnpm dev:backend` / `pnpm dev:backend:infra` | 启动前端 / 后端 / MySQL |
| `pnpm build:antd` | 构建前端 |
| `pnpm check` | 循环依赖 + 依赖检查 + 类型检查 + 拼写检查 |
| `pnpm lint` / `pnpm format` | 代码检查 / 格式化 |
| `pnpm test:unit` | 前端单元测试（vitest） |
| `cd apps/backend && ./mvnw test` | 后端单元测试（不依赖 DB/Redis） |
| `pnpm commit` | 交互式规范提交（commitlint + czg） |

## 提交规范

遵循 Angular 约定（lefthook + commitlint 强制校验）：`feat`、`fix`、`style`、`perf`、`refactor`、`revert`、`test`、`docs`、`chore`、`ci`、`types`。

## 已知注意事项

- **Spring Boot 4.1.x** 较新（Jackson 3，包名 `tools.jackson`），如需更稳生态可降级到 3.x，注意同步调整依赖命名。
- **Boot 4 把各技术的自动配置拆成了独立模块**：引入新中间件时要用 `spring-boot-starter-xxx`（如 `spring-boot-starter-flyway`），只把第三方 jar（如 `flyway-core`）放进 classpath **不会触发自动配置，而且不报错**——症状是"启动正常，但功能没生效"。新增依赖后请验证实际行为，不要只看"编译通过"。
- **表结构变更走迁移文件**：新增 `V2__xxx.sql`，禁止修改 `V1__baseline.sql`（checksum 校验会拒绝启动）。
- **审计日志的覆盖范围**：登录相关事件（`sys_login_log`）+ `/api/system/**` 的写操作（`sys_oper_log`）。业务域如果也要审计，把路径前缀加进 `OperationLogInterceptor.AUDITED_PREFIXES`，或直接调 `AuditLogService.recordOper` 显式记录。
- **登录限流是账号 + IP 双维度**：调大 `LOGIN_MAX_ATTEMPTS` 前先想清楚"爆破成本"；`CLIENT_IP_HEADER` 只在可信代理之后配置，否则请求方可伪造该头绕过 IP 维度。
- **`/actuator/**` 与接口文档路径不在 `/api/**` 下，不受鉴权拦截器保护**：生产已关闭 springdoc，健康检查只暴露 health/info 且不展示细节。新增 actuator 端点时要一并确认暴露范围。
- 后端持久层为 **MyBatis-Plus 3.5.17**（`mybatis-plus-spring-boot4-starter`，适配 Spring Boot 4）。单表 CRUD 用 `BaseMapper`，复杂 SQL 写 `@Select` 注解或 XML（放 `resources/mapper/`）。
- `apps/backend-mock` 已停用（`VITE_NITRO_MOCK=false`），保留作为 `/api/system/*`、`/api/menu/all` 等接口的参考实现；真实后端未实现的接口不要在前端调用。
- 权限模式为 `mixed`：本地固定路由（dashboard 等）与后端下发菜单（`/api/menu/all`）自动合并；角色变更实时生效（每次请求查库）。
- 列表排序走后端**白名单**：前端 `sortConfig.remote` 提交 `sort`/`order`，后端仅在 `SORTABLE_FIELDS` 命中时生效（列名会拼进 ORDER BY，不能信任前端输入）。新增可排序列需**同时**改前端列的 `sortable: true` 与后端白名单，否则点了列头"没反应"。
- 启动日志出现「表结构自检未通过」说明存量库缺列，按提示到「存量库迁移」章节执行 `ALTER TABLE` 即可；不要用 `VERIFY_SCHEMA=false` 绕过。
- Redis 宕机会导致所有用户登出（令牌状态在 Redis），生产环境建议开启持久化（docker-compose 已配 AOF）并做好高可用。
- 本仓库派生自 vue-vben-admin（MIT），`packages/`、`internal/` 为其官方分层，升级时可对照上游。
