package com.zmy.yorvix.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 免鉴权路径匹配。
 * <p>这是"错了也不报错、只是行为悄悄不对"的典型：多放行一个路径不会抛异常、日志也正常，
 * 只是那个接口不再校验令牌。因此这里重点锁住**不该命中却命中的情况**。
 */
class WhiteListTest {

  @Nested
  @DisplayName("精确匹配（默认语义）")
  class ExactMatch {

    @Test
    @DisplayName("命中配置的路径本身")
    void matchesExactPath() {
      assertThat(WhiteList.covers("/api/auth/login", "/api/auth/login")).isTrue();
      assertThat(WhiteList.covers("/error", "/error")).isTrue();
    }

    @Test
    @DisplayName("不匹配同前缀的其它路径——这正是弃用 startsWith 的原因")
    void doesNotMatchLongerPathsWithSamePrefix() {
      // 以下三个如果被放行，就等于"新增接口自动变成公开接口"，且没有任何提示
      assertThat(WhiteList.covers("/api/auth/login", "/api/auth/login-audit")).isFalse();
      assertThat(WhiteList.covers("/api/auth/login", "/api/auth/login/extra")).isFalse();
      assertThat(WhiteList.covers("/error", "/errors")).isFalse();
    }

    @Test
    @DisplayName("不匹配子路径（要覆盖子树必须显式写 /**）")
    void doesNotMatchSubPaths() {
      assertThat(WhiteList.covers("/api/auth", "/api/auth/login")).isFalse();
      assertThat(WhiteList.covers("/api", "/api/orders")).isFalse();
    }
  }

  @Nested
  @DisplayName("子树通配 /**")
  class SubtreeMatch {

    @Test
    @DisplayName("覆盖子树根与其所有后代")
    void matchesSubtreeRootAndDescendants() {
      assertThat(WhiteList.covers("/api/public/**", "/api/public")).isTrue();
      assertThat(WhiteList.covers("/api/public/**", "/api/public/a")).isTrue();
      assertThat(WhiteList.covers("/api/public/**", "/api/public/a/b/c")).isTrue();
    }

    @Test
    @DisplayName("不越界到同前缀的兄弟路径")
    void doesNotLeakToSiblingPaths() {
      assertThat(WhiteList.covers("/api/public/**", "/api/publicity")).isFalse();
      assertThat(WhiteList.covers("/api/public/**", "/api/private")).isFalse();
    }

    @Test
    @DisplayName("/** 表示全部放行（配置里一眼可见，不会误配）")
    void bareDoubleStarMatchesEverything() {
      assertThat(WhiteList.covers("/**", "/api/orders")).isTrue();
      assertThat(WhiteList.covers("/**", "/")).isTrue();
    }
  }

  @Nested
  @DisplayName("边界输入")
  class EdgeCases {

    @Test
    @DisplayName("配置项或路径为空一律不命中（fail-closed）")
    void blankInputsNeverMatch() {
      assertThat(WhiteList.covers(null, "/api/auth/login")).isFalse();
      assertThat(WhiteList.covers("", "/api/auth/login")).isFalse();
      assertThat(WhiteList.covers("   ", "/api/auth/login")).isFalse();
      assertThat(WhiteList.covers("/api/auth/login", null)).isFalse();
      assertThat(WhiteList.covers("/api/auth/login", "")).isFalse();
    }

    @Test
    @DisplayName("空列表视为不命中：白名单漏配时必须走鉴权，而不是全部放行")
    void anyCoversIsFailClosed() {
      assertThat(WhiteList.anyCovers(List.of(), "/api/auth/login")).isFalse();
      assertThat(WhiteList.anyCovers(null, "/api/auth/login")).isFalse();
    }

    @Test
    @DisplayName("任一命中即返回 true")
    void anyCoversMatchesAnyEntry() {
      List<String> whiteList = List.of("/api/auth/login", "/api/auth/refresh", "/error");
      assertThat(WhiteList.anyCovers(whiteList, "/api/auth/refresh")).isTrue();
      assertThat(WhiteList.anyCovers(whiteList, "/error")).isTrue();
      assertThat(WhiteList.anyCovers(whiteList, "/api/orders")).isFalse();
    }

    @Test
    @DisplayName("列表中的空项被跳过，不影响其它项判定")
    void skipsBlankEntries() {
      List<String> whiteList = java.util.Arrays.asList(null, "", "  ", "/api/auth/login");
      assertThat(WhiteList.anyCovers(whiteList, "/api/auth/login")).isTrue();
    }
  }
}
