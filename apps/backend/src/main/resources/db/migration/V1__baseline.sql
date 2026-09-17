-- ============================================================
-- V1 基线：与 MyBatis-Plus 实体一一对应（本目录是表结构唯一来源）
--
--   sys_user       -> model/sys/SysUser.java
--   sys_role       -> model/system/SysRole.java
--   sys_user_role  -> model/system/SysUserRole.java
--   sys_menu       -> model/system/SysMenu.java
--   sys_role_menu  -> model/system/SysRoleMenu.java
--   sys_login_log  -> model/system/SysLoginLog.java
--   sys_oper_log   -> model/system/SysOperLog.java
--
-- 审计日志的排查字段：两张日志表都带 trace_id（= 应用日志里的 [traceId]），
-- sys_oper_log 另有 target_id（操作对象主键，即审计要求的「客体标识」，
-- 对象类型见 module 列）——分别由 AuditLogServiceImpl 与 OperationLogInterceptor 填充。
--
-- 迁移机制（Flyway）：
--   · 本文件是**基线**，发布后禁止修改（Flyway 会校验 checksum，改了会拒绝启动）；
--     后续任何结构变更请新增 V2__xxx.sql、V3__xxx.sql …
--   · 语句保持幂等（CREATE TABLE IF NOT EXISTS），因此对"已由历史版本 schema.sql
--     建好表的存量库"同样安全：baseline-on-migrate 会在版本 0 打基线后执行本文件，
--     已存在的表原样跳过，缺失的表（如审计日志表）被补齐。
--   · 应用启动时自动执行（spring.flyway.*，见 application.yaml），
--     无需再手工执行建表 SQL。
--
-- 表名前缀约定：平台治理数据用 sys_*（ADMIN 独占、不参与 RBAC 授权）；
-- 新增业务表建议用 biz_*，便于在日志、慢 SQL、误删排查时一眼区分两类数据。
--
-- 权限模型：RBAC（角色-菜单/按钮授权表驱动）；
-- ADMIN 为内置管理员角色，代码层全量放行，不依赖 sys_role_menu。
-- 超管（sys_user.is_super=1，仅种子/改库维护）代码层同样全量放行，且不依赖 sys_user_role。
-- 令牌状态在 Redis（security/TokenService），不使用数据库存储。
-- ============================================================

-- 系统用户
CREATE TABLE IF NOT EXISTS sys_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username      VARCHAR(64)  NOT NULL COMMENT '登录账号',
  password_hash VARCHAR(255) NOT NULL COMMENT 'PBKDF2 密码散列（security/PasswordEncoder）',
  nickname      VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
  email         VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  phone         VARCHAR(32)  DEFAULT NULL COMMENT '手机号',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用 0禁用',
  is_super      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否超管 1是 0否（仅种子/改库维护，不出现在用户管理）',
  token_version INT          NOT NULL DEFAULT 0 COMMENT '令牌版本：递增即让该用户已签发的全部令牌失效（改密/重置密码/禁用时 +1）',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='系统用户';

