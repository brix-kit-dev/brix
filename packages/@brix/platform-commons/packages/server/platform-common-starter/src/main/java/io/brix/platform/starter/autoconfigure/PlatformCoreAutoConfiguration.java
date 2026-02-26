package io.brix.platform.starter.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import io.brix.platform.starter.config.PlatformApiProperties;
import io.brix.platform.starter.config.ServiceProperties;

/**
 * 平台核心自动配置
 * 
 * <p>自动配置平台核心相关 Bean，包括服务属性和 API 配置</p>
 * 
 * <p>此配置类为基础配置，其他自动配置类依赖于此</p>
 * 
 * <p>配置条件</p>
 * <ul>
 *   <li>brix.platform.enabled=true（默认）</li>
 * </ul>
 * 
 * <p>启用的配置属性：</p>
 * <ul>
 *   <li>ServiceProperties：服务基础配置</li>
 *   <li>PlatformApiProperties：API 版本配置</li>
 * </ul>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see ServiceProperties
 * @see PlatformApiProperties
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "brix.platform", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({ServiceProperties.class, PlatformApiProperties.class})
public class PlatformCoreAutoConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(PlatformCoreAutoConfiguration.class);
    
    /**
     * 平台核心自动配置已加
     * 
     * <p>ServiceProperties PlatformApiProperties 通过 @EnableConfigurationProperties 自动注册
     * 无需手动创建 @Bean 方法。这避免Bean 重复定义的问题</p>
     */
    public PlatformCoreAutoConfiguration() {
        log.info("[PlatformCoreAutoConfiguration] 平台核心自动配置已加");
    }
}
