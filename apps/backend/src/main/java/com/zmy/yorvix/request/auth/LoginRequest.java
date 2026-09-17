package com.zmy.yorvix.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录请求。
 * <p>长度上限是**安全边界**而不是形式校验：
 * <ul>
 *   <li>username 会参与登录限流的 Redis key 与审计日志落库，长度上限与
 *       {@code sys_user.username VARCHAR(64)} 对齐；</li>
 *   <li>password 会进入 PBKDF2 计算，必须有上限，否则超大请求体可以直接放大单次
 *       登录的成本。上限取 128（创建用户时限制 32，留足余量以免历史口令被拒）。</li>
 * </ul>
 * <p>注意：约束在进入 Controller 前生效，因此"超长用户名"这类请求不会触发限流计数——
 * 但它也永远不可能命真实账号，不构成暴力破解面。
 */
@Getter
@Setter
public class LoginRequest {

  @NotBlank
  @Size(max = 64)
  private String username;

  @NotBlank
  @Size(max = 128)
  private String password;
}
