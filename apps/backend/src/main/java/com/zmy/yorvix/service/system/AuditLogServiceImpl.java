package com.zmy.yorvix.service.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zmy.yorvix.common.page.PageQuery;
import com.zmy.yorvix.common.page.PageResult;
import com.zmy.yorvix.config.TraceIdFilter;
import com.zmy.yorvix.mapper.system.SysLoginLogMapper;
import com.zmy.yorvix.mapper.system.SysOperLogMapper;
import com.zmy.yorvix.model.system.SysLoginLog;
import com.zmy.yorvix.model.system.SysOperLog;
import com.zmy.yorvix.response.system.LoginLogItem;
import com.zmy.yorvix.response.system.OperLogItem;
import com.zmy.yorvix.security.ClientInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 审计日志服务实现。
 *
 * <h2>为什么写入用独立事务（{@code REQUIRES_NEW}）</h2>
 * 审计记录必须在**业务失败时也留下来**——"谁能把这次越权尝试的痕迹一起回滚掉"，
 * 正是审计最需要防的事。典型场景：管理员删除角色失败（角色已被绑定，抛
 * {@code BizException}）→ 外层事务回滚。如果审计写入挂在同一个事务里，
 * 这条"谁尝试删除了哪个角色"的记录会跟着消失，审计表只剩下成功案例，价值大打折扣。
 * <p>反之，登录失败路径本身没有外层事务（{@code AuthServiceImpl#login} 故意不加
 * {@code @Transactional}，见其注释），独立事务在这里同样成立。
 *
 * <h2>为什么失败只记日志、不抛出</h2>
 * 见 {@link AuditLogService} 的接口注释：把审计故障升级成业务中断得不偿失。
 * 但这里会把失败打成 ERROR，需纳入告警——否则审计表"悄悄不再增长"没人会发现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

  /** 列表允许的前端排序字段（白名单，理由见 SystemUserServiceImpl 同名常量） */
  private static final List<String> LOGIN_SORTABLE_FIELDS = List.of("username", "createdAt");
  private static final List<String> OPER_SORTABLE_FIELDS =
      List.of("username", "module", "status", "durationMs", "createdAt");

  private final SysLoginLogMapper loginLogMapper;
  private final SysOperLogMapper operLogMapper;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordLogin(String username, Long userId, LoginEvent event, String detailKey,
      ClientInfo client) {
    try {
      SysLoginLog log = new SysLoginLog();
      log.setUsername(username);
      log.setUserId(userId);
      log.setEvent(event.name());
      log.setDetail(detailKey);
      ClientInfo info = client == null ? ClientInfo.unknown() : client;
      log.setIp(info.ip());
      log.setUserAgent(info.userAgent());
      log.setTraceId(currentTraceId());
      loginLogMapper.insert(log);
    } catch (Exception e) {
      // 审计写不进去不能拖垮登录本身（见类注释），但必须能被告警发现
      log.error("登录日志写入失败: username={}, event={}", username, event, e);
    }
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordOper(OperLogCommand command) {
    try {
      SysOperLog log = new SysOperLog();
      log.setUserId(command.userId());
      log.setUsername(command.username());
      log.setModule(command.module());
      log.setMethod(command.method());
      log.setUri(command.uri());
      ClientInfo info = command.client() == null ? ClientInfo.unknown() : command.client();
      log.setIp(info.ip());
      log.setStatus(command.status());
      log.setSuccess(command.success() ? 1 : 0);
      log.setErrorMessage(command.errorKey());
      log.setDurationMs(command.durationMs());
      log.setTargetId(command.targetId());
      log.setTraceId(currentTraceId());
      operLogMapper.insert(log);
    } catch (Exception e) {
      log.error("操作日志写入失败: uri={}, user={}", command.uri(), command.username(), e);
    }
  }

  /**
   * 当前请求的 traceId，取自 MDC（由 {@code config/TraceIdFilter} 写入）。
   * <p>为什么从 MDC 取而不是当参数传：写入点有登录、登出、操作拦截器三处，
   * 参数传法要求每个调用点都记得带上，漏一个就静默少一个字段——traceId 又恰恰是
   * "出问题时才想起来要"的数据。而它本来就是当前请求上下文的属性，MDC 是权威来源。
   * <p>成立的前提是**审计写入与请求在同一线程**（本项目为同步写入）。若将来改为异步，
   * 必须在提交任务前把 MDC 一并带过去（{@code MDC.getCopyOfContextMap()}），
   * 否则这里会静默变成 null。
   */
  private String currentTraceId() {
    String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
    return StringUtils.hasText(traceId) ? traceId : null;
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<LoginLogItem> pageLogin(PageQuery pageQuery, String username, String event) {
    LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(username)) {
      wrapper.like(SysLoginLog::getUsername, username);
    }
    if (StringUtils.hasText(event)) {
      // 事件类型来自枚举名，统一大写后精确匹配；非法值时查不到数据（比 400 更省事，也无害）
      wrapper.eq(SysLoginLog::getEvent, event.trim().toUpperCase(Locale.ROOT));
    }
    Page<SysLoginLog> page = loginLogMapper.selectPage(
        pageQuery.toMpPage(LOGIN_SORTABLE_FIELDS), wrapper);
    return PageResult.of(page, LoginLogItem::of);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<OperLogItem> pageOper(PageQuery pageQuery, String username, String module,
      Integer success) {
    LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(username)) {
      wrapper.like(SysOperLog::getUsername, username);
    }
    if (StringUtils.hasText(module)) {
      wrapper.like(SysOperLog::getModule, module);
    }
    if (success != null) {
      wrapper.eq(SysOperLog::getSuccess, success);
    }
    Page<SysOperLog> page = operLogMapper.selectPage(
        pageQuery.toMpPage(OPER_SORTABLE_FIELDS), wrapper);
    return PageResult.of(page, OperLogItem::of);
  }
}
