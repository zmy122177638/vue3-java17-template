package com.zmy.yorvix.config;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 菜单相关配置，对应 YAML 结构：
 * <pre>
 * yorvix:
 *   menu:
 *     home-path: /dashboard   # 登录后的全局统一首页；留空表示不配置
 *     admin-only-paths:       # ADMIN 独占菜单的路由地址前缀
 *       - /system
 * </pre>
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "yorvix.menu")
public class MenuProperties {

  /**
   * 登录后的全局统一首页（所有用户一致），如 {@code /dashboard}。
   * <p><b>留空（默认）= 不干预</b>：回退为"自动取该用户可见菜单树中第一个可渲染页面"。
   * <p>注意：这里不做可见性校验 —— 它通常指向前端本地固定路由（后端菜单表里查不到），
   * 因此填写的地址需保证所有用户都能访问，否则会出现 404/403。
   */
  @Pattern(regexp = "^(/\\S*)?$", message = "yorvix.menu.home-path 需以 / 开头且不含空格（留空表示不配置）")
  private String homePath = "";

  /**
   * ADMIN 独占菜单的路由地址前缀（如 {@code /system}）。
   * <p>命中的菜单及其全部后代：受 {@code @RequiresRoles("ADMIN")} 保护、不参与 RBAC 授权。
   * 后端下发菜单树时为它们打 {@code adminOnly} 标记（前端据此在授权树中隐藏），
   * 写入角色授权时再做一次兜底剔除，避免手工构造的请求写入无效授权。
   * <p><b>新增 ADMIN 独占的顶级菜单时必须同步此配置</b>，
   * 否则会出现"菜单能被勾选授权、被分配者一访问接口就 403"的难排查问题。
   */
  private List<String> adminOnlyPaths = List.of("/system");
}
