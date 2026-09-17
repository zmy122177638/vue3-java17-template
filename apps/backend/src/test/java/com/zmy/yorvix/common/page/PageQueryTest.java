package com.zmy.yorvix.common.page;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PageQuery} 的分页钳制与排序白名单。
 * <p>这两段逻辑都属于"静默失效"：钳制失效会让 {@code ?size=1000000} 直接全表拉取，
 * 白名单失效会让任意列名拼进 ORDER BY——两者都不会报错，只会让行为悄悄不对，
 * 因此需要用测试锁住。
 */
class PageQueryTest {

  @Test
  @DisplayName("Controller 走无参构造 + setter 绑定时，分页参数同样被钳制")
  void clampsPageAndSizeSetViaSetters() {
    PageQuery query = new PageQuery();
    query.setPage(0);
    query.setSize(1_000_000);

    Page<Object> page = query.toMpPage();

    assertThat(page.getCurrent()).isEqualTo(1);
    assertThat(page.getSize()).isEqualTo(PageQuery.MAX_SIZE);
  }

  @Test
  @DisplayName("非法的 page/size 被钳制到合法范围")
  void clampsIllegalPageAndSize() {
    PageQuery negative = new PageQuery();
    negative.setPage(-5);
    negative.setSize(-1);
    assertThat(negative.toMpPage().getCurrent()).isEqualTo(1);
    assertThat(negative.toMpPage().getSize()).isEqualTo(1);

    PageQuery zeroSize = new PageQuery();
    zeroSize.setSize(0);
    assertThat(zeroSize.toMpPage().getSize()).isEqualTo(1);
  }

  @Test
  @DisplayName("未指定排序时回退默认 id 倒序")
  void fallsBackToDefaultOrder() {
    assertThat(orderKeys(new PageQuery().toMpPage())).containsExactly("id desc");
  }

  @Test
  @DisplayName("排序字段命中白名单时生效，驼峰字段名转成下划线列名")
  void appliesWhitelistedSort() {
    PageQuery query = new PageQuery();
    query.setSort("createdAt");
    query.setOrder("asc");

    Page<Object> page = query.toMpPage(List.of("createdAt", "username"));

    assertThat(orderKeys(page)).containsExactly("created_at asc");
  }

  @Test
  @DisplayName("排序字段不在白名单时被忽略——这是防注入的安全边界")
  void ignoresSortOutsideWhitelist() {
    PageQuery query = new PageQuery();
    query.setSort("password_hash");
    query.setOrder("asc");

    Page<Object> page = query.toMpPage(List.of("username"));

    assertThat(orderKeys(page)).containsExactly("id desc");
  }

  @Test
  @DisplayName("排序方向只认 asc，其余一律按 desc 处理")
  void treatsNonAscOrderAsDesc() {
    PageQuery query = new PageQuery();
    query.setSort("username");
    query.setOrder("; DROP TABLE sys_user");

    Page<Object> page = query.toMpPage(List.of("username"));

    assertThat(orderKeys(page)).containsExactly("username desc");
  }

  @Test
  @DisplayName("白名单为空时忽略前端排序（用于不需要排序的列表）")
  void ignoresSortWhenWhitelistEmpty() {
    PageQuery query = new PageQuery();
    query.setSort("username");
    query.setOrder("asc");

    assertThat(orderKeys(query.toMpPage())).containsExactly("id desc");
  }

  /** 把 ORDER BY 渲染成可读字符串便于断言（OrderItem 未实现 equals，不能直接比对象） */
  private static List<String> orderKeys(Page<?> page) {
    return page.orders().stream()
        .map(item -> item.getColumn() + (item.isAsc() ? " asc" : " desc"))
        .toList();
  }
}
