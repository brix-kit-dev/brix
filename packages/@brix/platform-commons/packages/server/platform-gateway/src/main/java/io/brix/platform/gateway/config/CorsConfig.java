package io.brix.platform.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import jakarta.annotation.PostConstruct;

/**
 * 跨域配置。
 *
 * <p>配置全局 CORS（Cross-Origin Resource Sharing）策略，
 * 允许前端应用跨域访问网关接口。
 * 所有配置项均可通过 application.yml 外部化管理。
 *
 * @author Brix Platform Authors
 * @version 1.0.1
 * @see CorsProperties CORS 配置属性类
 * @see CorsWebFilter Spring WebFlux 响应式跨域过滤器
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    /**
     * 安全警告消息模板
     */
    private static final String SECURITY_WARNING = """

        ╔══════════════════════════════════════════════════════════╗
        ║                  ⚠️  CORS 安全警告  ⚠️                   ║
        ╠══════════════════════════════════════════════════════════╣
        ║ 当前 CORS 配置允许所有来源访问 (allowed-origin-patterns: *)  ║
        ║ 此配置仅适用于开发环境，生产环境存在严重安全风险！               ║
        ╠══════════════════════════════════════════════════════════╣
        ║ 生产环境请在 application.yml 中配置具体的域名白名单：          ║
        ║                                                          ║
        ║   gateway:                                                ║
        ║     cors:                                                 ║
        ║       allowed-origin-patterns:                            ║
        ║         - "https://www.your-domain.com"                   ║
        ║         - "https://*.your-domain.com"                     ║
        ║       block-wildcard-in-production: true                  ║
        ╚══════════════════════════════════════════════════════════╝
        """;

    /**
     * 凭证冲突警告消息
     */
    private static final String CREDENTIALS_WARNING = """
        [CORS] 配置警告：allowCredentials=true 时不应使用通配符来源 "*"
        浏览器会拒绝携带凭证的跨域请求，请配置具体的域名白名单。
        """;

    private final CorsProperties corsProperties;
    private final Environment environment;

    public CorsConfig(CorsProperties corsProperties, Environment environment) {
        this.corsProperties = corsProperties;
        this.environment = environment;
    }

    /**
     * 启动时安全检查。
     *
     * <p>检查 CORS 配置是否符合安全要求，输出相应的警告或阻止应用启动。
     */
    @PostConstruct
    public void validateCorsConfiguration() {
        log.info("[CORS] 配置加载完成: {}", corsProperties);

        // 检查是否包含通配符来源
        if (corsProperties.hasWildcardOrigin()) {
            // 检查是否为生产环境
            boolean isProduction = isProductionEnvironment();

            // 生产环境阻断检查
            if (isProduction && corsProperties.isBlockWildcardInProduction()) {
                log.error("[CORS] 生产环境禁止使用通配符 CORS 配置！请配置具体的域名白名单。");
                throw new IllegalStateException(
                    "CORS 安全检查失败：生产环境不允许使用通配符来源配置。" +
                    "请在 application.yml 中配置具体的 allowed-origin-patterns 域名白名单。"
                );
            }

            // 输出安全警告
            if (corsProperties.isWarnOnWildcard()) {
                log.warn(SECURITY_WARNING);
            }

            // 凭证冲突检查
            if (corsProperties.isAllowCredentials()) {
                log.warn(CREDENTIALS_WARNING);
            }
        } else {
            log.info("[CORS] 配置检查通过，允许的来源: {}", corsProperties.getAllowedOriginPatterns());
        }
    }

    /**
     * 检查当前是否为生产环境。
     *
     * @return 如果当前激活的 profile 包含 "prod" 则返回 true
     */
    private boolean isProductionEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if (profile.toLowerCase().contains("prod")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建跨域过滤器 Bean。
     *
     * <p>根据 {@link CorsProperties} 配置创建 CORS 过滤器，
     * 应用于所有经过网关的请求。
     *
     * @return CorsWebFilter 响应式跨域过滤器实例
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 配置允许的来源模式
        config.setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns());

        // 配置允许的 HTTP 方法
        config.setAllowedMethods(corsProperties.getAllowedMethods());

        // 配置允许的请求头
        config.setAllowedHeaders(corsProperties.getAllowedHeaders());

        // 配置暴露给客户端的响应头
        if (corsProperties.getExposedHeaders() != null && !corsProperties.getExposedHeaders().isEmpty()) {
            config.setExposedHeaders(corsProperties.getExposedHeaders());
        }

        // 配置是否允许携带凭证
        config.setAllowCredentials(corsProperties.isAllowCredentials());

        // 配置预检请求缓存时间
        config.setMaxAge(corsProperties.getMaxAge());

        // 创建基于 URL 的 CORS 配置源
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // 对所有路径应用 CORS 配置
        source.registerCorsConfiguration("/**", config);

        log.debug("[CORS] 过滤器已创建，配置: allowedOriginPatterns={}, allowedMethods={}, allowCredentials={}",
            corsProperties.getAllowedOriginPatterns(),
            corsProperties.getAllowedMethods(),
            corsProperties.isAllowCredentials()
        );

        return new CorsWebFilter(source);
    }
}
