package io.brix.platform.gateway.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 安全配置
 * <p>
 * 启用所有安全相关的配置属性类
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@Configuration
@EnableConfigurationProperties({
        ApiKeyAuthProperties.class,
        SensitiveHeaderStripProperties.class,
        LogSanitizationProperties.class
})
public class SecurityConfig {
    // 配置属性通过 @EnableConfigurationProperties 自动装配
}
