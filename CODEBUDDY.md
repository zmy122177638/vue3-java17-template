# CodeBuddy AI 协作规则（vue3-java17-template）

> 本文件是 AI 助手在本仓库工作的强制约定。每次生成或修改代码前必须遵守。详细的项目说明、启动方式、部署清单见 `README.md`。

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
5. 认证方案是 **Opaque Token + Redis**（`security/TokenService`）：token 为 256 位随机数，状态存 Redis（`auth:access:{token}` / `auth:refresh:{token}`，TTL 即有效期）。**禁止改回 JWT 或在 token 中携带业务信息**；登出/刷新 = 删除 Redis key。载荷含 `tokenVersion`，与 `sys_user.token_version` 比对，不一致即失效——**改密 / 管理员重置密码 / 禁用账号时必须调 `SysUserMapper.bumpTokenVersion(id)` 踢下线**（仅靠拦截器的 `status` 校验不够：重新启用后旧令牌会复活）。该列用 `@TableField(updateStrategy = NEVER)` 锁住，只能通过 `bumpTokenVersion` 原子递增，禁止用 `updateById` 写回。
   - **登出必须携带 Authorization 头**（前端 `logoutApi(accessToken)` 负责，`store/auth.ts` 传入）：登出接口在白名单里（令牌过期也要能登出），但"删除服务端令牌"依赖请求头里的令牌，漏带会退化成"界面登出了、旧 accessToken 仍有效到 TTL 结束"。
   - **登录失败限流**由 `security/LoginAttemptGuard` 承担（账号 + IP 双维度），判定必须在查库/校验口令之前；**账号不存在与密码错误必须返回同一结果**，且"账号被禁用"的提示只能放在口令校验之后（否则接口变成账号枚举器）。
6. 认证流程：白名单在 `application.yaml` 的 `yorvix.security.white-list`。**匹配语义是精确匹配**（`security/WhiteList`），需要放开整棵子树必须显式写 `/**`（如 `/api/public/**`）——**不要改回 `startsWith` 前缀匹配**，那会让 `/api/auth/login-audit` 这类"恰好同前缀"的新接口自动变成公开接口，开发期毫无迹象、出事时也想不到是白名单。新增公开接口要加白名单，否则拦截器 401；`login/refresh/logout` 三项是认证链路必需，缺失会让系统不可用，`StartupChecks` 会在启动期拒绝启动。
7. 当前用户注入：Controller 方法参数 `@CurrentUser LoginUser user`，禁止在业务代码里手动解析请求头。
8. 业务错误码统一在 `ResultCode` 枚举登记（1001-1009 已占用），不要散落魔法数字。
9. 权限模型是 **RBAC + mixed 菜单下发**（详见 README「权限系统」章节）：`sys_role/sys_user_role/sys_menu/sys_role_menu` 四表；路由模式 `mixed`（本地固定路由 + `/api/menu/all` 下发合并）。权限分**三个域**，接口路径与鉴权方式一一对应：**系统管理域**（`/api/system/**`，`@RequiresRoles("ADMIN")` 一刀切，**不参与 RBAC 授权**）、**业务域**（`@RequiresPermissions(...)`，按 `sys_role_menu` 授权菜单与按钮）、**本人域**（`/api/user/**`，只需登录且操作对象恒为当前用户）。**不要跨域混用鉴权方式。**
10. **接口鉴权是安全边界**：管理员接口必须标注 `@RequiresRoles("ADMIN")`（类或方法级，AND 语义，403 由 `AuthInterceptor` 返回）；`ADMIN` 为内置角色，代码全量放行，禁止删除/改码逻辑。
11. 按钮权限码（大写冒号风格，命名形如 `资源:动作`，如 `Report:Export`）存于 BUTTON 型菜单的 `auth_code`，经 `/api/auth/codes` 下发。它**有两个用途，缺一不可**：
    - **接口级安全边界**：业务接口必须标 `@RequiresPermissions(AuthCodes.XXX)`，权限码常量集中登记在 `common/permission/AuthCodes`（与种子数据共用同一常量，避免注解与种子写得不一致导致"授权了却 403"）。拦截器**懒加载**——只有命中该注解的接口才查一次权限码，未标注的接口零额外开销。
    - **前端 UX**：`VbenTableAction.auth` / `CellOperation.options[].auth` / `v-access:code` 控制显隐。它只是同一份权限码的 UI 呈现，**隐藏按钮不等于拒绝请求**，禁止只做前端控制。系统管理域**不使用**权限码：它是 ADMIN 独占（`@RequiresRoles("ADMIN")` 一刀切 + ADMIN 代码层全量放行），在那里声明 `System:User:*` 永远不会参与判定，因此 `DataInitializer` 不播种这类按钮、系统管理页面也不挂 `auth`。
