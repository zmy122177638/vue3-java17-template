package com.zmy.yorvix.security;

import com.zmy.yorvix.config.TokenProperties;
import com.zmy.yorvix.service.system.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Set;

/**
 * 操作日志拦截器：把系统管理域的**写操作**逐个记入 {@code sys_oper_log}。
 *
 * <h2>记录口径</h2>
 * <ul>
 *   <li>路径命中 {@link #AUDITED_PREFIXES}（系统管理域）；</li>
 *   <li>方法属于 {@link #MUTATING_METHODS}（GET/HEAD/OPTIONS 不记，
 *       否则查询接口会把审计表刷爆，反而淹没了真正要看的信息）。</li>
 * </ul>
 * 业务域如果需要审计，把前缀加进来即可；更细粒度的场景（如数据导出）
 * 可以直接调 {@link AuditLogService#recordOper} 显式记录。
 *
 * <h2>为什么注册顺序必须在 {@link AuthInterceptor} 之前</h2>
 * 拦截器的 {@code afterCompletion} 按**注册顺序的逆序**执行，且某个拦截器
 * {@code preHandle} 返回 false 时，Spring 只会回调"之前已返回 true 的"拦截器
 * （见 {@link AuthContextFilter} 的注释）。鉴权失败（403）恰恰是操作日志最需要记录的
 * 场景，所以本拦截器必须排在 AuthInterceptor 前面才能收到那次回调。
 * 注册见 {@code config/WebMvcConfig}。
 *
 * <h2>operator 从哪来</h2>
 * {@link AuthContext} 由 {@link AuthContextFilter} 在**整个过滤器链结束后**才清理，
 * 而拦截器的 afterCompletion 发生在过滤器链内部，因此在 403 场景下依然能读到
 * 已被 AuthInterceptor 注入的用户（这正是把 ThreadLocal 生命周期交给 Filter 的附带好处）。
 *
 * <h2>成功/失败怎么判定</h2>
 * 业务异常被 {@code GlobalExceptionHandler} 处理后会"变成"一个正常的响应，
 * 此时 afterCompletion 拿到的 {@code ex} 是 null，仅靠状态码无法区分
 * "改成了"与"业务校验不通过"（后者在本项目里是 HTTP 200 + 业务码）。
 * 因此约定：由 {@code GlobalExceptionHandler} 把失败原因的 i18n key 写入
 * {@link #ERROR_ATTRIBUTE}，本拦截器据此判定——这是两者之间唯一的耦合点，
 * 也是让审计表能反映真实成败的最简做法。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogInterceptor implements HandlerInterceptor {

  /** 失败原因（i18n key）的请求属性名，由 GlobalExceptionHandler 写入 */
  public static final String ERROR_ATTRIBUTE = OperationLogInterceptor.class.getName() + ".ERROR";

  /** 记入操作日志的路径前缀（系统管理域） */
  private static final List<String> AUDITED_PREFIXES = List.of("/api/system/");

  /** 只有写操作才留痕 */
  private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

  /** 与 sys_oper_log.module（VARCHAR(64)）一致 */
  private static final int MAX_MODULE_LENGTH = 64;

  /** 与 sys_oper_log.uri（VARCHAR(255)）一致 */
  private static final int MAX_URI_LENGTH = 255;

  private static final String START_ATTRIBUTE = OperationLogInterceptor.class.getName() + ".START";

  private final AuditLogService auditLogService;
  private final TokenProperties tokenProperties;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
      Object handler) {
    if (isAudited(request)) {
      request.setAttribute(START_ATTRIBUTE, System.nanoTime());
    }
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
      Object handler, Exception ex) {
    if (!isAudited(request)) {
      return;
    }
    Object start = request.getAttribute(START_ATTRIBUTE);
    long durationMs = start instanceof Long startNanos
        ? (System.nanoTime() - startNanos) / 1_000_000L
        : 0L;

    // ex 在这里几乎总是 null（异常已被 GlobalExceptionHandler 消化），因此失败原因以
    // GlobalExceptionHandler 写入的属性为准；两者都取不到时再看 HTTP 状态码兜底
    String errorKey = request.getAttribute(ERROR_ATTRIBUTE) instanceof String key ? key : null;
    if (errorKey == null && ex != null) {
      errorKey = ex.getClass().getSimpleName();
    }

    LoginUser user = AuthContext.getOrNull();
    String uri = request.getRequestURI();
    boolean success = errorKey == null && response.getStatus() < 400;

    AuditedTarget target = resolveTarget(uri);
    auditLogService.recordOper(new AuditLogService.OperLogCommand(
        user == null ? null : user.getUserId(),
        user == null ? null : user.getUsername(),
        target.module(),
        request.getMethod(),
        truncate(uri, MAX_URI_LENGTH),
        target.targetId(),
        ClientInfoResolver.resolve(request, tokenProperties.getSecurity().getClientIpHeader()),
        response.getStatus(),
        success,
        errorKey,
        durationMs));
  }

  private boolean isAudited(HttpServletRequest request) {
    if (!MUTATING_METHODS.contains(request.getMethod())) {
      return false;
    }
    String uri = request.getRequestURI();
    if (!StringUtils.hasText(uri)) {
      return false;
    }
    return AUDITED_PREFIXES.stream().anyMatch(uri::startsWith);
  }

  /**
   * 从审计路径解析操作对象（审计要求的「客体标识」）。
   *
   * <p>规则：前缀之后的第一段是**对象类型**（同时作为 module，如 {@code users}），
   * 第二段**只有是纯数字**才算对象主键。因此：
   * <ul>
   *   <li>{@code /api/system/users/3/status} -> {@code (users, 3)}；</li>
   *   <li>{@code /api/system/roles/5/menus} -> {@code (roles, 5)}；</li>
   *   <li>{@code /api/system/users}（新建）-> {@code (users, null)}；</li>
   *   <li>{@code /api/system/menus/sort}（动作名不是 id）-> {@code (menus, null)}。</li>
   * </ul>
   *
   * <p>不用正则而要逐段判断：路径段是外部输入，只有能完整解析成 {@code Long} 的才
   * 允许进入 BIGINT 列，解析失败一律按 null 处理——审计缺一个可选字段，
   * 远好过为此抛异常、把一次真实操作的记录整条丢掉。
   * <p>包内可见以便单测直接覆盖（这段逻辑"错了也不报错"，只能靠测试锁住）。
   */
  static AuditedTarget resolveTarget(String uri) {
    if (!StringUtils.hasText(uri)) {
      return AuditedTarget.NONE;
    }
    for (String prefix : AUDITED_PREFIXES) {
      if (!uri.startsWith(prefix)) {
        continue;
      }
      String[] segments = uri.substring(prefix.length()).split("/");
      if (segments.length == 0 || !StringUtils.hasText(segments[0])) {
        return AuditedTarget.NONE;
      }
      Long targetId = segments.length > 1 ? parseId(segments[1]) : null;
      return new AuditedTarget(truncate(segments[0], MAX_MODULE_LENGTH), targetId);
    }
    return AuditedTarget.NONE;
  }

  /** 路径段能完整解析成 {@code Long} 才视为对象主键；动作名（sort 等）与超范围数字返回 null */
  private static Long parseId(String segment) {
    if (!StringUtils.hasText(segment)) {
      return null;
    }
    try {
      return Long.parseLong(segment);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  /** 审计路径解析结果：{@code module} 是对象类型，{@code targetId} 可能为 null */
  record AuditedTarget(String module, Long targetId) {

    /** 未命中审计前缀、或路径里没有资源名时的占位值 */
    static final AuditedTarget NONE = new AuditedTarget(null, null);
  }
}
