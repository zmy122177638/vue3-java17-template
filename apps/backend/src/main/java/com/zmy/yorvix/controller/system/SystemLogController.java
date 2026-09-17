package com.zmy.yorvix.controller.system;

import com.zmy.yorvix.common.api.Result;
import com.zmy.yorvix.common.page.PageQuery;
import com.zmy.yorvix.common.page.PageResult;
import com.zmy.yorvix.response.system.LoginLogItem;
import com.zmy.yorvix.response.system.OperLogItem;
import com.zmy.yorvix.security.RequiresRoles;
import com.zmy.yorvix.service.system.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志查询接口（仅管理员，只读）。
 * <p>属于系统管理域：类级 {@code @RequiresRoles("ADMIN")} 一刀切，不参与 RBAC 授权，
 * 因此不声明按钮权限码（理由见 {@code common/permission/AuthCodes} 的注释）。
 * <p>只提供查询——日志的写入由 {@code AuditLogService} 与
 * {@code security/OperationLogInterceptor} 在业务链路里完成，接口层不暴露写入能力，
 * 否则"审计记录"就变成了可以被调用方伪造的数据。
 */
@RestController
@RequestMapping("/api/system/logs")
@RequiredArgsConstructor
@RequiresRoles("ADMIN")
public class SystemLogController {

  private final AuditLogService auditLogService;

  /** 登录日志分页（event 取 AuditLogService.LoginEvent 名称，如 FAILURE） */
  @GetMapping("/login/page")
  public Result<PageResult<LoginLogItem>> loginPage(PageQuery pageQuery,
      @RequestParam(required = false) String username,
      @RequestParam(required = false) String event) {
    return Result.ok(auditLogService.pageLogin(pageQuery, username, event));
  }

  /** 操作日志分页（success：1 成功 0 失败，不传表示不过滤） */
  @GetMapping("/oper/page")
  public Result<PageResult<OperLogItem>> operPage(PageQuery pageQuery,
      @RequestParam(required = false) String username,
      @RequestParam(required = false) String module,
      @RequestParam(required = false) Integer success) {
    return Result.ok(auditLogService.pageOper(pageQuery, username, module, success));
  }
}
