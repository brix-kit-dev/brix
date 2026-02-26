package io.brix.platform.starter.autoconfigure;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import io.brix.platform.starter.config.FlywayExtProperties;
import io.brix.platform.starter.config.ServiceProperties;
import io.brix.platform.starter.flyway.FlywayConflictChecker;
import io.brix.platform.starter.flyway.PluginFlywayConfigurer;

/**
 * Flyway 自动配置
 * 
 * <p>自动配置 Flyway 相关 Bean，解决多插件版本冲突问题</p>
 * 
 * <p>配置条件</p>
 * <ul>
 *   <li>classpath 中存在 Flyway </li>
 *   <li>spring.flyway.enabled=true（默认）</li>
 * </ul>
 * 
 * <p>提供Bean</p>
 * <ul>
 *   <li>PluginFlywayConfigurer：插件 Flyway 配置</li>
 *   <li>FlywayConflictChecker：版本冲突检测器</li>
 * </ul>
 * 
 * <p>解决的问题：</p>
 * <ul>
 *   <li>多插件 Flyway 版本冲突</li>
 *   <li>版本命名规范</li>
 *   <li>自动冲突检</li>
 * </ul>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see FlywayExtProperties
 * @see PluginFlywayConfigurer
 */
@AutoConfiguration
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FlywayExtProperties.class)
public class FlywayAutoConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(FlywayAutoConfiguration.class);
    
    /**
     * 插件 Flyway 配置
     * 
     * <p>自定义 Flyway 配置，添加插件前缀支持</p>
     * 
     * @param flywayProperties  Flyway 扩展配置
     * @param serviceProperties 服务配置
     * @return Flyway 配置
     */
    @Bean
    @ConditionalOnMissingBean
    public PluginFlywayConfigurer pluginFlywayConfigurer(
            FlywayExtProperties flywayProperties,
            ServiceProperties serviceProperties) {
        
        log.info("[FlywayAutoConfiguration] 创建插件 Flyway 配置- 前缀: {}",
            flywayProperties.getPluginPrefix());
        
        return new PluginFlywayConfigurer(flywayProperties, serviceProperties);
    }
    
    /**
     * Flyway 版本冲突检测器
     * 
     * @param flywayProperties Flyway 扩展配置
     * @return 冲突检测器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "shinwa.flyway", name = "conflict-check-enabled", havingValue = "true", matchIfMissing = true)
    public FlywayConflictChecker flywayConflictChecker(FlywayExtProperties flywayProperties) {
        log.info("[FlywayAutoConfiguration] 创建 Flyway 版本冲突检测器");
        return new FlywayConflictChecker(flywayProperties);
    }
}
