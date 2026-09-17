package com.zmy.yorvix.common.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Getter;

import java.util.List;
import java.util.function.Function;

/**
 * 通用分页结果（对接 MyBatis-Plus IPage）。
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

  public static <S, T> PageResult<T> of(IPage<S> page, Function<S, T> mapper) {
    List<T> list = page.getRecords().stream().map(mapper).toList();
    return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(),
        (int) page.getSize(), (int) page.getPages());
  }
}
