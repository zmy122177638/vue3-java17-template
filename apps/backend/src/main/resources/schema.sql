-- ============================================================
-- yorvix 数据库结构脚本（spring.sql.init 自动执行，幂等可重复）
--
-- 通用登录模板：仅保留账号表；
-- 表结构与 MyBatis-Plus 实体一一对应（本文件是表结构唯一来源）：
--   sys_user -> model/sys/SysUser.java
--
-- 令牌状态已迁移至 Redis（security/TokenService），不再使用数据库黑名单表。
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
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='系统用户';

-- 清理旧版 JWT 方案的 token 黑名单表（令牌状态已迁移至 Redis）
DROP TABLE IF EXISTS sys_token_blacklist;
