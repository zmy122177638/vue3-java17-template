package com.zmy.yorvix.security;

import com.zmy.yorvix.security.OperationLogInterceptor.AuditedTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审计路径的操作对象解析（「客体标识」）。
 * <p>这是"错了也不报错"的典型：解析结果只写进日志表，少一个 id、或把动作名当成 id，
 * 都不会抛异常、也不影响接口返回，但它决定了"谁改了哪条数据"能不能被回答——
 * 而这正是审计最需要回答的问题。因此这里把行为逐条锁住。
 */
class OperationLogInterceptorTest {

  @Nested
  @DisplayName("命中审计前缀：解析对象类型与主键")
  class ResolveTarget {

    @Test
    @DisplayName("路径中的纯数字段作为对象主键")
    void resolvesTargetIdFromNumericSegment() {
      assertThat(OperationLogInterceptor.resolveTarget("/api/system/users/3/status"))
          .isEqualTo(new AuditedTarget("users", 3L));
      assertThat(OperationLogInterceptor.resolveTarget("/api/system/roles/5/menus"))
          .isEqualTo(new AuditedTarget("roles", 5L));
      assertThat(OperationLogInterceptor.resolveTarget("/api/system/menus/12"))
          .isEqualTo(new AuditedTarget("menus", 12L));
    }

    @Test
    @DisplayName("无 id 的写操作不应被编造出对象：新建、动作名、尾部斜杠")
    void leavesTargetIdNullWhenAbsent() {
      // POST 新建时对象还不存在（id 由数据库生成）
      assertThat(OperationLogInterceptor.resolveTarget("/api/system/users"))
          .isEqualTo(new AuditedTarget("users", null));
      // sort 是动作名而不是 id：把它当成 id 会写出永远查不到实体的脏数据
      assertThat(OperationLogInterceptor.resolveTarget("/api/system/menus/sort"))
          .isEqualTo(new AuditedTarget("menus", null));
      // 尾部斜杠不影响解析
      assertThat(OperationLogInterceptor.resolveTarget("/api/system/users/3/"))
          .isEqualTo(new AuditedTarget("users", 3L));
    }

    @Test
    @DisplayName("非数字与超出 Long 范围的段一律按无对象处理，绝不抛异常")
    void toleratesUnparsableSegment() {
      assertThat(OperationLogInterceptor.resolveTarget("/api/system/users/abc/status"))
          .isEqualTo(new AuditedTarget("users", null));
      // 超出 Long.MAX_VALUE：parseLong 会抛异常，必须被吞掉而不是让整条审计记录丢失
      assertThat(
          OperationLogInterceptor.resolveTarget("/api/system/users/99999999999999999999/status"))
          .isEqualTo(new AuditedTarget("users", null));
      assertThat(OperationLogInterceptor.resolveTarget("/api/system/users/3.5/status"))
          .isEqualTo(new AuditedTarget("users", null));
    }

    @Test
    @DisplayName("module 截断到与 sys_oper_log 列宽一致（超长会插入失败，整条记录被吞掉）")
    void truncatesModuleToColumnWidth() {
      String longSegment = "a".repeat(100);
      AuditedTarget target =
          OperationLogInterceptor.resolveTarget("/api/system/" + longSegment + "/1");

      assertThat(target.module()).hasSize(64);
      assertThat(target.targetId()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("未命中审计前缀或路径不含资源名")
  class NotAudited {

    @Test
    @DisplayName("业务域路径与不完整的系统管理路径均返回空值")
    void returnsNoneForNonAuditedPaths() {
      // 业务域不在 AUDITED_PREFIXES 内
      assertThat(OperationLogInterceptor.resolveTarget("/api/user/profile"))
          .isEqualTo(AuditedTarget.NONE);
      // 前缀是 /api/system/，少了斜杠不算命中
      assertThat(OperationLogInterceptor.resolveTarget("/api/system"))
          .isEqualTo(AuditedTarget.NONE);
      // 只有前缀、没有资源名
      assertThat(OperationLogInterceptor.resolveTarget("/api/system/"))
          .isEqualTo(AuditedTarget.NONE);
    }

    @Test
    @DisplayName("空路径不抛异常")
    void handlesBlankUri() {
      assertThat(OperationLogInterceptor.resolveTarget(null)).isEqualTo(AuditedTarget.NONE);
      assertThat(OperationLogInterceptor.resolveTarget("")).isEqualTo(AuditedTarget.NONE);
      assertThat(OperationLogInterceptor.resolveTarget("   ")).isEqualTo(AuditedTarget.NONE);
    }
  }
}
