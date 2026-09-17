package com.zmy.yorvix.common.page;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.List;

/**
 * 通用分页查询参数：统一默认值（page=1, size=10）与上限（100），并支持前端列头排序。
 * <p>转换目标为 MyBatis-Plus 分页对象；controller 直接以分页参数构造，
 * 内部钳制非法值，避免各处重复防御。
 */
@Getter
@Setter
public class PageQuery {

  /** 单页最大行数 */
  public static final int MAX_SIZE = 100;

  private int page = 1;
  private int size = 10;

  /**
   * 排序字段（前端字段名，如 {@code createdAt}）。
   * <p>对应 vxe 的 remote sort 参数；<b>必须经白名单校验后才会生效</b>。
   */
  private String sort;

  /** 排序方向：{@code asc} / {@code desc}，其它值一律按 desc 处理 */
  private String order;

  public PageQuery() {
  }

  public PageQuery(int page, int size) {
    this.page = Math.max(page, 1);
    this.size = Math.min(Math.max(size, 1), MAX_SIZE);
  }

  /** 转换为 MyBatis-Plus 分页对象（忽略前端排序，默认 id 倒序） */
  public <T> Page<T> toMpPage() {
    return toMpPage(List.of(), OrderItem.desc("id"));
  }

  /**
   * 转换为 MyBatis-Plus 分页对象，支持前端排序。
   * <p>钳制在这里收口：Controller 以 {@code PageQuery} 作为请求参数时，Spring 走的是
   * 无参构造 + setter 绑定，{@link #PageQuery(int, int)} 中的钳制不会执行，
   * 若不在此处兜底，{@code ?size=1000000} 会被原样传给 MyBatis-Plus 造成全表拉取。
   * <p>排序解析：{@link #sort} 命中 {@code allowedSortFields} 才按前端要求排序，
   * 否则回退 {@code defaultOrders}。白名单是必需的安全边界——排序列会拼进 ORDER BY
   * 语句，不能信任前端输入（未加白名单可被用于 SQL 注入或探测表结构）。
   *
   * @param allowedSortFields 允许排序的字段白名单（前端字段名，驼峰；内部会转成下划线列名）
   * @param defaultOrders     未指定排序或排序字段不合法时的默认排序
   */
  public <T> Page<T> toMpPage(Collection<String> allowedSortFields, OrderItem... defaultOrders) {
    int safePage = Math.max(page, 1);
    int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
    Page<T> p = new Page<>(safePage, safeSize);
    OrderItem requested = resolveOrderItem(allowedSortFields);
    if (requested != null) {
      p.addOrder(requested);
    } else if (defaultOrders.length > 0) {
      p.addOrder(defaultOrders);
    } else {
      // 兜底：没有默认排序时也必须给出确定的顺序。MySQL 对无 ORDER BY 的查询不保证行序，
      // 翻页时同一行可能重复出现或被跳过
      p.addOrder(OrderItem.desc("id"));
    }
    return p;
  }

  /** 解析前端排序请求；字段为空或不在白名单内返回 null（表示沿用默认排序） */
  private OrderItem resolveOrderItem(Collection<String> allowedSortFields) {
    if (sort == null || sort.isBlank() || allowedSortFields == null
        || !allowedSortFields.contains(sort)) {
      return null;
    }
    // 走白名单后再转列名，最终拼进 ORDER BY 的只可能是 [a-z_] 组成的受控标识符
    String column = StringUtils.camelToUnderline(sort);
    return "asc".equalsIgnoreCase(order) ? OrderItem.asc(column) : OrderItem.desc(column);
  }
}
