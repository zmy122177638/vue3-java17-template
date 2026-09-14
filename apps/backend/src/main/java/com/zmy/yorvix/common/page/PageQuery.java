package com.zmy.yorvix.common.page;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * 通用分页查询参数：统一默认值（page=1, size=10）与上限（100）。
 * <p>controller 直接以分页参数构造，内部钳制非法值，避免各处重复防御。
 */
@Getter
@Setter
public class PageQuery {

  /** 单页最大行数 */
  public static final int MAX_SIZE = 100;

  private int page = 1;
  private int size = 10;

  public PageQuery() {
  }

  public PageQuery(int page, int size) {
    this.page = Math.max(page, 1);
    this.size = Math.min(Math.max(size, 1), MAX_SIZE);
  }

  /** 转换为 Spring Data 分页对象（id 倒序） */
  public PageRequest toPageRequest() {
    return toPageRequest(Sort.by(Sort.Direction.DESC, "id"));
  }

  public PageRequest toPageRequest(Sort sort) {
    return PageRequest.of(page - 1, size, sort);
  }
}
