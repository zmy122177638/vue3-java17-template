package com.zmy.yorvix.security;

import java.util.Collection;

/**
 * 免鉴权路径匹配（{@code yorvix.security.white-list}）。
 *
 * <h2>匹配语义</h2>
 * <ul>
 *   <li><b>精确匹配</b>：{@code /api/auth/login} 只匹配该路径本身；</li>
 *   <li><b>子树通配</b>：{@code /api/public/**} 匹配 {@code /api/public} 本身及其任意后代。</li>
 * </ul>
 *
 * <h2>为什么不用 {@code startsWith} 前缀匹配</h2>
 * 这是本项目原来的一处隐患：前缀匹配会让"未来新增的 {@code /api/auth/login-audit}"
 * 因为恰好以 {@code /api/auth/login} 开头而**自动变成公开接口**（{@code /errors} 之于
 * {@code /error} 同理）。这类问题在开发期完全不暴露——接口一切正常，只是不再校验令牌——
 * 而真出事时几乎不会有人联想到白名单配置。改成精确匹配后，要放开一整棵子树必须
 * 显式写下 {@code /**}，意图在配置文件里就能看见。
 *
 * <p>纯函数、无状态，因此被两处复用：{@link AuthInterceptor} 的请求判定，
 * 以及 {@code StartupChecks} 的启动自检（校验白名单是否包含认证链路必需项）。
 * 放在一起是为了让两处的语义永远不会漂移。
 */
public final class WhiteList {

  /** 子树通配后缀 */
  public static final String SUFFIX_ANY = "/**";

  private WhiteList() {
  }

  /**
   * 单个配置项是否覆盖给定 URI。
   *
   * @param pattern 白名单配置项，如 {@code /api/auth/login} 或 {@code /api/public/**}
   * @param uri     请求路径（不含查询串）
   */
  public static boolean covers(String pattern, String uri) {
    if (pattern == null || pattern.isBlank() || uri == null || uri.isEmpty()) {
      return false;
    }
    if (pattern.endsWith(SUFFIX_ANY)) {
      String prefix = pattern.substring(0, pattern.length() - SUFFIX_ANY.length());
      // 通配同时覆盖"子树根"本身：/api/public/** 应能放行 /api/public
      return uri.equals(prefix) || uri.startsWith(prefix + "/");
    }
    return uri.equals(pattern);
  }

  /** 任一配置项覆盖即命中；{@code patterns} 为空视为不命中（即全部需要鉴权，fail-closed） */
  public static boolean anyCovers(Collection<String> patterns, String uri) {
    if (patterns == null || patterns.isEmpty()) {
      return false;
    }
    for (String pattern : patterns) {
      if (covers(pattern, uri)) {
        return true;
      }
    }
    return false;
  }
}
