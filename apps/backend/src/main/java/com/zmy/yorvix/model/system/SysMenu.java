package com.zmy.yorvix.model.system;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 菜单（目录 CATALOG / 页面菜单 MENU / 按钮 BUTTON / 内嵌 EMBEDDED / 外链 LINK，pid 树形自关联）。
 * <p>title 为多语言 JSON：{"zh-CN":"系统管理","en-US":"System"}，
 * 下发时由 MenuService 按 Accept-Language 解析为纯文本。
 * <p>link_src：EMBEDDED 型下发为 meta.iframeSrc，LINK 型下发为 meta.link。
 * <p><b>可选字段一律标 {@code updateStrategy = ALWAYS}</b>：MyBatis-Plus 默认策略是
 * {@code NOT_NULL}，会把值为 null 的字段从 UPDATE 语句里**剔除**，于是"清空图标/徽标"
 * 变成"不修改"——接口返回成功、库里还是旧值，属于典型的静默失败。
 */
@Getter
@Setter
@TableName("sys_menu")
public class SysMenu {

  public static final String TYPE_CATALOG = "CATALOG";
  public static final String TYPE_MENU = "MENU";
  public static final String TYPE_BUTTON = "BUTTON";
  public static final String TYPE_EMBEDDED = "EMBEDDED";
  public static final String TYPE_LINK = "LINK";

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 父级 ID，0 为根 */
  private Long pid;

  /** 类型：CATALOG / MENU / BUTTON */
  private String type;

  /** 路由名（BUTTON 型仅作标识） */
  private String name;

  // ---------------- 可清空字段（updateStrategy = ALWAYS） ----------------
  // MenuServiceImpl.applyUpsert 会把这批字段**原样**从请求写入实体，值为 null 就表示
  // "清空"（用户点了输入框的 ×）。默认的 NOT_NULL 策略会把 null 字段从 UPDATE SET 中
  // 剔除，导致清空后保存"没反应"。新增可选字段时记得一并加这个注解。

  /** 路由路径（相对父级）；BUTTON / LINK 型没有该值，允许清空 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String path;

  /** 组件路径（MENU 型，如 /system/user/index，对应前端 views 目录）；切换为非 MENU 型时清空 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String component;

  /** 按钮权限码（BUTTON 型，业务域使用，命名形如 资源:动作，如 Report:Export）；切换为非 BUTTON 型时清空 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String authCode;

  /** 显示名（多语言 JSON）。必填（@NotBlank），不会为 null，因此不需要 ALWAYS */
  private String title;

  /** 图标（lucide 图标名） */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String icon;

  /** 激活时显示的图标（lucide 图标名） */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String activeIcon;

  /** 作为路由时，需要激活（高亮）的菜单 path */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String activePath;

  /** 外链/内嵌页地址，http(s) */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String linkSrc;

  /** 徽标类型 dot / normal */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String badgeType;

  /** 徽标内容（badgeType=normal 时生效） */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String badge;

  /** 徽标颜色 default / destructive / primary / success / warning */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String badgeVariants;

  /** 是否在菜单中隐藏 1是 0否 */
  private Integer hideInMenu = 0;

  /** 是否在菜单中隐藏子级 1是 0否 */
  private Integer hideChildrenInMenu = 0;

  /** 是否在面包屑中隐藏 1是 0否 */
  private Integer hideInBreadcrumb = 0;

  /** 是否在标签栏中隐藏 1是 0否 */
  private Integer hideInTab = 0;

  /** 排序，越小越靠前 */
  private Integer sort = 0;

  /** 1 启用，0 禁用 */
  private Integer status = 1;

  /** 是否缓存页面 1是 0否 */
  private Integer keepAlive = 0;

  /** 是否固定标签页 1是 0否 */
  private Integer affixTab = 0;

  /** 创建时间（数据库默认值维护） */
  private LocalDateTime createdAt;

  public boolean isEnabled() {
    return status != null && status == 1;
  }

  public boolean isButton() {
    return TYPE_BUTTON.equals(type);
  }
}
