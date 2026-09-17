package com.zmy.yorvix.mapper.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zmy.yorvix.model.system.SysUserRole;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户角色关联数据访问接口。
 */
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

  /** 批量写入绑定（全量替换角色时合并为一条多值 INSERT） */
  @Insert("<script>"
      + "INSERT INTO sys_user_role (user_id, role_id) VALUES "
      + "<foreach collection='list' item='item' separator=','>"
      + "(#{item.userId}, #{item.roleId})"
      + "</foreach>"
      + "</script>")
  int insertBatch(@Param("list") List<SysUserRole> list);
}