12. 菜单 `title` 是多语言 JSON（`{"zh-CN":"系统管理","en-US":"System"}`），下发时按 `Accept-Language` 解析；新增菜单字段变更需同步 `MenuUpsertRequest` 与前端表单。
13. **ADMIN 独占菜单**由 `yorvix.menu.admin-only-paths`（默认 `/system`，前缀匹配，含全部后代）声明。后端下发菜单树时给 `MenuNode.adminOnly` 打标记，角色授权树据此隐藏这些节点；写授权时再兜底剔除，防止手工构造的请求写入无效授权。**新增 ADMIN 独占的顶级菜单必须同步该配置**，否则会出现"菜单能勾选授权、被分配者一访问接口就 403"的难排查问题。
14. 角色授权写入（`PUT /api/system/roles/{id}/menus`）是**全量替换**，后端会依次：去重 → 校验 menuId 存在 → **逐级补全祖先** → 剔除 ADMIN 独占菜单。补全祖先是必需的：菜单 `path` 多为相对父级（如 `user`），缺父目录时下发会把子菜单当顶级路由，而 `path` 语义仍是相对的，前端会注册到错误位置。
15. **错误消息一律用 i18n key，禁止写死中文**：`ResultCode` 与 `BizException` 携带的是 key（如 `error.username.exists`），由 `GlobalExceptionHandler` 按 `Accept-Language` 解析（机制见 `common/i18n/Messages`）。带参数用占位符：`throw new BizException(ResultCode.BAD_REQUEST, "error.role.disabled", roleName)`，properties 里写 `角色[{0}]已禁用`。新增 key 必须**同时**补 `i18n/messages.properties` 与 `messages_en_US.properties`（`MessagesPropertiesTest` 会校验两边 key 集合一致）。
16. **DTO 校验消息不要写 `message = "中文"`**：标准约束（`@NotBlank/@NotEmpty/@NotNull/@Size/@Email/@Min/@Max/@DecimalMin/@Digits`）的文案统一在 `i18n/messages*.properties` 中覆盖 `jakarta.validation.constraints.*.message`，注解上只留约束本身。只有枚举型约束等确实需要自定义文案时才写 `message = "{your.key}"`。
17. **时间字段统一 `yyyy-MM-dd HH:mm:ss`**，由 `config/JacksonConfig` 为 `LocalDateTime` 注册。**不要再依赖 `spring.jackson.date-format`**——它只作用于 `java.util.Date`，对 `LocalDateTime` 不生效（坑很隐蔽：配置看着对，接口却返回带 `T` 的 ISO 字符串，前端表格直接展示原始值）。
18. **三个权限域的边界不要混用**：`/api/system/**`（ADMIN 独占，`@RequiresRoles`）、业务路径（`@RequiresPermissions` 按权限码）、`/api/user/**`（本人域：操作对象**只能从 `AuthContext` 取当前登录用户**，绝不接受目标用户 ID 作为参数）。新增接口先判断属于哪个域，再选鉴权方式。
19. **"可清空字段"必须标 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`**：MyBatis-Plus 默认策略是 `NOT_NULL`，会把值为 null 的字段从 UPDATE 语句里**剔除**——于是"用户点了输入框的 × 清空后保存"变成"不修改"，接口返回成功、库里还是旧值，**不报任何错**。已按此规则标注：`SysUser.nickname/email/phone`、`SysMenu` 的 `path/component/authCode/icon/activeIcon/activePath/linkSrc/badgeType/badge/badgeVariants`、`SysRole.remark`。判断标准：**前端能把值置空传上来的字段**就要加（区分"没填"与"要清空"）；反之，必填字段（有 `@NotBlank`/`@NotNull`）不需要，只允许原子递增或由数据库维护的字段用 `updateStrategy = NEVER`（如 `SysUser.tokenVersion`、`createdAt`）。 **新增实体字段时按这条自检**，否则就是又一个"改了没生效"的静默 bug。

## 4. 后端规范（apps/backend）

- 持久层是 **MyBatis-Plus 3.5.17**（`mybatis-plus-spring-boot4-starter` + `mybatis-plus-jsqlparser`）：Mapper 写在 `mapper/` 包（`extends BaseMapper<T>`），实体写在 `model/` 包（`@TableName` + `@TableId(type = IdType.AUTO)`，字段驼峰自动映射下划线列）。
- SQL 纪律：单表 CRUD 直接用 BaseMapper 方法；条件查询用 `LambdaQueryWrapper`；复杂 SQL 用 `@Select` 注解或 XML（放 `resources/mapper/`，mapper XML namespace 对应 Mapper 接口全限定名）。
- 分页：`PageQuery.toMpPage()` 转换为 MP `Page`，`selectPage(...)` 查询后用 `PageResult.of(page, mapper)` 转换；分页插件在 `config/MybatisPlusConfig`（改动前确认 `mybatis-plus-jsqlparser` 依赖在位）。
- 实体纪律：`created_at/updated_at` 交由数据库默认值与 `ON UPDATE` 维护，实体字段留空；不要在实体上加 MyBatis-Plus 以外的持久化注解（项目已无 JPA）。**新增"前端可清空"的字段时必须标 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`**，否则清空操作会被 MyBatis-Plus 默认的 `NOT_NULL` 策略静默丢弃（见 §3.19）。
- 分层结构：`controller → service(接口 + Impl) → mapper`；请求 DTO 放 `request/`，响应 DTO 放 `response/`。
- 表结构**唯一来源**是 `src/main/resources/db/migration/` 下的 **Flyway 迁移文件**，应用启动时自动执行；不要开启 MyBatis-Plus/DDL 自动建表，也不要用 `spring.sql.init`。
  - `V1__baseline.sql` 是基线，**禁止修改**（Flyway 校验 checksum，改动会直接拒绝启动）；建表语句保持 `CREATE TABLE IF NOT EXISTS` 幂等。
  - 任何结构变更（加表/加列/加索引）都**新增一个迁移文件**（`V2__xxx.sql`…），不要把 ALTER 追加进 V1。
  - 引入 Flyway 用 `spring-boot-starter-flyway` 而不是裸 `flyway-core`：Spring Boot 4 把各技术自动配置拆成了独立模块，只放 jar 进 classpath **不会执行迁移且不报错**。
