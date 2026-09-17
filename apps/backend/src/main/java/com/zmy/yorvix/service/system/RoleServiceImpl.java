package com.zmy.yorvix.service.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zmy.yorvix.mapper.sys.SysUserMapper;
import com.zmy.yorvix.mapper.system.SysRoleMapper;
import com.zmy.yorvix.mapper.system.SysUserRoleMapper;
import com.zmy.yorvix.model.sys.SysUser;
import com.zmy.yorvix.model.system.SysRole;
import com.zmy.yorvix.model.system.SysUserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 角色服务实现：每次请求实时查库，角色变更即时生效。
 * <p><b>这是一个显式的取舍，代价要写清楚</b>：不缓存换来的是"授权变更立即生效"，
 * 代价是每个受保护请求都要查 {@code sys_user_role} + {@code sys_role}（2 次 SQL）。
 * 表很小、几乎不变，所以这里完全可以用 Redis/本地缓存 + 变更时失效来换掉，
 * 但那样会引入"授权变更最多延迟一个 TTL"的语义。要改之前先确认这个语义可以接受，
 * 不要只看到"能省两次查询"就动手。
 * <p>调用方如果手上已经有 {@code SysUser}，请用
 * {@link #getRoleCodes(Long, boolean)} 以免重复读取同一行。
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

  private final SysRoleMapper roleMapper;
  private final SysUserRoleMapper userRoleMapper;
  private final SysUserMapper userMapper;

  @Override
  @Transactional(readOnly = true)
  public List<String> getRoleCodes(Long userId) {
    SysUser user = userMapper.selectById(userId);
    return getRoleCodes(userId, user != null && user.isSuperUser());
  }

  @Override
  @Transactional(readOnly = true)
  public List<String> getRoleCodes(Long userId, boolean superUser) {
    List<String> codes = queryRoleCodes(userId);
    if (superUser && !codes.contains(ADMIN_CODE)) {
      // 超管代码层全量放行：不依赖 sys_user_role，改库把角色全删了也照样管理，
      // 补上 ADMIN 后鉴权（@RequiresRoles）、菜单下发、前端权限码三处自动一致
      List<String> merged = new ArrayList<>(codes);
      merged.add(ADMIN_CODE);
      return merged;
    }
    return codes;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isAdmin(Long userId) {
    return getRoleCodes(userId).contains(ADMIN_CODE);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isSuper(Long userId) {
    SysUser user = userMapper.selectById(userId);
    return user != null && user.isSuperUser();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasRoleBinding(Long userId) {
    return userRoleMapper.selectCount(
        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId)) > 0;
  }

  private List<String> queryRoleCodes(Long userId) {
    List<SysUserRole> bindings = userRoleMapper.selectList(
        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
    if (bindings.isEmpty()) {
      return Collections.emptyList();
    }
    List<Long> roleIds = bindings.stream().map(SysUserRole::getRoleId).toList();
    return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
            .in(SysRole::getId, roleIds)
            .eq(SysRole::getStatus, 1))
        .stream()
        .map(SysRole::getCode)
        .toList();
  }
}
