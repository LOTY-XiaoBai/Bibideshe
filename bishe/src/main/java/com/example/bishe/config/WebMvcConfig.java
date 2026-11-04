package com.example.bishe.config;

import com.example.bishe.service.FileUploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置: 注册鉴权拦截器, 开放登录/注册接口, 映射本地上传目录。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final FileUploadService fileUploadService;
    private final String[] allowedOrigins;

    public WebMvcConfig(AuthInterceptor authInterceptor, FileUploadService fileUploadService,
                        @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.fileUploadService = fileUploadService;
        this.allowedOrigins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty() && !"*".equals(s)).toArray(String[]::new);
        if (this.allowedOrigins.length == 0) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS 必须配置明确来源，不能使用 *");
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/student/login",
                        "/api/student/register",
                        "/api/teacher/login",
                        "/api/teacher/register",
                        "/api/admin/login",
                        "/api/admin/register",
                        "/api/admin/register/available",
                        "/api/captcha",
                        "/api/captcha/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = fileUploadService.resolveUploadRoot().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location).resourceChain(true)
                .addResolver(new org.springframework.web.servlet.resource.PathResourceResolver() {
                    @Override protected org.springframework.core.io.Resource getResource(String path, org.springframework.core.io.Resource root) throws java.io.IOException {
                        String normalized=org.springframework.util.StringUtils.cleanPath(path.replace('\\','/'));
                        String checked=normalized.toLowerCase(java.util.Locale.ROOT);
                        if(checked.equals("private") || checked.startsWith("private/"))return null;
                        return super.getResource(path,root);
                    }
                });
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
        registry.addMapping("/uploads/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