- 依赖纪律：认证采用自研 Opaque Token + Redis（`security/TokenService`，零 JWT/安全框架依赖）+ PBKDF2 `PasswordEncoder`。引入 Spring Security、jjwt、Sa-Token 等前必须先询问用户。
- 配置一律环境变量注入（如 `DB_HOST`、`REDIS_HOST`、`REDIS_PASSWORD`），不要把密码/密钥硬编码进代码或 yaml 的新增行。
- 异常：业务错误抛 `BizException`，由 `GlobalExceptionHandler` 统一处理；不要在 Controller 里 try-catch 后自行拼 Result。
- **认证链路不要重复读同一行**：`AuthInterceptor` 已在入口加载 `SysUser`（校验状态 + 令牌版本），下游需要超管标识或角色时用 `RoleService.getRoleCodes(userId, superUser)` 重载传入已加载的值，不要让 `getRoleCodes(userId)` 内部再查一次 `sys_user`——那是每个受保护请求都要付的固定开销。新增"需要按用户判定的服务方法"时沿用这个思路：**手上已经有实体就传下去，不要传 id 让它重查**。
- **配置分层**：`application.yaml` 是面向本地开发的默认值（种子开启、日志 debug、CORS `*`、`cookie-secure=false`；表结构统一由 Flyway 启动时迁移，dev/prod 行为一致）。**新增的配置项若涉及安全或生产环境，必须同时落到 `application-prod.yaml`**（种子关闭、日志 info、`cookie-secure=true`、CORS 留空、`springdoc.*.enabled=false`）。新增"危险默认值"时，同步在 `StartupChecks#warnUnsafeDefaults` 补一条告警。
- **表结构变更**：改实体字段的同时必须新增迁移文件（`V2__xxx.sql`）；`StartupChecks` 启动时会校验实体与库表一致，缺列直接拒绝启动，**不要用 `VERIFY_SCHEMA=false` 绕过**。
- **审计日志**：登录相关事件写 `sys_login_log`（`AuditLogService`），`/api/system/**` 的写操作写 `sys_oper_log`（`OperationLogInterceptor`，成功与失败都记）。新增系统管理域接口无需额外适配，路径前缀命中即可；**审计写入必须保持"不阻断业务"（失败只打 ERROR 日志）且使用独立事务**，否则业务回滚会把痕迹一起抹掉。业务域要审计就把前缀加入 `OperationLogInterceptor.AUDITED_PREFIXES`。两张表都自动带 `trace_id`（`AuditLogServiceImpl` 从 MDC 取，与 `X-Request-Id`/应用日志 `[traceId]` 同值，**不要改成当参数传**：漏传只会静默少字段）；`sys_oper_log` 另有 `target_id`（客体标识，由 `OperationLogInterceptor.resolveTarget` 从路径解析，**对象类型复用 `module` 列，不要再加 `target_type`**）——新增写接口时保持 `/api/system/{资源}/{id}/...` 路径形态，否则解析不出对象；新增日志字段要同步 `LoginLogItem`/`OperLogItem` 与前端 `views/system/log/*/data.ts`。
- **登录限流**：`security/LoginAttemptGuard` 按账号 + IP 双维度计数（`auth:login:fail:*`），必须在**查库与校验口令之前**执行。`yorvix.security.client-ip-header` 只有在应用确实位于可信反向代理之后才可配置（该头是调用方可控输入）；留空时用 `remoteAddr`。
- **`/actuator/**`、`/swagger-ui.html`、`/v3/api-docs` 不在 `/api/**` 下，不受 `AuthInterceptor` 保护**：生产 profile 已关闭 springdoc，actuator 只暴露 health/info 且 `show-details=never`。新增这类端点时必须显式确认暴露范围。
- **接口文档**：新增接口无需额外注解（springdoc 自动扫描），但涉及认证语义或约定的接口建议补 `@Operation`/`@Tag` 说明；`config/OpenApiConfig` 已注册全局 Bearer 方案。
- **列表排序**：后端用 `PageQuery.toMpPage(SORTABLE_FIELDS, ...)`，白名单是安全边界（列名会拼进 ORDER BY 语句）。新增可排序列必须同时改后端白名单与前端列的 `sortable: true`，否则列头点了没反应。
- **单元测试**：放 `src/test/java`，纯逻辑测试不要起 Spring 上下文或连接数据库/Redis；`./mvnw test` 必须保持全绿。凡是"错了也不报错、只是行为悄悄不对"的逻辑（分页钳制、排序白名单、授权规范化、路径匹配、i18n key 缺失），都要补测试。
- **分域约定**：`service/` 按权限域分包——`system`（ADMIN 独占治理）、`user`（本人域）、`auth`（认证）；新增业务模块时再加一层 `biz` 子包，并用 `biz_` 表名前缀与治理数据（`sys_`）区分。`model/mapper/controller/request/response` 同样按域分。**新增功能先定域，再决定鉴权方式**，不要把业务代码塞进 `system` 包。
- **请求链路标识**：`config/TraceIdFilter` 把 `X-Request-Id`（调用方透传或后端生成）写入 MDC 并回写响应头；日志格式含 `[traceId]`（见 `application.yaml` 的 `logging.pattern`）。排查问题先按 traceId 捞同一次请求的日志，不要靠时间戳猜。
- **配置同步**：新增环境变量时同步更新 `apps/backend/.env.example`（清单）与 `StartupChecks#warnUnsafeDefaults`（危险默认值告警）。

