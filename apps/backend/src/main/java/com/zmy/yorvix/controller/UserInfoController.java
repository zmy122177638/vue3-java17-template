package com.zmy.yorvix.controller;

import com.zmy.yorvix.common.api.Result;
import com.zmy.yorvix.response.auth.CurrentUserInfo;
import com.zmy.yorvix.security.AuthContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户信息接口（vben 前端契约：GET /api/user/info）。
 */
@RestController
@RequestMapping("/api/user")
public class UserInfoController {

  /** 获取当前用户信息（需 Bearer token） */
  @GetMapping("/info")
  public Result<CurrentUserInfo> info() {
    return Result.ok(CurrentUserInfo.of(AuthContext.require()));
  }
}
