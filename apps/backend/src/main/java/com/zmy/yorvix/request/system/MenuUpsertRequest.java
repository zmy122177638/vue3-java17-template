package com.zmy.yorvix.request.system;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 菜单新增/编辑请求。type 不允许在编辑时变更（后端校验）。
 */
@Getter
@Setter
public class MenuUpsertRequest {

  /** 父级 ID，0 为根 */
  private Long pid;

  @NotBlank
  // 枚举型约束的提示需要列出合法取值，标准 key 表达不了，因此用自定义 key
  // （文案见 i18n/messages*.properties 的 validation.menu.type.invalid）
  @Pattern(regexp = "CATALOG|MENU|BUTTON|EMBEDDED|LINK",
      message = "{validation.menu.type.invalid}")
  private String type;

  @NotBlank
  @Size(max = 64)
  private String name;

  @Size(max = 128)
  private String path;

  /** MENU 型组件路径，如 /system/user/index */
  @Size(max = 128)
  private String component;

  /** BUTTON 型按钮权限码，业务域使用，命名形如 资源:动作（如 Report:Export） */
  @Size(max = 64)
  private String authCode;

  /** 多语言 JSON：{"zh-CN":"系统管理","en-US":"System"} */
  @NotBlank
  @Size(max = 255)
  private String title;

  @Size(max = 64)
  private String icon;

  @Size(max = 64)
  private String activeIcon;

  @Size(max = 128)
  private String activePath;

  /** EMBEDDED 内嵌页地址 / LINK 外链地址，http(s) 开头 */
  @Size(max = 255)
  private String linkSrc;

  /** 徽标类型 dot / normal */
  @Size(max = 16)
  private String badgeType;

  @Size(max = 64)
  private String badge;

  @Size(max = 32)
  private String badgeVariants;

  private Boolean hideInMenu;

  private Boolean hideChildrenInMenu;

  private Boolean hideInBreadcrumb;

  private Boolean hideInTab;

  private Integer sort;

  @Min(0)
  @Max(1)
  private Integer status;

  private Boolean keepAlive;

  private Boolean affixTab;
}