## 5. 前端规范（apps/web-antd）

- 路径别名：用 `#/*`（对应 `apps/web-antd/src/*`），这是 package.json `imports` 配置，不要用 `@/`。
- 新增**业务页面**（mixed 模式）两步：`src/views/<domain>/` 放页面文件 + 菜单管理页插入菜单数据（component 填视图路径如 `/your-domain/list/index`）。本地固定路由才放 `src/router/routes/modules/`。
- 新增**管理接口**：Controller 标注 `@RequiresRoles("ADMIN")`；前端 API 层分页响应映射为 `{ items, total }` 结构（vxe `proxyConfig` 契约，见 `api/system/*.ts`）。
- 全局状态（access/user/tab）来自 `@vben/stores`；应用级业务 store 写在 `src/store/`（Pinia setup 风格）。
- API 写法：`requestClient.get/post/put/delete`；文件上传下载等需要原始响应的场景用 `baseRequestClient`。**注意 `post(url, data, config)` 的第二个参数是请求体**，`withCredentials`、`headers` 等 axios 配置必须放第三个参数——放错位置不会报错，配置静默失效（跨域部署时表现为 refresh/logout 拿不到 Cookie）。
- 文案：用户可见文案优先走 `$t()` 国际化（`src/locales/langs/` 下 zh-CN 与 en-US 成对添加）。
- 图标使用 `@vben/icons` 导出的 lucide 图标组件。
- 通知中心接入点在 `src/layouts/basic.vue` 的 `notifications`（当前为空数组），接真实消息接口后在此填充，勿写入硬编码演示数据。
- 语言包位于 `src/locales/langs/{zh-CN,en-US}/`，由 glob 动态加载，新增 json 文件即生效，两种语言需成对添加。
- **业务页面参照 `views/system/user/`**：它是"分页表格 + 搜索 + 抽屉表单 + 语言包"的完整范本（`index.vue` / `data.ts` / `modules/*.vue`），新增业务页优先复用同一套组织方式，不要另起一套写法。
- **按钮权限码**：普通按钮挂 `v-access:code`，操作列用 `VbenTableAction.auth`，字符串必须与后端 `AuthCodes` 中的常量一致。权限码在登录时一次性拉取（`/api/auth/codes`），**变更授权后需重新登录才会刷新**——这是已知取舍，不要改为每次请求拉取。
- 后端错误提示已按 `Accept-Language` 本地化（见 `CODEBUDDY.md` §3.15），前端**不要**维护"错误码 → 文案"映射表，直接展示响应体的 `message` 即可。