-- 角色
CREATE TABLE IF NOT EXISTS sys_role (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  code       VARCHAR(64)  NOT NULL COMMENT '角色编码（ADMIN 为内置管理员，代码全量放行）',
  name       VARCHAR(64)  NOT NULL COMMENT '角色名称',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用 0禁用',
  remark     VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色';

-- 用户角色关联
CREATE TABLE IF NOT EXISTS sys_user_role (
  id      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  -- uk_user_role 只能按 user_id 前缀命中；删除角色时要按 role_id 统计/清理绑定，否则全表扫
  KEY idx_user_role_role_id (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户角色关联';

-- 菜单（目录/菜单/按钮/内嵌/外链 五类，pid 树形自关联）
-- title 为多语言 JSON：{"zh-CN":"系统管理","en-US":"System"}
-- link_src：EMBEDDED 型为内嵌页地址（下发 meta.iframeSrc），LINK 型为外链地址（下发 meta.link）
CREATE TABLE IF NOT EXISTS sys_menu (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  pid            BIGINT       NOT NULL DEFAULT 0 COMMENT '父级ID，0为根',
  type           VARCHAR(16)  NOT NULL COMMENT '类型 CATALOG目录 MENU菜单 BUTTON按钮 EMBEDDED内嵌 LINK外链',
  name           VARCHAR(64)  DEFAULT NULL COMMENT '路由名（BUTTON 型仅作标识）',
  path           VARCHAR(128) DEFAULT NULL COMMENT '路由路径（相对父级）',
  component      VARCHAR(128) DEFAULT NULL COMMENT '组件路径（MENU 型，如 /system/user/index）',
  auth_code      VARCHAR(64)  DEFAULT NULL COMMENT '按钮权限码（BUTTON 型，业务域使用，命名形如 资源:动作，如 Report:Export）',
  title          VARCHAR(255) NOT NULL COMMENT '显示名（多语言 JSON）',
  icon           VARCHAR(64)  DEFAULT NULL COMMENT '图标（lucide 图标名）',
  active_icon    VARCHAR(64)  DEFAULT NULL COMMENT '激活时图标（lucide 图标名）',
  active_path    VARCHAR(128) DEFAULT NULL COMMENT '作为菜单高亮依据的目标路由路径',
  link_src       VARCHAR(255) DEFAULT NULL COMMENT '外链/内嵌地址（http(s)）',
  badge_type     VARCHAR(16)  DEFAULT NULL COMMENT '徽标类型 dot红点 normal文字',
  badge          VARCHAR(64)  DEFAULT NULL COMMENT '徽标内容（badge_type=normal 时生效）',
  badge_variants VARCHAR(32)  DEFAULT NULL COMMENT '徽标颜色 default/destructive/primary/success/warning',
  hide_in_menu   TINYINT      NOT NULL DEFAULT 0 COMMENT '菜单中隐藏 1是 0否',
  hide_children_in_menu TINYINT NOT NULL DEFAULT 0 COMMENT '菜单中隐藏子级 1是 0否',
  hide_in_breadcrumb TINYINT  NOT NULL DEFAULT 0 COMMENT '面包屑中隐藏 1是 0否',
  hide_in_tab    TINYINT      NOT NULL DEFAULT 0 COMMENT '标签栏中隐藏 1是 0否',
  sort           INT          NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用 0禁用',
  keep_alive     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否缓存页面 1是 0否',
  affix_tab      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否固定标签页 1是 0否',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_menu_pid (pid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜单';

-- 角色菜单授权（ADMIN 内置管理员不查此表）
CREATE TABLE IF NOT EXISTS sys_role_menu (
  id      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  menu_id BIGINT NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_menu (role_id, menu_id),
  -- uk_role_menu 只能按 role_id 前缀命中；删除菜单时要按 menu_id 清理授权，否则全表扫
  KEY idx_role_menu_menu_id (menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色菜单授权';

-- 登录日志（审计）：成功/失败/被限流/登出逐条留痕。
-- 为什么单独建表而不是只打日志：安全事件的事后追溯需要**可查询**（按账号/IP/时间），
-- 日志文件里翻是做不到的；限制失败次数（security/LoginAttemptGuard）也依赖这里能对账。
CREATE TABLE IF NOT EXISTS sys_login_log (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username   VARCHAR(64)  DEFAULT NULL COMMENT '尝试登录的账号（失败时也记录，用于识别撞库）',
  user_id    BIGINT       DEFAULT NULL COMMENT '账号存在时的用户ID，否则 NULL',
  event      VARCHAR(16)  NOT NULL COMMENT 'SUCCESS成功 FAILURE凭据错误 DISABLED账号禁用 LOCKED被限流 LOGOUT登出',
  ip         VARCHAR(64)  DEFAULT NULL COMMENT '来源 IP（见 security/ClientInfoResolver）',
  user_agent VARCHAR(255) DEFAULT NULL COMMENT 'User-Agent（截断后）',
  detail     VARCHAR(255) DEFAULT NULL COMMENT '补充信息（i18n key 或原因），不含任何凭据',
  trace_id   VARCHAR(64)  DEFAULT NULL COMMENT '请求链路标识（与 X-Request-Id、应用日志的 traceId 一致）',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_login_log_username (username),
  KEY idx_login_log_created_at (created_at),
  -- 按 traceId 反查"这一次请求都做了什么"是排查与取证的常用入口
  KEY idx_login_log_trace_id (trace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='登录日志';

-- 操作日志（审计）：系统管理域（/api/system/**）的写操作逐条留痕。
-- 记录口径见 security/OperationLogInterceptor：只记非 GET 请求，避免查询把表刷爆。
CREATE TABLE IF NOT EXISTS sys_oper_log (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id      BIGINT       DEFAULT NULL COMMENT '操作人ID',
  username     VARCHAR(64)  DEFAULT NULL COMMENT '操作人账号（冗余，避免联表）',
  module       VARCHAR(64)  DEFAULT NULL COMMENT '模块，同时是操作对象类型（由路径推断，如 users/roles/menus）',
  target_id    BIGINT       DEFAULT NULL COMMENT '操作对象主键（客体标识）；新建、无 id 的动作为 NULL',
  method       VARCHAR(16)  NOT NULL COMMENT 'HTTP 方法（POST/PUT/DELETE）',
  uri          VARCHAR(255) NOT NULL COMMENT '请求路径',
  ip           VARCHAR(64)  DEFAULT NULL COMMENT '来源 IP',
  status       INT          NOT NULL COMMENT 'HTTP 状态码',
  success      TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功 1是 0否（业务失败也算失败）',
  error_message VARCHAR(255) DEFAULT NULL COMMENT '失败原因（i18n key），成功为 NULL',
  duration_ms  BIGINT       NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
  trace_id     VARCHAR(64)  DEFAULT NULL COMMENT '请求链路标识（与 X-Request-Id、应用日志的 traceId 一致）',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_oper_log_created_at (created_at),
  KEY idx_oper_log_user_id (user_id),
  -- 按 traceId 反查"这一次请求都做了什么"是排查与取证的常用入口
  KEY idx_oper_log_trace_id (trace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='操作日志';
