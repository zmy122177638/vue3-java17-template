package com.zmy.yorvix.mapper.sys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zmy.yorvix.model.sys.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/**
 * 用户表数据访问接口（mapper 层，仅 SQL 映射与简单 CRUD）。
 * <p>继承 {@link BaseMapper} 即获得单表 CRUD；复杂 SQL 用注解或 XML 扩展。
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

  @Select("SELECT * FROM sys_user WHERE username = #{username}")
  Optional<SysUser> findByUsername(String username);

  /**
   * 递增令牌版本，使该用户**已签发的全部令牌**立即失效（改密 / 重置密码 / 禁用时调用）。
   * <p>用 SQL 自增而不是"读出来 +1 再写回"：并发下后者会丢失更新，
   * 导致本该失效的会话仍然有效。
   */
  @Update("UPDATE sys_user SET token_version = token_version + 1 WHERE id = #{id}")
  int bumpTokenVersion(@Param("id") Long id);
}
