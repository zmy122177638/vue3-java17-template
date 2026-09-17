package com.zmy.yorvix.request.system;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 同级拖拽排序请求：按 {@code ids} 的顺序重排 {@code pid} 下兄弟节点的 sort。
 * <p>只支持同级排序，ids 必须覆盖该父级下的全部子节点，避免漏传/越权导致顺序错乱。
 */
@Getter
@Setter
public class MenuSortRequest {

  /** 父级 ID，0 为根 */
  private Long pid;

  /** 同一父级下菜单 ID 的新顺序 */
  @NotEmpty
  private List<Long> ids;
}
