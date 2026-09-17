package com.zmy.yorvix.model;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.zmy.yorvix.model.system.SysMenu;
import com.zmy.yorvix.model.system.SysRole;
import com.zmy.yorvix.model.sys.SysUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实体字段的 MyBatis-Plus 更新策略。
 *
 * <p><b>为什么需要这个测试</b>："清空后保存不生效"是典型的**静默失败**——
 * MyBatis-Plus 默认策略是 {@code NOT_NULL}，会把值为 null 的字段从 UPDATE 语句里剔除，
 * 于是前端点输入框的「×」清空、再保存，接口照样返回成功，但库里还是旧值。
 * 没有任何异常、没有任何日志，只能靠用户反复操作后反馈"改不掉"。
 *
 * <p>这个 bug 在本项目真实发生过（菜单的徽标清不掉，图标/组件路径/权限码等同样受影响），
 * 因此这里用反射把规则钉住：下面 {@link #CLEARABLE_FIELDS} 里的字段必须标
 * {@code updateStrategy = ALWAYS}。
 *
 * <p><b>新增实体字段时按这里自检</b>：只要"前端能把值置空传上来"，就属于可清空字段，
 * 需要加注解并登记到 {@link #CLEARABLE_FIELDS}。反过来，必填字段（有 {@code @NotBlank} /
 * {@code @NotNull}）不需要——它永远不会是 null；由数据库维护或只允许原子递增的字段则用
 * {@code NEVER}（见 {@link #DB_MAINTAINED_FIELDS}）。
 *
 * <p>测试不依赖 Spring 上下文与数据库，纯反射。
 */
class EntityUpdateStrategyTest {

  /**
   * 前端**可以清空**的字段：必须 {@code ALWAYS}。
   * <p>这份清单同时是"哪些字段允许被清空"的说明。
   */
  private static final Map<Class<?>, Set<String>> CLEARABLE_FIELDS = new LinkedHashMap<>();

  /**
   * 不允许通过 {@code updateById} 写入的字段：必须 {@code NEVER}。
   * <p>由数据库默认值维护，或只允许通过专用方法原子更新。
   */
  private static final Map<Class<?>, Set<String>> DB_MAINTAINED_FIELDS = new LinkedHashMap<>();

  static {
    CLEARABLE_FIELDS.put(SysUser.class, Set.of("nickname", "email", "phone"));
    CLEARABLE_FIELDS.put(SysRole.class, Set.of("remark"));
    CLEARABLE_FIELDS.put(SysMenu.class, Set.of(
        // 切换菜单类型时这些值会变成"不适用"，需要在编辑时清掉
        "path", "component", "authCode", "linkSrc",
        // 可选的展示类字段
        "icon", "activeIcon", "activePath",
        "badgeType", "badge", "badgeVariants"));

    // 令牌版本靠 SysUserMapper#bumpTokenVersion 原子递增：
    // 允许 updateById 把内存里的旧值写回去，会让"本该失效的会话复活"
    DB_MAINTAINED_FIELDS.put(SysUser.class, Set.of("tokenVersion"));
  }

  @Test
  @DisplayName("可清空字段标了 updateStrategy = ALWAYS（否则清空会被静默忽略）")
  void clearableFieldsUseAlwaysStrategy() {
    CLEARABLE_FIELDS.forEach((entity, names) -> names.forEach(name -> {
      Field field = declaredField(entity, name);
      TableField annotation = field.getAnnotation(TableField.class);

      assertThat(annotation)
          .as("%s.%s 缺少 @TableField。前端清空该字段后保存会被 MyBatis-Plus 默认的 "
              + "NOT_NULL 策略静默丢弃——请加 @TableField(updateStrategy = FieldStrategy.ALWAYS)",
              entity.getSimpleName(), name)
          .isNotNull();
      assertThat(annotation.updateStrategy())
          .as("%s.%s 的更新策略应为 ALWAYS，实际为 %s",
              entity.getSimpleName(), name, annotation.updateStrategy())
          .isEqualTo(FieldStrategy.ALWAYS);
    }));
  }

  @Test
  @DisplayName("数据库维护 / 只允许原子递增的字段标了 updateStrategy = NEVER")
  void dbMaintainedFieldsUseNeverStrategy() {
    DB_MAINTAINED_FIELDS.forEach((entity, names) -> names.forEach(name -> {
      Field field = declaredField(entity, name);
      TableField annotation = field.getAnnotation(TableField.class);

      assertThat(annotation)
          .as("%s.%s 缺少 @TableField(updateStrategy = FieldStrategy.NEVER)",
              entity.getSimpleName(), name)
          .isNotNull();
      assertThat(annotation.updateStrategy())
          .as("%s.%s 的更新策略应为 NEVER，实际为 %s",
              entity.getSimpleName(), name, annotation.updateStrategy())
          .isEqualTo(FieldStrategy.NEVER);
    }));
  }

  @Test
  @DisplayName("清单与被测实体没有拼写错位（字段不存在会直接报错，而不是静默通过）")
  void everyListedFieldExists() {
    assertThat(CLEARABLE_FIELDS).isNotEmpty();
    assertThat(DB_MAINTAINED_FIELDS).isNotEmpty();
    // declaredField 在字段不存在时会抛 NoSuchFieldException，测试即失败
    CLEARABLE_FIELDS.forEach((entity, names) ->
        names.forEach(name -> assertThat(declaredField(entity, name)).isNotNull()));
  }

  private static Field declaredField(Class<?> entity, String name) {
    try {
      return entity.getDeclaredField(name);
    } catch (NoSuchFieldException e) {
      throw new AssertionError(
          entity.getSimpleName() + " 已不存在字段 " + name + "，请同步更新本测试的字段清单", e);
    }
  }
}
