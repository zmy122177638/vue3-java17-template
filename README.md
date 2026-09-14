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
│   │       ├── api/         # API 封装（core/auth.ts、core/user.ts、request.ts）
│   │       ├── adapter/     # vben 表格/表单适配层
│   │       ├── layouts/     # BasicLayout / AuthLayout
│   │       ├── router/      # 路由 + 权限守卫（guard.ts / access.ts）
│   │       ├── store/       # 应用级 Pinia store（auth.ts）
│   │       └── views/       # 页面（_core 为核心页，其余按业务域建目录）
│   ├── backend/             # Java 后端（Spring Boot，端口 4838）
│   │   ├── docker-compose.yml   # MySQL 8.4
│   │   └── src/main/
│   │       ├── java/com/zmy/yorvix/
│   │       │   ├── common/      # Result / ResultCode / BizException / 全局异常 / 分页
│   │       │   ├── config/      # TokenProperties / WebMvcConfig / DataInitializer
│   │       │   ├── controller/  # AuthController / UserInfoController
│   │       │   ├── mapper/      # MyBatis-Plus BaseMapper（+ 注解/自定义 SQL）
│   │       │   ├── model/       # MyBatis-Plus 实体（@TableName/@TableId）
│   │       │   ├── request/ response/  # 请求/响应 DTO
│   │       │   ├── security/    # TokenService（Opaque Token + Redis）/ AuthInterceptor / AuthContext / @CurrentUser
│   │       │   └── service/     # 业务逻辑
│   │       └── resources/
│   │           ├── application.yaml
│   │           └── schema.sql   # 表结构唯一来源（幂等执行）
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

访问 `http://localhost:5666`，默认种子账号：**admin / 123456**（可通过 `yorvix.init.seed-enabled=false` 关闭种子数据）。

## 环境变量

### 前端 `apps/web-antd/.env*`

| 变量 | 说明 |
| --- | --- |
| `VITE_GLOB_API_URL` | API 基地址。开发为 `/api`（走 vite proxy）；生产为 `/api`（需 Nginx 反代） |
| `VITE_NITRO_MOCK` | dev 时是否拉起 mock 服务，已固定 `false`（使用真实后端） |
| `VITE_ROUTER_HISTORY` | 路由模式，生产默认 `history`（Nginx 需配 try_files） |
| `VITE_APP_STORE_SECURE_KEY` | 全局 store 持久化加密密钥，**新项目必须更换** |
| `VITE_APP_TITLE` / `VITE_APP_NAMESPACE` | 应用标题与命名空间 |

### 后端 `apps/backend/src/main/resources/application.yaml`

| 配置 | 说明 | 默认值 |
| --- | --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | 数据库连接（环境变量注入） | localhost / 3306 / yorvix |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接（令牌会话存储） | localhost / 6379 / 空 |
| `yorvix.token.expire-minutes` | accessToken 有效期（同时是 Redis TTL） | 120 |
| `yorvix.token.refresh-expire-minutes` | refreshToken 有效期 | 10080（7 天） |
| `yorvix.security.white-list` | 免认证接口白名单 | /api/auth/login、refresh、logout |
| `yorvix.init.seed-enabled` | 是否创建种子账号 admin/123456 | true |

## 认证机制

Opaque Token + Redis 设计（token 本体是 256 位随机数的 base64url 编码，不携带业务信息，状态全部在 Redis），前后端契约如下：

1. `POST /api/auth/login` → 返回 `Result{code:0, data:{accessToken, refreshToken, expiresIn, user}}`；同时 refreshToken 写入 HttpOnly Cookie（`yorvix_refresh_token`，path=/api/auth）。
2. Redis 存储映射：`auth:access:{token}` / `auth:refresh:{token}` → `{userId, username, nickname}`，TTL 即令牌有效期，过期自动清除。
3. 前端存 accessToken，请求自动带 `Authorization: Bearer <token>`；拦截器以「Redis 中是否存在对应 key」判定有效性（登出即删除，跨实例立即生效）。
4. accessToken 失效返回真实 HTTP 401 → 前端拦截器自动 `POST /api/auth/refresh`（Cookie 携带 refreshToken，后端返回**裸 token 字符串**，非 Result 包装），刷新时**轮换**旧 refreshToken（旧的立即删除）。
5. 登出 `POST /api/auth/logout` → 删除 access/refresh token 对应的 Redis key（无需黑名单表，无定时清理任务）。
6. 认证链路：白名单 → Redis 校验 → 用户存在/启用校验 → 写入 ThreadLocal `AuthContext`，业务代码可用 `@CurrentUser` 注入当前用户。

> 相比 JWT 的优势：令牌可随时吊销（删除 key 即刻全网生效）、无签名密钥泄露风险、支持服务端主动踢人；代价是每次请求多一次 Redis 查询（单次 GET，可接受）。

## 前后端契约（硬性约定）