## 6. UI 生成规则（强制）

每次生成或修改任何 UI 组件/页面/样式，必须同时满足：

1. **自适应**：布局覆盖 375px 移动端到 1440px+ 桌面端。用 Tailwind 响应式前缀（`sm:/md:/lg:/xl:`）、`grid-cols-1 md:grid-cols-2 xl:grid-cols-3` 降列、`flex-wrap`、`w-full + max-w-*`，窄屏不溢出。
2. **dark 模式**：颜色优先用语义色类（`text-foreground`、`text-muted-foreground`、`bg-card`、`bg-background`、`bg-muted`、`border-border`），避免只写亮色 hex。必须硬编码时配 `dark:` 变体，保证深底对比度 ≥ 4.5:1。
3. `bg-[linear-gradient(...)]` 是 background-image，dark 下需单独 `dark:bg-[linear-gradient(...)]` 覆盖；半透明背景在 dark 下适当提高不透明度。
4. 中后台页面遵循 vben 布局体系（`BasicLayout` + 菜单路由），不要自建全局壳子。

## 7. 代码风格与提交

- 格式化由仓库的 `@vben/eslint-config` / oxlint / oxfmt 托管，不要手写 `.prettierrc` 或引入新格式化工具。
- 提交信息遵循 Angular 约定：`feat(xxx): ...`、`fix(xxx): ...`（`pnpm commit` 交互式生成）。scope 用业务域（auth、system、frontend、backend 等）。
- 完成代码后主动运行：`pnpm lint`（前端改动）、`pnpm check:type`（涉及 TS 类型时）；后端改动运行 `./mvnw test`（同时完成编译校验）。`.github/workflows/ci.yml` 会跑同样的检查，本地需保持全绿再提交。

## 8. 常用命令速查

```bash
pnpm dev:antd            # 前端 dev（5666）
pnpm dev:backend         # 后端 dev（4838）
pnpm dev:backend:infra   # MySQL（docker compose）
pnpm build:antd          # 前端构建
pnpm lint / pnpm format  # 检查 / 格式化
pnpm check               # 循环依赖+依赖+类型+拼写
pnpm build:docker        # 前端镜像（yorvix-web）
docker build -t yorvix-api apps/backend   # 后端镜像
pnpm test:unit                            # 前端单测（vitest）
cd apps/backend && ./mvnw test            # 后端单测（不依赖 DB/Redis）
SPRING_PROFILES_ACTIVE=prod ...           # 生产 profile：关种子/关接口文档/收紧日志
```

## 9. 典型任务的标准做法

- **新增业务模块**：按 README「新增业务模块指南」前后端成对实现，先后端（接口契约）后前端。
- **新增系统管理页（CRUD）**：参考 `views/system/{user,role,menu}` 现成实现——`useVbenVxeGrid` + `CellTag/CellSwitch/CellOperation` 渲染器（适配器已内置权限码过滤）+ `useVbenDrawer` 抽屉表单 + `useVbenForm` schema；操作按钮权限用 `VbenTableAction.auth` 或 options 项 `auth` 字段。
- **角色授权/权限排查**：角色授权走角色管理页授权树（`sys_role_menu`）；403 排查顺序：`@RequiresRoles` 是否标注 → 用户角色绑定（`sys_user_role`）→ 角色启用状态。
- **排查 401**：先确认接口是否该加白名单 → 再用 `redis-cli` 查 `auth:access:{token}` 是否存在（登出/过期即删除）→ 最后查用户状态。
- **升级框架**：`packages/`、`internal/` 改动需对照 vue-vben-admin 上游（v5.x），并在提交信息中注明。
