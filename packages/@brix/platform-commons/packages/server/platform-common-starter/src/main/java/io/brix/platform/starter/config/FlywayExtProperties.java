package io.brix.platform.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Flyway 扩展配置属
 * 
 * <p>解决多插件 Flyway 版本冲突问题
 * 通过插件前缀区分不同插件的迁移脚本</p>
 * 
 * <p>设计目的</p>
 * <ul>
 *   <li>解决问题7：Flyway 脚本版本冲突（V1__init_schema.sql</li>
 *   <li>制定 Flyway 版本命名规范</li>
 *   <li>自动添加插件前缀到迁移脚</li>
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
 * <p>插件前缀分配表：</p>
 * <table>
 *   <tr><th>插件</th><th>前缀</th></tr>
 *   <tr><td>plugin-user</td><td>001</td></tr>
 *   <tr><td>plugin-contract</td><td>002</td></tr>
 *   <tr><td>plugin-file-center</td><td>003</td></tr>
 *   <tr><td>plugin-notification</td><td>004</td></tr>
 *   <tr><td>plugin-partner-catalog</td><td>005</td></tr>
 *   <tr><td>plugin-service-package</td><td>006</td></tr>
 *   <tr><td>plugin-case-engine</td><td>010-019</td></tr>
 *   <tr><td>plugin-medical-*</td><td>020-029</td></tr>
 * </table>
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
 */
@ConfigurationProperties(prefix = "shinwa.flyway")
public class FlywayExtProperties {
    
    /**
     * 插件前缀
     * 
     * <p>用于区分不同插件Flyway 迁移脚本</p>
     * <p>格式位数字，001, 002, 010</p>
     * <p>每个插件分配唯一的前缀范围</p>
     */
    private String pluginPrefix;
    
    /**
     * 迁移脚本位置
     * 
     * <p>Flyway 扫描迁移脚本的路</p>
     * <p>支持 classpath: filesystem: 前缀</p>
     * 
     * <p>默认值：classpath:db/migration</p>
     */
    private String locations = "classpath:db/migration";
    
    /**
     * 是否启用版本冲突检
     * 
     * <p>启用时，启动时会检查是否存在版本号冲突</p>
     * <p>发现冲突时会记录警告日志</p>
     * 
     * <p>默认值：true</p>
     */
    private boolean conflictCheckEnabled = true;
    
    /**
     * 是否使用服务名作schema
     * 
     * <p>启用时，每个服务使用独立的数据库 schema</p>
     * <p>用于更严格的数据隔离</p>
     * 
     * <p>默认值：false</p>
     */
    private boolean useServiceSchema = false;
    
    /**
     * 自定schema 名称
     * 
     * <p>useServiceSchema true 时使</p>
     * <p>如果未设置，则使用服务名作为 schema</p>
     */
    private String schemaName;
    
    /**
     * 是否启用基线版本
     * 
     * <p>对于已有数据库，启用基线可以跳过历史迁移</p>
     * 
     * <p>默认值：false</p>
     */
    private boolean baselineOnMigrate = false;
    
    /**
     * 基线版本
     * 
     * <p>baselineOnMigrate true 时使</p>
     * 
     * <p>默认值：1</p>
     */
    private String baselineVersion = "1";
    
    /**
     * 是否在验证失败时清理
     * 
     * <p>仅用于开发环境！生产环境必须禁用</p>
     * 
     * <p>默认值：false</p>
     */
    private boolean cleanOnValidationError = false;
    
    // ===== 工具方法 =====
    
    /**
     * 生成带插件前缀的版本号
     * 
     * <p>将简单版本号转换为带插件前缀的完整版本号</p>
     * 
     * @param simpleVersion 简单版本号，如 "001", "002"
     * @return 完整版本号，"001_001", "001_002"
     */
    public String buildVersionPrefix(String simpleVersion) {
        if (pluginPrefix == null || pluginPrefix.isEmpty()) {
            return simpleVersion;
        }
        return pluginPrefix + "_" + simpleVersion;
    }
    
    /**
     * 验证插件前缀格式
     * 
     * <p>前缀必须为数字</p>
     * 
     * @return 如果格式正确返回 true
     */
    public boolean isValidPluginPrefix() {
        if (pluginPrefix == null || pluginPrefix.isEmpty()) {
            return false;
        }
        return pluginPrefix.matches("\\d{3}");
    }
    
    // ===== Getters and Setters =====
    
    public String getPluginPrefix() {
        return pluginPrefix;
    }
    
    public void setPluginPrefix(String pluginPrefix) {
        this.pluginPrefix = pluginPrefix;
    }
    
    public String getLocations() {
        return locations;
    }
    
    public void setLocations(String locations) {
        this.locations = locations;
    }
    
    public boolean isConflictCheckEnabled() {
        return conflictCheckEnabled;
    }
    
    public void setConflictCheckEnabled(boolean conflictCheckEnabled) {
        this.conflictCheckEnabled = conflictCheckEnabled;
    }
    
    public boolean isUseServiceSchema() {
        return useServiceSchema;
    }
    
    public void setUseServiceSchema(boolean useServiceSchema) {
        this.useServiceSchema = useServiceSchema;
    }
    
    public String getSchemaName() {
        return schemaName;
    }
    
    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }
    
    public boolean isBaselineOnMigrate() {
        return baselineOnMigrate;
    }
    
    public void setBaselineOnMigrate(boolean baselineOnMigrate) {
        this.baselineOnMigrate = baselineOnMigrate;
    }
    
    public String getBaselineVersion() {
        return baselineVersion;
    }
    
    public void setBaselineVersion(String baselineVersion) {
        this.baselineVersion = baselineVersion;
    }
    
    public boolean isCleanOnValidationError() {
        return cleanOnValidationError;
    }
    
    public void setCleanOnValidationError(boolean cleanOnValidationError) {
        this.cleanOnValidationError = cleanOnValidationError;
    }
}
