package com.zmy.yorvix.service.system;

import com.zmy.yorvix.common.api.ResultCode;
import com.zmy.yorvix.common.exception.BizException;
import com.zmy.yorvix.mapper.sys.SysUserMapper;
import com.zmy.yorvix.mapper.system.SysRoleMapper;
import com.zmy.yorvix.mapper.system.SysUserRoleMapper;
import com.zmy.yorvix.model.sys.SysUser;
import com.zmy.yorvix.model.system.SysRole;
import com.zmy.yorvix.model.system.SysUserRole;
import com.zmy.yorvix.request.system.UserCreateRequest;
import com.zmy.yorvix.response.system.RoleItem;
import com.zmy.yorvix.security.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 系统用户管理：越权保护与"踢下线"语义。
 *
 * <h2>这份测试在守什么</h2>
 * 这里的规则都是**横向越权/提权**的边界，破了不会报错，只会让某个管理员悄悄获得更大权力：
 * <ul>
 *   <li>超管账号不可被任何人操作；</li>
 *   <li>管理员之间互不可操作（防止互相禁用后系统失去管理能力）；</li>
 *   <li>只有超管能分配内置 ADMIN 角色；</li>
 *   <li>不能删除自己；</li>
 *   <li>禁用与重置密码必须递增 token_version（否则旧令牌会"复活"或继续可用）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SystemUserServiceImplTest {

  private static final Long OPERATOR = 9L;
  private static final Long TARGET = 2L;

  @Mock
  private SysUserMapper userMapper;

  @Mock
  private SysUserRoleMapper userRoleMapper;

  @Mock
  private SysRoleMapper roleMapper;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private RoleService roleService;

  private SystemUserServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new SystemUserServiceImpl(userMapper, userRoleMapper, roleMapper, passwordEncoder,
        roleService);
  }

  // ---------------- 创建 ----------------

  @Test
  @DisplayName("用户名重复：拒绝创建")
  void rejectsDuplicateUsername() {
    when(userMapper.selectCount(any())).thenReturn(1L);

    BizException thrown = catchThrowableOfType(
        () -> service.create(createRequest(Set.of(1L)), OPERATOR), BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode());
    assertThat(thrown.getMessage()).isEqualTo("error.username.exists");
    verify(userMapper, never()).insert(any(SysUser.class));
  }

  @Test
  @DisplayName("未分配角色：拒绝创建（避免造出能登录却无任何权限的废号）")
  void rejectsEmptyRoleAssignment() {
    when(userMapper.selectCount(any())).thenReturn(0L);

    BizException thrown = catchThrowableOfType(
        () -> service.create(createRequest(Set.of()), OPERATOR), BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getMessage()).isEqualTo("error.user.role.required");
  }

  @Test
  @DisplayName("分配了已禁用的角色：拒绝并带上角色名（错误信息里要有可定位的信息）")
  void rejectsDisabledRole() {
    when(userMapper.selectCount(any())).thenReturn(0L);
    when(roleMapper.selectByIds(Set.of(1L))).thenReturn(List.of(role(1L, "OPS", 0, "运营")));

    BizException thrown = catchThrowableOfType(
        () -> service.create(createRequest(Set.of(1L)), OPERATOR), BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getMessage()).isEqualTo("error.role.disabled");
    assertThat(thrown.getArgs()).containsExactly("运营");
  }

  @Test
  @DisplayName("非超管分配内置 ADMIN 角色：拒绝（否则同级管理员可自行提权）")
  void rejectsAdminRoleAssignedByNonSuper() {
    when(userMapper.selectCount(any())).thenReturn(0L);
    when(roleMapper.selectByIds(Set.of(1L)))
        .thenReturn(List.of(role(1L, RoleService.ADMIN_CODE, 1, "管理员")));
    when(roleService.isSuper(OPERATOR)).thenReturn(false);

    BizException thrown = catchThrowableOfType(
        () -> service.create(createRequest(Set.of(1L)), OPERATOR), BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getMessage()).isEqualTo("error.role.admin.assign.denied");
  }

  @Test
  @DisplayName("角色 ID 不存在：拒绝（防止写入无效绑定）")
  void rejectsUnknownRoleId() {
    when(userMapper.selectCount(any())).thenReturn(0L);
    when(roleMapper.selectByIds(Set.of(1L, 2L))).thenReturn(List.of(role(1L, "OPS", 1, "运营")));

    BizException thrown = catchThrowableOfType(
        () -> service.create(createRequest(Set.of(1L, 2L)), OPERATOR), BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getMessage()).isEqualTo("error.role.invalid");
  }

  @Test
  @DisplayName("创建成功：写入用户 + 建立角色绑定，且重复的 roleId 会被去重")
  void createsUserWithDeduplicatedRoleBindings() {
    when(userMapper.selectCount(any())).thenReturn(0L);
    when(roleMapper.selectByIds(Set.of(1L, 2L)))
        .thenReturn(List.of(role(1L, "OPS", 1, "运营"), role(2L, "FIN", 1, "财务")));
    when(roleService.isSuper(OPERATOR)).thenReturn(true);
    when(passwordEncoder.encode("secret")).thenReturn("hashed");
    // 插入后 MyBatis-Plus 会回填自增主键，这里模拟之
    when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
      SysUser inserted = invocation.getArgument(0);
      inserted.setId(100L);
      return 1;
    });

    service.create(createRequest(List.of(1L, 1L, 2L)), OPERATOR);

    ArgumentCaptor<SysUser> user = ArgumentCaptor.forClass(SysUser.class);
    verify(userMapper).insert(user.capture());
    assertThat(user.getValue().getUsername()).isEqualTo("bob");
    assertThat(user.getValue().getPasswordHash()).isEqualTo("hashed");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<SysUserRole>> bindings = ArgumentCaptor.forClass(List.class);
    verify(userRoleMapper).insertBatch(bindings.capture());
    // 1L 重复两次必须只写一条，否则撞 uk_user_role 唯一键
    assertThat(bindings.getValue()).hasSize(2);
    assertThat(bindings.getValue()).allMatch(binding -> binding.getUserId().equals(100L));
  }

  // ---------------- 状态切换与踢下线 ----------------

  @Test
  @DisplayName("禁用账号：必须递增令牌版本，否则重新启用后旧令牌会复活")
  void disablingUserBumpsTokenVersion() {
    SysUser target = user(TARGET, "bob", 1, 0);
    when(userMapper.selectById(TARGET)).thenReturn(target);
    when(roleService.isAdmin(TARGET)).thenReturn(false);

    service.updateStatus(TARGET, 0, OPERATOR);

    verify(userMapper).bumpTokenVersion(TARGET);
  }

  @Test
  @DisplayName("启用账号：不需要递增令牌版本（禁用时已经作废过）")
  void enablingUserDoesNotBumpTokenVersion() {
    SysUser target = user(TARGET, "bob", 0, 0);
    when(userMapper.selectById(TARGET)).thenReturn(target);
    when(roleService.isAdmin(TARGET)).thenReturn(false);

    service.updateStatus(TARGET, 1, OPERATOR);

    verify(userMapper, never()).bumpTokenVersion(any());
  }

  @Test
  @DisplayName("重置密码：必须递增令牌版本，把该用户所有会话踢下线")
  void resetPasswordBumpsTokenVersion() {
    SysUser target = user(TARGET, "bob", 1, 0);
    when(userMapper.selectById(TARGET)).thenReturn(target);
    when(roleService.isAdmin(TARGET)).thenReturn(false);
    when(passwordEncoder.encode("new-secret")).thenReturn("new-hash");

    service.resetPassword(TARGET, "new-secret", OPERATOR);

    assertThat(target.getPasswordHash()).isEqualTo("new-hash");
    verify(userMapper).bumpTokenVersion(TARGET);
  }

  // ---------------- 越权保护 ----------------

  @Test
  @DisplayName("超管账号：任何人都不能操作")
  void superUserIsImmutable() {
    when(userMapper.selectById(TARGET)).thenReturn(user(TARGET, "root", 1, 1));

    BizException thrown = catchThrowableOfType(
        () -> service.updateStatus(TARGET, 0, OPERATOR), BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getMessage()).isEqualTo("error.super.immutable");
    verify(userMapper, never()).updateById(any(SysUser.class));
  }

  @Test
  @DisplayName("管理员账号：非超管不可操作（管理员之间互不可操作）")
  void adminUserIsProtectedFromNonSuper() {
    when(userMapper.selectById(TARGET)).thenReturn(user(TARGET, "admin2", 1, 0));
    when(roleService.isAdmin(TARGET)).thenReturn(true);
    when(roleService.isSuper(OPERATOR)).thenReturn(false);

    BizException thrown = catchThrowableOfType(
        () -> service.updateStatus(TARGET, 0, OPERATOR), BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getMessage()).isEqualTo("error.admin.protected");
    verify(userMapper, never()).updateById(any(SysUser.class));
  }

  @Test
  @DisplayName("超管操作管理员账号：允许")
  void superUserCanOperateAdmin() {
    SysUser target = user(TARGET, "admin2", 1, 0);
    when(userMapper.selectById(TARGET)).thenReturn(target);
    when(roleService.isAdmin(TARGET)).thenReturn(true);
    when(roleService.isSuper(OPERATOR)).thenReturn(true);

    service.updateStatus(TARGET, 0, OPERATOR);

    verify(userMapper).bumpTokenVersion(TARGET);
  }

  @Test
  @DisplayName("删除自己：拒绝（避免管理员把自己删掉后无人可管理）")
  void rejectsDeletingSelf() {
    when(userMapper.selectById(OPERATOR)).thenReturn(user(OPERATOR, "self", 1, 0));

    BizException thrown = catchThrowableOfType(
        () -> service.delete(OPERATOR, OPERATOR), BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getMessage()).isEqualTo("error.user.delete.self");
    verifyNoInteractions(userRoleMapper);
  }

  @Test
  @DisplayName("删除他人：先清理角色绑定再删用户（避免留下孤儿绑定）")
  void deleteRemovesBindingsBeforeUser() {
    when(userMapper.selectById(TARGET)).thenReturn(user(TARGET, "bob", 1, 0));
    when(roleService.isAdmin(TARGET)).thenReturn(false);

    service.delete(TARGET, OPERATOR);

    verify(userRoleMapper).delete(any());
    verify(userMapper).deleteById(TARGET);
  }

  // ---------------- 可分配角色 ----------------

  @Test
  @DisplayName("非超管的可分配角色列表不含内置 ADMIN 角色（界面层不给提权入口）")
  void assignableRolesHidesAdminFromNonSuper() {
    when(roleService.isSuper(OPERATOR)).thenReturn(false);
    when(roleMapper.selectList(any()))
        .thenReturn(List.of(role(1L, "OPS", 1, "运营"), role(2L, RoleService.ADMIN_CODE, 1, "管理员")));

    List<RoleItem> roles = service.assignableRoles(OPERATOR);

    assertThat(roles).extracting(RoleItem::getCode).containsExactly("OPS");
  }

  @Test
  @DisplayName("超管的可分配角色列表包含 ADMIN，并排在最前")
  void assignableRolesIncludesAdminForSuper() {
    when(roleService.isSuper(OPERATOR)).thenReturn(true);
    when(roleMapper.selectList(any()))
        .thenReturn(List.of(role(1L, "OPS", 1, "运营"), role(2L, RoleService.ADMIN_CODE, 1, "管理员")));

    List<RoleItem> roles = service.assignableRoles(OPERATOR);

    assertThat(roles).extracting(RoleItem::getCode).containsExactly(RoleService.ADMIN_CODE, "OPS");
  }

  // ---------------- 夹具 ----------------

  private UserCreateRequest createRequest(Set<Long> roleIds) {
    UserCreateRequest request = new UserCreateRequest();
    request.setUsername("bob");
    request.setPassword("secret");
    request.setRoleIds(List.copyOf(roleIds));
    return request;
  }

  private UserCreateRequest createRequest(List<Long> roleIds) {
    UserCreateRequest request = new UserCreateRequest();
    request.setUsername("bob");
    request.setPassword("secret");
    request.setRoleIds(roleIds);
    return request;
  }

  private SysUser user(Long id, String username, Integer status, Integer isSuper) {
    SysUser user = new SysUser();
    user.setId(id);
    user.setUsername(username);
    user.setStatus(status);
    user.setIsSuper(isSuper);
    return user;
  }

  private SysRole role(Long id, String code, Integer status, String name) {
    SysRole role = new SysRole();
    role.setId(id);
    role.setCode(code);
    role.setName(name);
    role.setStatus(status);
    return role;
  }
}
