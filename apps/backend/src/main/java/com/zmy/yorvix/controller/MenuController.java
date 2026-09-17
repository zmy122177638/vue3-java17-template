package com.zmy.yorvix.controller;

import com.zmy.yorvix.common.api.Result;
import com.zmy.yorvix.security.AuthContext;
import com.zmy.yorvix.service.system.MenuService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 动态菜单下发接口（vben 前端契约：GET /api/menu/all）。
 * <p>mixed 模式下前端把返回的路由树与本地路由合并；
 * title 按 Accept-Language 下发对应语言文本。
 */
@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuController {

  private final MenuService menuService;

  @GetMapping("/all")
  public Result<List<Map<String, Object>>> all(HttpServletRequest request) {
    String lang = resolveLang(request.getHeader("Accept-Language"));
    return Result.ok(menuService.getMenuRoutes(AuthContext.require(), lang));
  }

  /** 请求头 Accept-Language 形如 zh-CN / en-US（前端 requestClient 统一携带） */
  private String resolveLang(String acceptLanguage) {
    if (acceptLanguage != null && acceptLanguage.toLowerCase().contains("en")) {
      return MenuService.LANG_EN;
    }
    return MenuService.LANG_ZH;
  }
}
