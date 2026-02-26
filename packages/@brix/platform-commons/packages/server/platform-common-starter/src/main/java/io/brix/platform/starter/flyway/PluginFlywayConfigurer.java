package io.brix.platform.starter.flyway;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import io.brix.platform.starter.config.FlywayExtProperties;
import io.brix.platform.starter.config.ServiceProperties;

/**
 * 插件 Flyway 配置
 * 
 * <p>自定义 Flyway 配置，解决多插件 Flyway 版本冲突问题</p>
 * 
 * <p>设计目的</p>
 * <ul>
 *   <li>解决问题7：Flyway 脚本版本冲突（V1__init_schema.sql</li>
 *   <li>自动设置插件前缀</li>
 *   <li>配置迁移脚本位置</li>
 *   <li>启用冲突检</li>
 * </ul>
 * 
 * <p>命名规范</p>
 * <pre>
 * V{插件前缀}_{版本号}__{描述}.sql
 * 
 * 示例
 * - V001_001__user_init_schema.sql          # plugin-user V1
 * - V001_002__user_add_avatar_column.sql    # plugin-user V2
 * - V002_001__contract_init_schema.sql      # plugin-contract V1
 * </pre>
 * 
 * <p>配置示例</p>
 * <pre>
 * shinwa:
 *   flyway:
 *     plugin-prefix: "001"           # plugin-user 使用 001 前缀
 *     locations: classpath:db/migration
 *     conflict-check-enabled: true
 * </pre>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see FlywayExtProperties
 */
public class PluginFlywayConfigurer implements FlywayConfigurationCustomizer {
    
    private static final Logger log = LoggerFactory.getLogger(PluginFlywayConfigurer.class);
    
    /**
     * Flyway 扩展配置
     */
    private final FlywayExtProperties flywayProperties;
    
    /**
     * 服务配置
     */
    private final ServiceProperties serviceProperties;
    
    /**
     * 构造函数
     * 
     * @param flywayProperties  Flyway 扩展配置
     * @param serviceProperties 服务配置
     */
    public PluginFlywayConfigurer(FlywayExtProperties flywayProperties,
                                 ServiceProperties serviceProperties) {
        this.flywayProperties = flywayProperties;
        this.serviceProperties = serviceProperties;
    }
    
    /**
     * 自定义 Flyway 配置
     * 
     * @param configuration Flyway 配置
     */
    @Override
    public void customize(FluentConfiguration configuration) {
        // 1. 设置迁移脚本位置
        if (flywayProperties.getLocations() != null) {
            String[] locations = flywayProperties.getLocations().split(",");
            configuration.locations(locations);
            log.info("[PluginFlywayConfigurer] 设置迁移脚本位置: {}", 
                flywayProperties.getLocations());
        }
        
        // 2. 设置 Schema（如果启用服Schema 隔离
        if (flywayProperties.isUseServiceSchema()) {
            String schema = flywayProperties.getSchemaName();
            if (schema == null || schema.isEmpty()) {
                // 使用服务名作Schema
                schema = serviceProperties != null && serviceProperties.getName() != null
                    ? serviceProperties.getName().replace("-", "_")
                    : "public";
            }
            configuration.schemas(schema);
            log.info("[PluginFlywayConfigurer] 设置 Schema: {}", schema);
        }
        
        // 3. 设置基线（如果启用）
        if (flywayProperties.isBaselineOnMigrate()) {
            configuration.baselineOnMigrate(true);
            configuration.baselineVersion(flywayProperties.getBaselineVersion());
            log.info("[PluginFlywayConfigurer] 启用基线迁移，版 {}", 
                flywayProperties.getBaselineVersion());
        }
        
        // 4. 设置验证失败时清理（仅开发环境！
        if (flywayProperties.isCleanOnValidationError()) {
            configuration.cleanOnValidationError(true);
            log.warn("[PluginFlywayConfigurer] 警告：已启用验证失败时清理，仅限开发环境使用！");
        }
        
        // 5. 记录插件前缀信息
        if (flywayProperties.getPluginPrefix() != null) {
            log.info("[PluginFlywayConfigurer] 插件前缀: {} (请确保迁移脚本使用正确的版本命名)", 
                flywayProperties.getPluginPrefix());
        }
        
        // 6. 执行冲突检
        if (flywayProperties.isConflictCheckEnabled()) {
            // 冲突检测将FlywayConflictChecker 中执
            log.debug("[PluginFlywayConfigurer] 版本冲突检测已启用");
        }
    }
}
