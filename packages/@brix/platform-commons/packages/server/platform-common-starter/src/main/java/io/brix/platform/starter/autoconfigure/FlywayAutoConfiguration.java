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
 * Flyway Auto-Configuration
 * 
 * <p>Auto-configures Flyway-related beans to resolve multi-plugin version conflicts.</p>
 * 
 * <p>Configuration Conditions:</p>
 * <ul>
 *   <li>Flyway class exists in classpath</li>
 *   <li>spring.flyway.enabled=true (default)</li>
 * </ul>
 * 
 * <p>Provided Beans:</p>
 * <ul>
 *   <li>PluginFlywayConfigurer: Plugin Flyway configuration</li>
 *   <li>FlywayConflictChecker: Version conflict detector</li>
 * </ul>
 * 
 * <p>Resolved Issues:</p>
 * <ul>
 *   <li>Multi-plugin Flyway version conflicts</li>
 *   <li>Version naming conventions</li>
 *   <li>Automatic conflict detection</li>
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
     * Plugin Flyway Configurer
     * 
     * <p>Customizes Flyway configuration with plugin prefix support.</p>
     * 
     * @param flywayProperties  Flyway extension properties
     * @param serviceProperties Service properties
     * @return Flyway configurer
     */
    @Bean
    @ConditionalOnMissingBean
    public PluginFlywayConfigurer pluginFlywayConfigurer(
            FlywayExtProperties flywayProperties,
            ServiceProperties serviceProperties) {
        
        log.info("[FlywayAutoConfiguration] Creating Plugin Flyway Configurer - prefix: {}",
            flywayProperties.getPluginPrefix());
        
        return new PluginFlywayConfigurer(flywayProperties, serviceProperties);
    }
    
    /**
     * Flyway Version Conflict Checker
     * 
     * @param flywayProperties Flyway extension properties
     * @return Conflict checker
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "brix.flyway", name = "conflict-check-enabled", havingValue = "true", matchIfMissing = true)
    public FlywayConflictChecker flywayConflictChecker(FlywayExtProperties flywayProperties) {
        log.info("[FlywayAutoConfiguration] Creating Flyway Version Conflict Checker");
        return new FlywayConflictChecker(flywayProperties);
    }
}
