package com.zmy.yorvix.config;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zmy.yorvix.security.WhiteList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * 启动自检：把"配置或表结构不对"尽早暴露，而不是变成运行期的一个 500。
 * <ol>
 *   <li><b>表结构校验（不通过则拒绝启动）</b>：把 MyBatis-Plus 已注册的实体与实际库表比对，
 *       缺表或缺列时打印确切的缺失项并中断启动。缺列曾是最容易踩的坑——早期用
 *       {@code spring.sql.init} + 单个 {@code schema.sql}，只能建表不能补列，
 *       症状是"登录正常、但登录后所有接口 500"，极难定位；现在结构由 Flyway 迁移管理
 *       （{@code db/migration}），本检查退化为"迁移文件与实体是否一致"的兜底防线。</li>
 *   <li><b>不安全默认值检查</b>：{@code application.yaml} 面向开箱即用的本地开发，默认只告警；
 *       prod profile 开启严格模式后，种子账号 / CORS 通配 / Cookie 未启用 Secure
 *       这三类问题会直接拒绝启动。</li>
 * </ol>
 * 结构校验可用 {@code yorvix.init.verify-schema=false} 关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StartupChecks implements CommandLineRunner {

  /** 只校验本项目实体所在包，避免把框架/第三方表一并算进来 */
  private static final String MODEL_PACKAGE_PREFIX = "com.zmy.yorvix.model";

  /**
   * 认证链路必需的白名单项，缺任何一项都视为配置错误并拒绝启动。
   * <p>三个缺项的后果都不是"少个功能"，而是系统不可用或行为异常：
   * <ul>
   *   <li>缺 {@code login} —— 谁也登不进来；</li>
   *   <li>缺 {@code refresh} —— 前端 access token 过期后无法续期，陷入 401 循环；</li>
   *   <li>缺 {@code logout} —— 登出接口 401，Redis 中的令牌无法主动清除。</li>
   * </ul>
   * 而白名单是"免鉴权清单"，少写一项不会报任何错，只表现为"这个接口永远 401"。
   */
  private static final List<String> REQUIRED_WHITE_LIST = List.of(
      "/api/auth/login", "/api/auth/refresh", "/api/auth/logout");

  private final DataSource dataSource;
  private final TokenProperties tokenProperties;

  @Override
  public void run(String... args) {
    verifySecurityConfig();
    if (tokenProperties.getInit().isVerifySchema()) {
      verifySchema();
    }
    verifyUnsafeDefaults();
  }

  // ---------------- 安全配置校验 ----------------

  /**
   * 校验白名单包含认证链路必需项。
   * <p>放在最前面：它不碰数据库，是开销最低、也最该先排除的错误。
   * <p>白名单**为空**同样会命中这里——那意味着连 {@code /api/auth/login} 都要鉴权，
   * 系统直接锁死，属于必须拦在启动期的配置错误。
   */
  private void verifySecurityConfig() {
    List<String> whiteList = tokenProperties.getSecurity().getWhiteList();
    List<String> missing = REQUIRED_WHITE_LIST.stream()
        .filter(required -> !WhiteList.anyCovers(whiteList, required))
        .toList();
    if (!missing.isEmpty()) {
      throw new IllegalStateException(renderWhiteListError(missing));
    }
  }

  private String renderWhiteListError(List<String> missing) {
    StringBuilder message = new StringBuilder();
    message.append("\n");
    message.append("================ 安全配置自检未通过，应用拒绝启动 ================\n");
    message.append("  - yorvix.security.white-list 缺少必需项: ").append(missing).append("\n");
    message.append("\n");
    message.append("白名单是【免鉴权】路径清单，少写一项不会报任何错，\n");
    message.append("只会表现为“该接口永远返回 401”，因此把它提前到启动期：\n");
    message.append("  · 缺 /api/auth/login   -> 谁也登不进来，系统整体不可用\n");
    message.append("  · 缺 /api/auth/refresh -> 前端令牌过期后无法续期，陷入 401 循环\n");
    message.append("  · 缺 /api/auth/logout  -> 登出接口 401，Redis 中的令牌无法主动清除\n");
    message.append("\n");
    message.append("请在 application.yaml 的 yorvix.security.white-list 中补齐后重启。\n");
    message.append("匹配语义：精确匹配；需要放开整棵子树请显式写 /**（如 /api/public/**）。\n");
    message.append("=============================================================");
    return message.toString();
  }

  // ---------------- 表结构校验 ----------------

  private void verifySchema() {
    List<TableInfo> tables = TableInfoHelper.getTableInfos().stream()
        .filter(this::isProjectEntity)
        .toList();
    if (tables.isEmpty()) {
      // 静默通过等于没有检查，这里明确说明，避免误以为已经校验过
      log.warn("未发现任何本项目实体（{}.*），已跳过表结构自检", MODEL_PACKAGE_PREFIX);
      return;
    }

    List<String> problems = new ArrayList<>();
    for (TableInfo table : tables) {
      Set<String> actual = actualColumns(table.getTableName());
      if (actual.isEmpty()) {
        problems.add("表 " + table.getTableName() + " 不存在");
        continue;
      }
      Set<String> missing = new TreeSet<>(expectedColumns(table));
      missing.removeAll(actual);
      if (!missing.isEmpty()) {
        problems.add("表 " + table.getTableName() + " 缺少列: " + missing);
      }
    }

    if (!problems.isEmpty()) {
      throw new IllegalStateException(renderSchemaError(problems));
    }
    log.info("表结构自检通过：{} 张表与实体定义一致", tables.size());
  }

  private boolean isProjectEntity(TableInfo table) {
    Class<?> entityType = table.getEntityType();
    return entityType != null && entityType.getName().startsWith(MODEL_PACKAGE_PREFIX);
  }

  /** 实体声明的列（主键 + 普通字段），即 MyBatis 生成 SQL 时会用到的列 */
  private Set<String> expectedColumns(TableInfo table) {
    Set<String> columns = new LinkedHashSet<>();
    if (table.getKeyColumn() != null) {
      columns.add(table.getKeyColumn().toLowerCase(Locale.ROOT));
    }
    for (TableFieldInfo field : table.getFieldList()) {
      columns.add(field.getColumn().toLowerCase(Locale.ROOT));
    }
    return columns;
  }

  private Set<String> actualColumns(String tableName) {
    Set<String> columns = new LinkedHashSet<>();
    String sql = "SELECT COLUMN_NAME FROM information_schema.COLUMNS"
        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, tableName);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          columns.add(resultSet.getString(1).toLowerCase(Locale.ROOT));
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException("表结构自检失败：无法读取 information_schema", e);
    }
    return columns;
  }

  private String renderSchemaError(List<String> problems) {
    StringBuilder message = new StringBuilder();
    message.append("\n");
    message.append("================ 表结构自检未通过，应用拒绝启动 ================\n");
    for (String problem : problems) {
      message.append("  - ").append(problem).append("\n");
    }
    message.append("\n");
    message.append("本项目的表结构唯一来源是 src/main/resources/db/migration（Flyway 迁移）。\n");
    message.append("出现本错误说明「已执行的迁移」与「实体定义」不一致，常见原因：\n");
    message.append("  · 改了实体但没写新的迁移文件（V2__xxx.sql）——补一个即可；\n");
    message.append("  · 手工改过数据库，或把 flyway_schema_history 一起删掉了。\n");
    message.append("注意：不要修改已发布的历史迁移文件（V1 等），Flyway 会校验 checksum 并拒绝启动，\n");
    message.append("结构变更一律新增迁移文件。确需跳过本检查可设置 VERIFY_SCHEMA=false。\n");
    message.append("=============================================================");
    return message.toString();
  }

  // ---------------- 不安全默认值检查 ----------------

  /**
   * 不安全默认值检查：默认只告警；严格模式（prod profile 默认开启）下拒绝启动。
   *
   * <p><b>为什么严格模式必须阻断</b>：下面三项在生产不是"提醒"而是事故源——
   * 一次漏配就等于「admin/弱口令超管 + 任何站点可携带凭证跨域 + refresh Cookie 可经明文发出」，
   * 而 {@code log.warn} 在容器编排里极易被淹没。{@code application.yaml} 仍保持
   * 面向本地开发的宽松默认值（改动它会破坏开箱即用的开发体验），
   * 「变严」只由 prod profile 的 {@code strict-mode=true} 触发。
   *
   * <p><b>为什么"种子密码仍是默认值"不纳入阻断项</b>：种子关闭后该密码不会被使用，
   * 纳入会导致 prod 永远启动不了（{@code application-prod.yaml} 并不同时覆盖 seed-password）。
   * 它仍保留在 {@link #warnUnsafeDefaults()} 的告警里。
   */
  private void verifyUnsafeDefaults() {
    List<String> violations = new ArrayList<>();
    if (tokenProperties.getInit().isSeedEnabled()) {
      violations.add("种子数据初始化已开启（yorvix.init.seed-enabled=true）："
          + "连上全新库会创建 admin 账号。生产请设 SEED_ENABLED=false。");
    }
    List<String> origins = tokenProperties.getSecurity().getCorsAllowedOrigins();
    if (origins != null && origins.contains("*")) {
      violations.add("CORS 允许任意来源且携带凭证（yorvix.security.cors-allowed-origins 含 *）："
          + "任何站点都能发起携带凭证的跨域请求。生产请留空或配置受信域名。");
    }
    if (!tokenProperties.getSecurity().isCookieSecure()) {
      violations.add("refresh Cookie 未启用 Secure 标志（yorvix.security.cookie-secure=false）："
          + "HTTPS 环境下 Cookie 可能经明文请求发出。生产请设 COOKIE_SECURE=true。");
    }

    if (tokenProperties.getSecurity().isStrictMode() && !violations.isEmpty()) {
      throw new IllegalStateException(renderStrictError(violations));
    }
    warnUnsafeDefaults();
  }

  private String renderStrictError(List<String> violations) {
    StringBuilder message = new StringBuilder();
    message.append("\n");
    message.append("===== 严格模式（yorvix.security.strict-mode=true）检测到不安全配置，应用拒绝启动 =====\n");
    for (String violation : violations) {
      message.append("  - ").append(violation).append("\n");
    }
    message.append("\n");
    message.append("严格模式由 prod profile 自动开启（application-prod.yaml 的 yorvix.security.strict-mode）。\n");
    message.append("确需在非生产环境临时放行，可设 SECURITY_STRICT_MODE=false。\n");
    message.append("=============================================================");
    return message.toString();
  }

  private void warnUnsafeDefaults() {
    List<String> warnings = new ArrayList<>();
    if (tokenProperties.getInit().isSeedEnabled()) {
      warnings.add("种子数据初始化已开启：连上全新库会创建 admin 账号"
          + "（生产请设 SPRING_PROFILES_ACTIVE=prod 或 SEED_ENABLED=false）");
    }
    if (TokenProperties.DEFAULT_SEED_PASSWORD.equals(tokenProperties.getInit().getSeedPassword())) {
      warnings.add("种子账号仍使用默认密码，请勿用于生产"
          + "（可用 SEED_ADMIN_PASSWORD 注入强口令）");
    }
    List<String> origins = tokenProperties.getSecurity().getCorsAllowedOrigins();
    if (origins != null && origins.contains("*")) {
      warnings.add("CORS 允许任意来源且携带凭证：仅适用于本地联调"
          + "（生产请设 CORS_ALLOWED_ORIGINS 为受信域名，或留空以只允许同源）");
    }
    if (!tokenProperties.getSecurity().isCookieSecure()) {
      warnings.add("refresh Cookie 未启用 Secure 标志，仅适用于 http 本地开发"
          + "（HTTPS 环境需设 COOKIE_SECURE=true）");
    }
    if (warnings.isEmpty()) {
      return;
    }

    StringBuilder message = new StringBuilder();
    message.append("\n");
    message.append("============ 检测到「本地开发默认配置」仍在生效 ============\n");
    for (String warning : warnings) {
      message.append("  · ").append(warning).append("\n");
    }
    message.append("以上仅为提醒，不影响启动。生产部署请激活 prod profile：\n");
    message.append("  SPRING_PROFILES_ACTIVE=prod\n");
    message.append("=========================================================");
    log.warn(message.toString());
  }
}
