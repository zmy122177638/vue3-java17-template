package com.zmy.yorvix.mapper.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zmy.yorvix.model.system.SysRoleMenu;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色菜单授权数据访问接口。
 */
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

  /**
   * 批量写入授权。全量替换授权时菜单数量可达几十条，
   * 逐条 insert 会产生同等数量的数据库往返，这里合并为一条多值 INSERT。
   */
  @Insert("<script>"
      + "INSERT INTO sys_role_menu (role_id, menu_id) VALUES "
      + "<foreach collection='list' item='item' separator=','>"
      + "(#{item.roleId}, #{item.menuId})"
      + "</foreach>"
      + "</script>")
  int insertBatch(@Param("list") List<SysRoleMenu> list);
}
