package io.brix.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 配置中心自动装配
 *
 * <p>提供配置中心的自动装配能力：
 * <ul>
 *   <li>配置加载 - 从多种来源加载配置</li>
 *   <li>动态刷新 - 配置变更自动刷新</li>
 *   <li>配置加密 - 敏感配置加密存储</li>
 * </ul>
 *
 * @since 3.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(ConfigProperties.class)
public class ConfigAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ConfigAutoConfiguration.class);

    @Bean
    public ConfigManager configManager(ConfigProperties properties) {
        log.info("初始化配置管理器...");
        return new ConfigManager(properties);
    }

    @Bean
    public ConfigRefreshListener configRefreshListener(ConfigManager configManager) {
        log.info("初始化配置刷新监听器...");
        return new ConfigRefreshListener(configManager);
    }
}
