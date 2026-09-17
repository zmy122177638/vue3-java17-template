package com.zmy.yorvix.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置。
 * <p>目的不是"有个文档页面"，而是把两件约定变成可交互的事实：
 * <ol>
 *   <li>统一返回体 {@code Result{code,message,data,timestamp}} 与各接口的真实出入参
 *       （前端不需要再靠读 Java 代码猜契约）；</li>
 *   <li>认证方式可调试：这里注册 Bearer 方案后，Swagger UI 上会出现 Authorize 按钮，
 *       填入 access token 即可直接调受保护接口。</li>
 * </ol>
 * <p><b>访问控制提醒</b>：{@code /swagger-ui.html} 与 {@code /v3/api-docs} 不在
 * {@code /api/**} 下，因此**不经过 AuthInterceptor**，等于匿名可读的接口清单。
 * 生产 profile 已整体关闭（见 {@code application-prod.yaml}）；需要长期开放时
 * 应改为把它们纳入鉴权范围，而不是只靠 profile。
 */
@Configuration
public class OpenApiConfig {

  /** 与前端请求头一致：Authorization: Bearer &lt;opaque token&gt; */
  private static final String SECURITY_SCHEME_NAME = "bearerAuth";

  @Bean
  public OpenAPI yorvixOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("Yorvix API")
            .version("v1")
            .description("""
                全栈模板后端接口。

                约定：
                · 成功码是 0（不是 200），响应体统一为 Result{code,message,data,timestamp}；
                · 错误消息已按 Accept-Language 本地化，前端直接展示 message 即可；
                · 认证为 Opaque Token + Redis：请求头 Authorization: Bearer <accessToken>，
                  令牌过期时前端调 POST /api/auth/refresh 用 HttpOnly Cookie 中的 refreshToken 续期。"""))
        .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
            new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .bearerFormat("Opaque Token")))
        // 默认全局要求认证；白名单接口（login/refresh/logout）由各自方法上的注解或
        // 调用者手动去掉该要求，避免"文档默认匿名"的误导
        .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
  }
}