- 统一返回格式：`{ code, message, data, timestamp }`，**成功码为 `0`**（`ResultCode.SUCCESS`）。
- 业务错误码：1001 用户名或密码错误、1002 账号禁用、1003 token 无效、1004 原密码错误、1005 用户不存在；HTTP 对齐码 400/401/403/404/405/500。
- `BizException` 抛出后由 `GlobalExceptionHandler` 统一转换为 Result；401/403 会同步设置真实 HTTP 状态码。
- 表结构唯一来源是 `schema.sql`（`spring.sql.init.mode: always` 幂等执行）。
- 前端分页请求/响应 DTO：`common/page/PageQuery` / `PageResult`（已就绪）。

## 新增业务模块指南

### 后端（以 `order` 域为例）

1. `model/order/Order.java` —— MyBatis-Plus 实体：`@TableName("t_order")`、主键 `@TableId(type = IdType.AUTO)`；字段驼峰自动映射下划线列；`created_at/updated_at` 交由数据库默认值与 `ON UPDATE` 维护（实体字段留空即可）
2. `mapper/order/OrderMapper.java` —— `extends BaseMapper<Order>`（单表 CRUD 免写 SQL），复杂查询用 `@Select` 注解或 XML；分页用 `selectPage(new Page<>(page, size), wrapper)`（分页插件已在 `MybatisPlusConfig` 配置）
3. `service/order/OrderService(Impl).java` —— 业务逻辑，抛 `BizException(ResultCode.XXX)`
4. `controller/order/OrderController.java` —— `@RequestMapping("/api/orders")`，返回 `Result.ok(data)`；当前用户用 `@CurrentUser LoginUser user` 注入
5. `schema.sql` 中追加建表语句（保持幂等）

### 前端

1. `src/api/order.ts` —— 用 `requestClient` 封装接口（自动拆 data、带 token、统一错误提示）
2. `src/views/order/index.vue` —— 页面（表格用 `#/adapter` 中的 vxe-table 适配器）
3. `src/router/routes/modules/order.ts` —— 新增路由模块（自动被 `routes/index.ts` 收集）
4. 如需权限码：后端 `/api/auth/codes` 返回数组，前端 `v-access:code` / `accessControl` 使用

## 构建与部署

```bash
# 前端构建（产物在 apps/web-antd/dist）
pnpm build:antd

# 后端构建
cd apps/backend && ./mvnw -DskipTests package
```

### Nginx 生产配置示例

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

- [ ] 注入 `REDIS_HOST` / `REDIS_PASSWORD` 等环境变量（Redis 存放会话令牌，务必设密码或内网隔离）
- [ ] 设置 `yorvix.init.seed-enabled=false` 或修改种子账号密码
- [ ] 更换 `VITE_APP_STORE_SECURE_KEY`
- [ ] 收紧后端 CORS（`WebMvcConfig`，使用 Nginx 反代后前端同源，可直接删除 CORS 配置）
- [ ] 前端 `.env.production` 修改 `VITE_APP_TITLE` / favicon 等品牌信息

## 常用命令

| 命令 | 说明 |
| --- | --- |
| `pnpm dev:antd` / `pnpm dev:backend` / `pnpm dev:backend:infra` | 启动前端 / 后端 / MySQL |
| `pnpm build:antd` | 构建前端 |
| `pnpm check` | 循环依赖 + 依赖检查 + 类型检查 + 拼写检查 |
| `pnpm lint` / `pnpm format` | 代码检查 / 格式化 |
| `pnpm commit` | 交互式规范提交（commitlint + czg） |

## 提交规范

遵循 Angular 约定（lefthook + commitlint 强制校验）：`feat`、`fix`、`style`、`perf`、`refactor`、`revert`、`test`、`docs`、`chore`、`ci`、`types`。

## 已知注意事项

- **Spring Boot 4.1.x** 较新（Jackson 3，包名 `tools.jackson`），如需更稳生态可降级到 3.x，注意同步调整依赖命名。
- 后端持久层为 **MyBatis-Plus 3.5.17**（`mybatis-plus-spring-boot4-starter`，适配 Spring Boot 4）。单表 CRUD 用 `BaseMapper`，复杂 SQL 写 `@Select` 注解或 XML（放 `resources/mapper/`）。
- `apps/backend-mock` 已停用（`VITE_NITRO_MOCK=false`），保留作为 `/api/system/*`、`/api/menu/all` 等接口的参考实现；真实后端未实现的接口不要在前端调用。
- 权限模式默认 `frontend`（本地路由 + 角色过滤）；如需后端下发菜单，需先实现 `/api/menu/all`。
- Redis 宕机会导致所有用户登出（令牌状态在 Redis），生产环境建议开启持久化（docker-compose 已配 AOF）并做好高可用。
- 本仓库派生自 vue-vben-admin（MIT），`packages/`、`internal/` 为其官方分层，升级时可对照上游。
