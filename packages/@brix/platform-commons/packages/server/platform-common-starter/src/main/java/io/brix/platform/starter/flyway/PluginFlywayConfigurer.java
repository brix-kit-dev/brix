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
package io.brix.platform.starter.flyway;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import io.brix.platform.starter.config.FlywayExtProperties;
import io.brix.platform.starter.config.ServiceProperties;

/**
 * Plugin Flyway Configurer
 * 
 * <p>Customizes Flyway configuration to resolve multi-plugin Flyway version conflicts.</p>
 * 
 * <p>Design Purpose:</p>
 * <ul>
 *   <li>Resolve Issue 7: Flyway script version conflicts (V1__init_schema.sql)</li>
 *   <li>Automatically set plugin prefix</li>
 *   <li>Configure migration script locations</li>
 *   <li>Enable conflict detection</li>
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
 * @see FlywayExtProperties
 */
public class PluginFlywayConfigurer implements FlywayConfigurationCustomizer {
    
    private static final Logger log = LoggerFactory.getLogger(PluginFlywayConfigurer.class);
    
    /**
     * Flyway extension configuration
     */
    private final FlywayExtProperties flywayProperties;
    
    /**
     * Service configuration
     */
    private final ServiceProperties serviceProperties;
    
    /**
     * Constructor
     * 
     * @param flywayProperties  Flyway extension configuration
     * @param serviceProperties Service configuration
     */
    public PluginFlywayConfigurer(FlywayExtProperties flywayProperties,
                                 ServiceProperties serviceProperties) {
        this.flywayProperties = flywayProperties;
        this.serviceProperties = serviceProperties;
    }
    
    /**
     * Customize Flyway configuration
     * 
     * @param configuration Flyway configuration
     */
    @Override
    public void customize(FluentConfiguration configuration) {
        // 1. Set migration script locations
        if (flywayProperties.getLocations() != null) {
            String[] locations = flywayProperties.getLocations().split(",");
            configuration.locations(locations);
            log.info("[PluginFlywayConfigurer] Set migration script locations: {}", 
                flywayProperties.getLocations());
        }
        
        // 2. Set schema (if service schema isolation is enabled)
        if (flywayProperties.isUseServiceSchema()) {
            String schema = flywayProperties.getSchemaName();
            if (schema == null || schema.isEmpty()) {
                // Use service name as schema
                schema = serviceProperties != null && serviceProperties.getName() != null
                    ? serviceProperties.getName().replace("-", "_")
                    : "public";
            }
            configuration.schemas(schema);
            log.info("[PluginFlywayConfigurer] Set schema: {}", schema);
        }
        
        // 3. Set baseline (if enabled)
        if (flywayProperties.isBaselineOnMigrate()) {
            configuration.baselineOnMigrate(true);
            configuration.baselineVersion(flywayProperties.getBaselineVersion());
            log.info("[PluginFlywayConfigurer] Enabled baseline migration, version: {}", 
                flywayProperties.getBaselineVersion());
        }
        
        // 4. Set clean on validation error (development environment only!)
        if (flywayProperties.isCleanOnValidationError()) {
            configuration.cleanOnValidationError(true);
            log.warn("[PluginFlywayConfigurer] Warning: Clean on validation error is enabled, for development environment only!");
        }
        
        // 5. Log plugin prefix information
        if (flywayProperties.getPluginPrefix() != null) {
            log.info("[PluginFlywayConfigurer] Plugin prefix: {} (please ensure migration scripts use correct version naming)", 
                flywayProperties.getPluginPrefix());
        }
        
        // 6. Execute conflict detection
        if (flywayProperties.isConflictCheckEnabled()) {
            // Conflict detection will be executed in FlywayConflictChecker
            log.debug("[PluginFlywayConfigurer] Version conflict detection is enabled");
        }
    }
}
