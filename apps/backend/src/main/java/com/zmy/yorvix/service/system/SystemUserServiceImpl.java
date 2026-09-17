package com.zmy.yorvix.service.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zmy.yorvix.common.api.ResultCode;
import com.zmy.yorvix.common.exception.BizException;
import com.zmy.yorvix.common.page.PageQuery;
import com.zmy.yorvix.common.page.PageResult;
import com.zmy.yorvix.mapper.system.SysRoleMapper;
import com.zmy.yorvix.mapper.system.SysUserRoleMapper;
import com.zmy.yorvix.mapper.sys.SysUserMapper;
import com.zmy.yorvix.model.sys.SysUser;
import com.zmy.yorvix.model.system.SysRole;
import com.zmy.yorvix.model.system.SysUserRole;
import com.zmy.yorvix.request.system.UserCreateRequest;
import com.zmy.yorvix.request.system.UserUpdateRequest;
import com.zmy.yorvix.response.system.RoleItem;
import com.zmy.yorvix.response.system.UserItem;
import com.zmy.yorvix.security.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统用户管理服务实现（仅管理员调用，controller 层已加 @RequiresRoles）。
 * <p>权限约束：
 * <ul>
 *   <li>超管账号（is_super=1）不出现在列表，且不允许被任何人操作（本人改密码走个人中心）；</li>
 *   <li>内置 ADMIN 角色仅超管可分配；</li>
 *   <li>持有 ADMIN 角色的用户仅超管可操作（规则 X：管理员之间互不可操作）；</li>
 *   <li>创建/编辑必须分配至少一个启用角色，避免出现“能登录但无任何权限”的账号。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SystemUserServiceImpl implements SystemUserService {

  /**
   * 列表允许的前端排序字段（白名单）。
   * <p>排序列会拼进 ORDER BY，因此只放前端字段名、且必须是"展示型"列；
   * 密码散列等敏感列永远不在此列。
   */
  private static final List<String> SORTABLE_FIELDS =
      List.of("createdAt", "email", "nickname", "phone", "status", "username");

  private final SysUserMapper userMapper;
  private final SysUserRoleMapper userRoleMapper;
  private final SysRoleMapper roleMapper;
  private final PasswordEncoder passwordEncoder;
  private final RoleService roleService;

  @Override
  @Transactional(readOnly = true)
  public PageResult<UserItem> page(PageQuery pageQuery, String keyword, Integer status,
      Long operatorId) {
    LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
        // 超管账号不展示在用户管理（任何角色都看不到、也无法操作）
        .ne(SysUser::getIsSuper, 1);
    if (StringUtils.hasText(keyword)) {
      wrapper.and(w -> w.like(SysUser::getUsername, keyword)
          .or().like(SysUser::getNickname, keyword));
    }
    if (status != null) {
      wrapper.eq(SysUser::getStatus, status);
    }
    Page<SysUser> page = userMapper.selectPage(pageQuery.toMpPage(SORTABLE_FIELDS), wrapper);
    List<SysUser> records = page.getRecords();
    if (records.isEmpty()) {
      return PageResult.of(page, user -> toItem(user, true, List.of(), Map.of()));
    }

    // 批量预取角色绑定与角色名称，避免原实现「每行 2~4 次查询」的 N+1
    List<SysUserRole> bindings = userRoleMapper.selectList(
        new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId,
            records.stream().map(SysUser::getId).toList()));
    Map<Long, List<Long>> roleIdsByUser = bindings.stream()
        .collect(Collectors.groupingBy(SysUserRole::getUserId,
            Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())));
    Map<Long, SysRole> roleById = bindings.isEmpty() ? Map.of()
        : roleMapper.selectByIds(
                bindings.stream().map(SysUserRole::getRoleId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(SysRole::getId, role -> role, (a, b) -> a));

    // 管理员判定只认「启用」的 ADMIN 角色，与 RoleServiceImpl.queryRoleCodes 的 status=1 语义严格对齐：
    // 角色被禁用后其持有者不应再被当作管理员，否则会出现「已被禁用的管理员仍能操作他人」的越权
    Set<Long> enabledAdminRoleIds = roleById.values().stream()
        .filter(role -> role.getStatus() != null && role.getStatus() == 1)
        .filter(role -> RoleService.ADMIN_CODE.equals(role.getCode()))
        .map(SysRole::getId)
        .collect(Collectors.toSet());
    Set<Long> adminUserIds = bindings.stream()
        .filter(binding -> enabledAdminRoleIds.contains(binding.getRoleId()))
        .map(SysUserRole::getUserId)
        .collect(Collectors.toSet());

    boolean operatorIsSuper = roleService.isSuper(operatorId);
    return PageResult.of(page, user -> toItem(user,
        operatorIsSuper || !adminUserIds.contains(user.getId()),
        roleIdsByUser.getOrDefault(user.getId(), List.of()),
        roleById));
  }

  @Override
  @Transactional
  public boolean create(UserCreateRequest request, Long operatorId) {
    Long occupied = userMapper.selectCount(
        new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
    if (occupied > 0) {
      throw new BizException(ResultCode.BAD_REQUEST, "error.username.exists");
    }
    validateRoleAssignment(request.getRoleIds(), operatorId);
    SysUser user = new SysUser();
    user.setUsername(request.getUsername());
    user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    user.setNickname(request.getNickname());
    user.setEmail(request.getEmail());
    user.setPhone(request.getPhone());
    user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    userMapper.insert(user);
    replaceRoles(user.getId(), request.getRoleIds());
    return true;
  }

  @Override
  @Transactional
  public boolean update(Long id, UserUpdateRequest request, Long operatorId) {
    SysUser user = requireUser(id);
    ensureOperable(user, operatorId);
    validateRoleAssignment(request.getRoleIds(), operatorId);
    // 先判定"是否从启用切到禁用"，再覆盖字段（顺序不能反，否则拿不到原状态）
    boolean disabled = isDisabling(user.getStatus(), request.getStatus());
    user.setNickname(request.getNickname());
    user.setEmail(request.getEmail());
    user.setPhone(request.getPhone());
    if (request.getStatus() != null) {
      user.setStatus(request.getStatus());
    }
    userMapper.updateById(user);
    if (disabled) {
      revokeAllSessions(id);
    }
    replaceRoles(id, request.getRoleIds());
    return true;
  }

  @Override
  @Transactional
  public boolean updateStatus(Long id, Integer status, Long operatorId) {
    SysUser user = requireUser(id);
    ensureOperable(user, operatorId);
    boolean disabled = isDisabling(user.getStatus(), status);
    user.setStatus(status);
    userMapper.updateById(user);
    if (disabled) {
      revokeAllSessions(id);
    }
    return true;
  }

  @Override
  @Transactional
  public boolean resetPassword(Long id, String rawPassword, Long operatorId) {
    SysUser user = requireUser(id);
    ensureOperable(user, operatorId);
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
    userMapper.updateById(user);
    // 重置密码后把该用户所有会话踢下线，否则旧令牌能一直用到 TTL 过期
    revokeAllSessions(id);
    return true;
  }

  @Override
  @Transactional
  public boolean delete(Long id, Long operatorId) {
    SysUser user = requireUser(id);
    if (Objects.equals(id, operatorId)) {
      throw new BizException(ResultCode.BAD_REQUEST, "error.user.delete.self");
    }
    // 超管与管理员账号的保护统一由 ensureOperable 承担（按 is_super / ADMIN 角色判定），
    // 不再按用户名硬编码判断内置账号
    ensureOperable(user, operatorId);
    userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
    userMapper.deleteById(id);
    return true;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Long> getRoleIds(Long userId) {
    return userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
        .stream()
        .map(SysUserRole::getRoleId)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<RoleItem> assignableRoles(Long operatorId) {
    boolean operatorIsSuper = roleService.isSuper(operatorId);
    return roleMapper.selectList(
            new LambdaQueryWrapper<SysRole>().eq(SysRole::getStatus, 1))
        .stream()
        // 非超管：可分配范围排除内置管理员角色，防止横向提权
        .filter(role -> operatorIsSuper || !RoleService.ADMIN_CODE.equals(role.getCode()))
        // 内置管理员角色排最前，其余按 id 升序
        .sorted(Comparator
            .comparing((SysRole role) -> !RoleService.ADMIN_CODE.equals(role.getCode()))
            .thenComparing(SysRole::getId))
        .map(this::toRoleItem)
        .toList();
  }

  // ---------------- 内部 ----------------

  /** 是否从启用切到禁用（newStatus 为 null 表示"本次不修改状态"，不算禁用） */
  private static boolean isDisabling(Integer previousStatus, Integer newStatus) {
    return newStatus != null && isEnabled(previousStatus) && !isEnabled(newStatus);
  }

  private static boolean isEnabled(Integer status) {
    return status != null && status == 1;
  }

  /**
   * 让该用户所有会话立即失效（踢下线）。
   * <p>实现是令牌版本 +1：已签发的 access / refresh token 版本不再匹配，
   * 拦截器与刷新接口都会拒绝，无需维护 userId → token 的反查索引。
   * <p>禁用场景必须这样做，而不能只依赖拦截器的状态校验——重新启用后旧令牌会"复活"。
   */
  private void revokeAllSessions(Long userId) {
    userMapper.bumpTokenVersion(userId);
  }

  private SysUser requireUser(Long id) {
    SysUser user = userMapper.selectById(id);
    if (user == null) {
      throw new BizException(ResultCode.NOT_FOUND, "error.user.not.found");
    }
    return user;
  }

  /**
   * 校验待分配角色：至少一个、必须存在且启用、ADMIN 角色仅超管可分配。
   * <p>“必须分配角色”是为了从源头避免造出能登录却没有任何权限的账号。
   */
  private void validateRoleAssignment(List<Long> roleIds, Long operatorId) {
    Set<Long> unique = roleIds == null ? Set.of() : new LinkedHashSet<>(roleIds);
    if (unique.isEmpty()) {
      throw new BizException(ResultCode.BAD_REQUEST, "error.user.role.required");
    }
    List<SysRole> roles = roleMapper.selectByIds(unique);
    if (roles.size() != unique.size()) {
      throw new BizException(ResultCode.BAD_REQUEST, "error.role.invalid");
    }
    boolean operatorIsSuper = roleService.isSuper(operatorId);
    for (SysRole role : roles) {
      if (role.getStatus() == null || role.getStatus() != 1) {
        // 占位符参数由 GlobalExceptionHandler 按请求语言插值（见 i18n/messages*.properties）
        throw new BizException(ResultCode.BAD_REQUEST, "error.role.disabled", role.getName());
      }
      if (RoleService.ADMIN_CODE.equals(role.getCode()) && !operatorIsSuper) {
        throw new BizException(ResultCode.BAD_REQUEST, "error.role.admin.assign.denied");
      }
    }
  }

  /**
   * 操作对象保护：超管账号一律不可操作；管理员账号仅超管可操作（规则 X）。
   * <p>管理员之间互不可操作，避免互相禁用/删除/摘角色导致系统失去管理能力。
   */
  private void ensureOperable(SysUser target, Long operatorId) {
    if (target.isSuperUser()) {
      throw new BizException(ResultCode.BAD_REQUEST, "error.super.immutable");
    }
    if (roleService.isAdmin(target.getId()) && !roleService.isSuper(operatorId)) {
      throw new BizException(ResultCode.BAD_REQUEST, "error.admin.protected");
    }
  }

  private void replaceRoles(Long userId, List<Long> roleIds) {
    userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
    if (roleIds == null || roleIds.isEmpty()) {
      return;
    }
    // 去重：roleIds 重复会触发 uk_user_role 唯一键冲突
    List<SysUserRole> bindings = new LinkedHashSet<>(roleIds).stream()
        .map(roleId -> {
          SysUserRole binding = new SysUserRole();
          binding.setUserId(userId);
          binding.setRoleId(roleId);
          return binding;
        })
        .toList();
    userRoleMapper.insertBatch(bindings);
  }

  private RoleItem toRoleItem(SysRole role) {
    RoleItem item = new RoleItem();
    item.setId(role.getId());
    item.setCode(role.getCode());
    item.setName(role.getName());
    item.setStatus(role.getStatus());
    item.setRemark(role.getRemark());
    item.setCreatedAt(role.getCreatedAt());
    return item;
  }

  /**
   * 组装列表项。角色绑定与角色名由 {@link #page} 批量预取后传入，此处不再查库。
   *
   * @param operatorCanOperate 当前操作者是否有权维护该用户（超管恒 true）
   * @param roleById           角色 id -> 角色（用于展示角色名）
   */
  private UserItem toItem(SysUser user, boolean operatorCanOperate, List<Long> roleIds,
      Map<Long, SysRole> roleById) {
    UserItem item = new UserItem();
    item.setId(user.getId());
    item.setUsername(user.getUsername());
    item.setNickname(user.getNickname());
    item.setEmail(user.getEmail());
    item.setPhone(user.getPhone());
    item.setStatus(user.getStatus());
    item.setCreatedAt(user.getCreatedAt());
    // 前端按此字段决定操作按钮显隐（后端仍会强校验，这里只用于界面呈现）
    item.setEditable(!user.isSuperUser() && operatorCanOperate);
    item.setRoleIds(roleIds);
    item.setRoleNames(roleIds.stream()
        .map(id -> {
          SysRole role = roleById.get(id);
          return role == null ? String.valueOf(id) : role.getName();
        })
        .toList());
    return item;
  }
}
