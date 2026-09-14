package com.zmy.yorvix.mapper.sys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zmy.yorvix.model.sys.SysUser;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * 用户表数据访问接口（mapper 层，仅 SQL 映射与简单 CRUD）。
 * <p>继承 {@link BaseMapper} 即获得单表 CRUD；复杂 SQL 用注解或 XML 扩展。
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

  @Select("SELECT * FROM sys_user WHERE username = #{username}")
  Optional<SysUser> findByUsername(String username);
}
