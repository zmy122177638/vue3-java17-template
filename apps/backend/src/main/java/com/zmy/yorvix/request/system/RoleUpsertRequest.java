package com.zmy.yorvix.request.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 角色新增/编辑请求。ADMIN 为内置角色：后端禁止删除，code 不可修改。
 */
@Getter
@Setter
public class RoleUpsertRequest {

  @NotBlank
  @Size(max = 64)
  private String code;

  /** 可选：不传时后端回退为角色编码 */
  @Size(max = 64)
  private String name;

  private Integer status;

  @Size(max = 255)
  private String remark;

  /** 授权的菜单/按钮 ID 列表（可空） */
  private List<Long> menuIds;
}
