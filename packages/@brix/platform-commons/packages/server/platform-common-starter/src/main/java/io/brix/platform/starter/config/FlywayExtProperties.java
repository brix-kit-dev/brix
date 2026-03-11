/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.brix.platform.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Flyway Extension Configuration Properties
 * 
 * <p>Resolves multi-plugin Flyway version conflicts by using
 * plugin prefixes to distinguish migration scripts from different plugins.</p>
 * 
 * <p>Design Purpose:</p>
 * <ul>
 *   <li>Resolve Issue #7: Flyway script version conflicts (V1__init_schema.sql)</li>
 *   <li>Establish Flyway version naming conventions</li>
 *   <li>Automatically add plugin prefix to migration scripts</li>
 * </ul>
 * 
 * <p>Naming Convention:</p>
 * <pre>
 * V{plugin_prefix}_{version_number}__{description}.sql
 * 
 * Examples:
 * - V001_001__user_init_schema.sql          # plugin-user V1
 * - V001_002__user_add_avatar_column.sql    # plugin-user V2
 * - V002_001__contract_init_schema.sql      # plugin-contract V1
 * </pre>
 * 
 * <p>Plugin Prefix Assignment Table:</p>
 * <table>
 *   <tr><th>Plugin</th><th>Prefix</th></tr>
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
 * <p>Configuration Example:</p>
 * <pre>
 * brix:
 *   flyway:
 *     plugin-prefix: "001"           # plugin-user uses prefix 001
 *     locations: classpath:db/migration
 *     conflict-check-enabled: true
 * </pre>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
@ConfigurationProperties(prefix = "brix.flyway")
public class FlywayExtProperties {
    
    /**
     * Plugin prefix
     * 
     * <p>Used to distinguish Flyway migration scripts from different plugins.</p>
     * <p>Format: 3-digit number, e.g., 001, 002, 010</p>
     * <p>Each plugin is assigned a unique prefix range.</p>
     */
    private String pluginPrefix;
    
    /**
     * Migration script locations
     * 
     * <p>Path where Flyway scans for migration scripts.</p>
     * <p>Supports classpath: and filesystem: prefixes.</p>
     * 
     * <p>Default: classpath:db/migration</p>
     */
    private String locations = "classpath:db/migration";
    
    /**
     * Whether to enable version conflict detection
     * 
     * <p>When enabled, checks for version conflicts at startup.</p>
     * <p>Logs warnings when conflicts are detected.</p>
     * 
     * <p>Default: true</p>
     */
    private boolean conflictCheckEnabled = true;
    
    /**
     * Whether to use service name as schema
     * 
     * <p>When enabled, each service uses a separate database schema.</p>
     * <p>Used for stricter data isolation.</p>
     * 
     * <p>Default: false</p>
     */
    private boolean useServiceSchema = false;
    
    /**
     * Custom schema name
     * 
     * <p>Used when useServiceSchema is true.</p>
     * <p>If not set, service name is used as schema.</p>
     */
    private String schemaName;
    
    /**
     * Whether to enable baseline version
     * 
     * <p>For existing databases, enabling baseline skips historical migrations.</p>
     * 
     * <p>Default: false</p>
     */
    private boolean baselineOnMigrate = false;
    
    /**
     * Baseline version
     * 
     * <p>Used when baselineOnMigrate is true.</p>
     * 
     * <p>Default: 1</p>
     */
    private String baselineVersion = "1";
    
    /**
     * Whether to clean on validation error
     * 
     * <p>For development environment only! Must be disabled in production.</p>
     * 
     * <p>Default: false</p>
     */
    private boolean cleanOnValidationError = false;
    
    // ===== Utility Methods =====
    
    /**
     * Generate version number with plugin prefix
     * 
     * <p>Converts simple version number to full version number with plugin prefix.</p>
     * 
     * @param simpleVersion Simple version number, e.g., "001", "002"
     * @return Full version number, e.g., "001_001", "001_002"
     */
    public String buildVersionPrefix(String simpleVersion) {
        if (pluginPrefix == null || pluginPrefix.isEmpty()) {
            return simpleVersion;
        }
        return pluginPrefix + "_" + simpleVersion;
    }
    
    /**
     * Validate plugin prefix format
     * 
     * <p>Prefix must be a 3-digit number.</p>
     * 
     * @return true if format is correct
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
