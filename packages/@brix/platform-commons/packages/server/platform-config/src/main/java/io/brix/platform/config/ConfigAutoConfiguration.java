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
package io.brix.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Configuration center auto-configuration.
 *
 * <p>Provides auto-configuration capabilities for the configuration center:
 * <ul>
 *   <li>Configuration loading - Load configurations from multiple sources</li>
 *   <li>Dynamic refresh - Auto-refresh on configuration changes</li>
 *   <li>Configuration encryption - Encrypted storage for sensitive configurations</li>
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
        log.info("Initializing configuration manager...");
        return new ConfigManager(properties);
    }

    @Bean
    public ConfigRefreshListener configRefreshListener(ConfigManager configManager) {
        log.info("Initializing configuration refresh listener...");
        return new ConfigRefreshListener(configManager);
    }
}
