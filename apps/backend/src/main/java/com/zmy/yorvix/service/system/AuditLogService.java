package com.zmy.yorvix.service.system;

import com.zmy.yorvix.common.page.PageQuery;
import com.zmy.yorvix.common.page.PageResult;
import com.zmy.yorvix.response.system.LoginLogItem;
import com.zmy.yorvix.response.system.OperLogItem;
import com.zmy.yorvix.security.ClientInfo;

/**
 * 审计日志服务：登录日志与操作日志的写入与分页查询。
 *
 * <h2>为什么这两张表必须存在</h2>
 * 认证与权限做得再严，没有留痕就无法回答"谁在什么时候把谁的角色改了""谁在撞库"。
 * 这类信息**必须在事发当时落库**——事后从日志文件里翻是做不到的
 * （按账号/IP 过滤、按时间对账都需要可查询的数据）。
 *
 * <h2>写入失败的取舍</h2>
 * 审计写入**不影响主流程**：失败只记本地错误日志（实现见
 * {@link AuditLogServiceImpl}）。这是刻意的——把"审计写不进去"升级成
 * "登录不了/改不了数据"，在故障时会直接变成业务中断。代价是审计可能静默缺失，
 * 因此失败日志级别是 ERROR，需要纳入告警。
 *
 * @see AuditLogServiceImpl 写入语义（独立事务）
 */
public interface AuditLogService {

  /** 登录日志事件类型（前端按 {@code system.log.event.<name>} 做 i18n） */
  enum LoginEvent {
    /** 登录成功 */
    SUCCESS,
    /** 凭据校验失败（账号不存在或密码错误，两者**不区分**，避免账号枚举） */
    FAILURE,
    /** 凭据正确但账号被禁用 */
    DISABLED,
    /** 失败次数超限，被限流拦截（未执行口令校验） */
    LOCKED,
    /** 主动登出 */
    LOGOUT
  }

  /**
   * 操作日志写入参数。
   *
   * <p>{@code traceId} 不在参数里：它取自 MDC（当前请求上下文本身的属性），
   * 由实现类统一填充，避免每个调用点都要记得传——漏传只会静默少一个字段，
   * 而这类"悄悄缺数据"的问题在排查时最难发现。
   *
   * @param userId      操作人 ID（未登录/令牌无效为 null）
   * @param username    操作人账号
   * @param module      模块 / 操作对象类型（由路径推断，如 users/roles/menus）
   * @param method      HTTP 方法
   * @param uri         请求路径
   * @param targetId    操作对象主键（客体标识）；新建或无 id 的动作为 null
   * @param client      来源 IP 与 User-Agent
   * @param status      HTTP 状态码
   * @param success     业务是否成功（业务失败也算 false，即便 HTTP 是 200）
   * @param errorKey    失败原因的 i18n key，成功为 null
   * @param durationMs  耗时（毫秒）
   */
  record OperLogCommand(Long userId, String username, String module, String method, String uri,
                        Long targetId, ClientInfo client, int status, boolean success,
                        String errorKey, long durationMs) {
  }

  /**
   * 记录一次登录相关事件。
   *
   * @param username 尝试登录的账号（可为 null，如登出时拿不到令牌）
   * @param userId   账号存在时的用户 ID，否则 null
   * @param event    事件类型
   * @param detailKey 补充说明的 i18n key（成功/登出为 null）
   * @param client   来源 IP 与 User-Agent
   */
  void recordLogin(String username, Long userId, LoginEvent event, String detailKey,
      ClientInfo client);

  /** 记录一次系统管理域的写操作 */
  void recordOper(OperLogCommand command);

  /** 登录日志分页（{@code event} 为事件类型名，空表示不过滤） */
  PageResult<LoginLogItem> pageLogin(PageQuery pageQuery, String username, String event);

  /** 操作日志分页（{@code success} 为 null 表示不过滤） */
  PageResult<OperLogItem> pageOper(PageQuery pageQuery, String username, String module,
      Integer success);
}
