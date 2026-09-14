package com.zmy.yorvix.common.page;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 通用分页结果。
 */
@Getter
public class PageResult<T> {

  private final List<T> list;
  private final long total;
  private final int page;
  private final int pageSize;
  private final int totalPages;

  public PageResult(List<T> list, long total, int page, int pageSize, int totalPages) {
    this.list = list;
    this.total = total;
    this.page = page;
    this.pageSize = pageSize;
    this.totalPages = totalPages;
  }

  public static <S, T> PageResult<T> of(Page<S> page, Function<S, T> mapper) {
    List<T> list = page.getContent().stream().map(mapper).toList();
    return new PageResult<>(list, page.getTotalElements(), page.getNumber() + 1,
        page.getSize(), page.getTotalPages());
  }
}
