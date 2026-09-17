package com.zmy.yorvix.service.user;

import com.zmy.yorvix.request.auth.ProfileUpdateRequest;
import com.zmy.yorvix.response.auth.ProfileItem;

/**
 * 本人资料服务（个人中心「基本设置」）。
 * <p><b>这是第三个权限域</b>，与另外两个并列：
 * <ul>
 *   <li>{@code service.system} —— 系统管理域，ADMIN 独占，管理**他人**账号与角色；</li>
 *   <li>{@code service.auth} —— 认证域，登录/登出/刷新/改密；</li>
 *   <li>{@code service.user}（本接口）—— 本人域，**只能操作自己**的资料。</li>
 * </ul>
 * 三个域的接口路径也对应着分：{@code /api/system/**}、{@code /api/auth/**}、{@code /api/user/**}。
 * 新增功能时先判断它属于哪个域，再决定鉴权方式（@RequiresRoles / 无需额外鉴权 / 本人限定）。
 */
public interface UserProfileService {

  /** 读取本人资料 */
  ProfileItem get(Long userId);

  /**
   * 更新本人资料。
   * <p>返回更新后的完整资料，而不是 {@code boolean}：前端拿到即可就地刷新，
   * 省掉一次 {@code GET} 往返（资料只有几个字段，回传成本可忽略）。
   */
  ProfileItem update(Long userId, ProfileUpdateRequest request);
}
