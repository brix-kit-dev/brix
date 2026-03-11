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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import io.brix.platform.starter.config.PlatformApiProperties;
import io.brix.platform.starter.config.ServiceProperties;

/**
 * Platform Core Auto-Configuration
 * 
 * <p>Auto-configures platform core beans including service properties and API configuration.</p>
 * 
 * <p>This configuration class serves as the base configuration that other auto-configurations depend on.</p>
 * 
 * <p>Configuration Conditions:</p>
 * <ul>
 *   <li>brix.platform.enabled=true (default)</li>
 * </ul>
 * 
 * <p>Enabled Configuration Properties:</p>
 * <ul>
 *   <li>ServiceProperties: Service base configuration</li>
 *   <li>PlatformApiProperties: API version configuration</li>
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
     * Platform core auto-configuration loaded
     * 
     * <p>ServiceProperties and PlatformApiProperties are automatically registered via @EnableConfigurationProperties.
     * No need to manually create @Bean methods. This avoids bean duplicate definition issues.</p>
     */
    public PlatformCoreAutoConfiguration() {
        log.info("[PlatformCoreAutoConfiguration] Platform core auto-configuration loaded");
    }
}
