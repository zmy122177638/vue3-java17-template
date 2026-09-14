package com.zmy.yorvix.controller;

import com.zmy.yorvix.common.api.Result;
import com.zmy.yorvix.config.TokenProperties;
import com.zmy.yorvix.request.auth.ChangePasswordRequest;
import com.zmy.yorvix.request.auth.LoginRequest;
import com.zmy.yorvix.request.auth.RefreshRequest;
import com.zmy.yorvix.response.auth.LoginResponse;
import com.zmy.yorvix.response.auth.LoginUserInfo;
import com.zmy.yorvix.security.TokenService;
import com.zmy.yorvix.service.auth.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * 认证接口。除 login/refresh/logout 外均需携带 Bearer token。
 * <p>Opaque Token + Redis 方案，vben 前端契约：
 * <ul>
 *   <li>login → Result{data:{accessToken}}，同时写入 refresh token HttpOnly Cookie；</li>
 *   <li>refresh → 响应体为裸 access token 字符串（非 Result 包装），token 取请求体或 Cookie，且轮换旧 refresh token；</li>
 *   <li>logout → 删除 Authorization 头中的 access token 与 Cookie 中 refresh token 对应的 Redis key；</li>
 *   <li>codes → 当前用户权限编码数组（按钮级权限控制）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  /** refresh token 的 HttpOnly Cookie 名（仅作用于 /api/auth 路径） */
  public static final String REFRESH_COOKIE = "yorvix_refresh_token";

  private final AuthService authService;
  private final TokenProperties tokenProperties;
  private final TokenService tokenService;

  /** 登录（免鉴权）：签发 access token，并写入 refresh token Cookie */
  @PostMapping("/login")
  public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
      HttpServletResponse response) {
    LoginResponse login = authService.login(request);
    writeRefreshCookie(response, login.getRefreshToken());
    return Result.ok(login);
  }

  /** 登出（免鉴权）：删除 Authorization 头中的 access token 与 Cookie 中 refresh token 的 Redis 记录 */
  @PostMapping("/logout")
  public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    authService.logout(resolveBearerToken(request));
    tokenService.revokeRefresh(getCookieValue(request));
    clearRefreshCookie(response);
    return Result.ok();
  }

  /**
   * 刷新令牌（免鉴权）：响应体为裸 access token 字符串（vben 前端约定）。
   * refresh token 取值优先级：请求体 token 字段 &gt; refresh token Cookie。
   */
  @PostMapping("/refresh")
  public String refresh(@RequestBody(required = false) RefreshRequest request,
      HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    String refreshToken = request != null && StringUtils.hasText(request.getToken())
        ? request.getToken()
        : getCookieValue(httpRequest);
    LoginResponse login = authService.refresh(refreshToken);
    writeRefreshCookie(httpResponse, login.getRefreshToken());
    return login.getAccessToken();
  }

  /** 当前用户访问码（模板默认为空数组；按钮级权限由各项目自行扩展） */
  @GetMapping("/codes")
  public Result<List<String>> codes() {
    return Result.ok(List.of());
  }

  /** 当前登录用户信息（含角色与权限） */
  @GetMapping("/me")
  public Result<LoginUserInfo> me() {
    return Result.ok(authService.me());
  }

  /** 修改当前用户密码 */
  @PutMapping("/password")
  public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
    authService.changePassword(request);
    return Result.ok();
  }

  private void writeRefreshCookie(HttpServletResponse response, String refreshToken) {
    response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, refreshToken)
        .httpOnly(true)
        .path("/api/auth")
        .sameSite("Lax")
        .maxAge(Duration.ofSeconds(tokenProperties.getRefreshExpireMinutes() * 60))
        .build()
        .toString());
  }

  private void clearRefreshCookie(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, "")
        .httpOnly(true)
        .path("/api/auth")
        .sameSite("Lax")
        .maxAge(Duration.ZERO)
        .build()
        .toString());
  }

  private String getCookieValue(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (REFRESH_COOKIE.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  private String resolveBearerToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
      return header.substring(7);
    }
    return null;
  }
}
