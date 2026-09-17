package com.zmy.yorvix.service.user;

import com.zmy.yorvix.common.api.ResultCode;
import com.zmy.yorvix.common.exception.BizException;
import com.zmy.yorvix.mapper.sys.SysUserMapper;
import com.zmy.yorvix.model.sys.SysUser;
import com.zmy.yorvix.request.auth.ProfileUpdateRequest;
import com.zmy.yorvix.response.auth.ProfileItem;
import com.zmy.yorvix.service.system.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 本人资料服务实现。
 * <p>userId 由 Controller 从 {@code AuthContext} 取当前登录用户传入，
 * 而不是从请求参数取——后者会让调用方通过改参数去改别人的资料（越权的经典写法）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

  private final SysUserMapper userMapper;
  private final RoleService roleService;

  @Override
  @Transactional(readOnly = true)
  public ProfileItem get(Long userId) {
    SysUser user = requireUser(userId);
    // 用已加载的 is_super，避免 getRoleCodes 内部再读一次同一行
    return toItem(user, roleService.getRoleCodes(userId, user.isSuperUser()));
  }

  @Override
  @Transactional
  public ProfileItem update(Long userId, ProfileUpdateRequest request) {
    SysUser user = requireUser(userId);
    // 只覆盖这三个人可编辑字段。
    // 传 null 会真正把列置空（SysUser 上这三个字段标了 updateStrategy = ALWAYS，
    // 否则 MyBatis-Plus 默认的 NOT_NULL 策略会把 null 字段从 UPDATE 语句里剔除，
    // 表现为"清空邮箱后接口返回成功，但邮箱还在"）
    user.setNickname(request.getNickname());
    user.setEmail(request.getEmail());
    user.setPhone(request.getPhone());
    userMapper.updateById(user);
    // 昵称在 AuthContext / 导航栏里都会用到，但拦截器每个请求都重新查库，
    // 因此下一次请求拿到的就是新值，不需要刷新或作废令牌
    log.info("用户 {} 更新了个人资料", user.getUsername());
    return toItem(user, roleService.getRoleCodes(userId, user.isSuperUser()));
  }

  // ---------------- 内部 ----------------

  private SysUser requireUser(Long userId) {
    SysUser user = userMapper.selectById(userId);
    if (user == null) {
      throw new BizException(ResultCode.USER_NOT_FOUND);
    }
    return user;
  }

  private ProfileItem toItem(SysUser user, List<String> roles) {
    ProfileItem item = new ProfileItem();
    item.setUsername(user.getUsername());
    item.setNickname(user.getNickname());
    item.setEmail(user.getEmail());
    item.setPhone(user.getPhone());
    item.setRoles(new ArrayList<>(roles == null ? List.of() : roles));
    return item;
  }
}
