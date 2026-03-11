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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import io.brix.platform.starter.config.PlatformApiProperties;
import io.brix.platform.starter.config.ServiceProperties;
import io.brix.platform.starter.registration.PluginManifestScanner;
import io.brix.platform.starter.registration.RouteScanner;
import io.brix.platform.starter.registration.ServiceHeartbeatService;
import io.brix.platform.starter.registration.ServiceRegistrationService;

/**
 * v2.1 Service Registration Auto-Configuration
 * 
 * <p>Auto-configures service registration, heartbeat and route scanning functionality.</p>
 * 
 * <p>Enable Conditions:</p>
 * <ul>
 *   <li>Web application environment</li>
 *   <li>brix.service.registration-enabled=true (default)</li>
 *   <li>Configured brix.service.name and brix.service.base-url</li>
 * </ul>
 * 
 * <p>Provided Beans:</p>
 * <ul>
 *   <li>RouteScanner - Route scanner</li>
 *   <li>ServiceRegistrationService - Service registration</li>
 *   <li>ServiceHeartbeatService - Service heartbeat</li>
 * </ul>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({RequestMappingHandlerMapping.class})
@ConditionalOnProperty(
    prefix = "brix.service",
    name = "registration-enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(ServiceProperties.class)
@ComponentScan(basePackages = "io.brix.platform.starter")
public class ServiceRegistrationAutoConfiguration {
    
    /**
     * Route Scanner
     * 
     * <p>Scans all REST endpoints exposed by @RestController in the service.</p>
     * 
     * @param handlerMapping Spring's request mapping handler mapping
     * @param serviceProperties Service configuration
     * @return Route scanner
     */
    @Bean
    @ConditionalOnMissingBean
    public RouteScanner routeScanner(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
            ServiceProperties serviceProperties) {
        return new RouteScanner(handlerMapping, serviceProperties);
    }
    
    /**
     * Plugin Manifest Scanner
     * 
     * <p>Scans all plugin META-INF/plugin-manifest.json files in classpath.</p>
     * 
     * @param objectMapper JSON serializer
     * @return Plugin manifest scanner
     */
    @Bean
    @ConditionalOnMissingBean
    public PluginManifestScanner pluginManifestScanner(ObjectMapper objectMapper) {
        return new PluginManifestScanner(objectMapper);
    }
    
    /**
     * Service Registration Service
     * 
     * <p>Responsible for registering service information to base including route manifest and aggregated UI contracts.</p>
     * 
     * @param serviceProperties Service configuration
     * @param platformApiProperties Platform API configuration
     * @param routeScanner Route scanner
     * @param pluginManifestScanner Plugin manifest scanner
     * @param environment Environment configuration
     * @param objectMapper JSON serializer
     * @return Service registration service
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "brix.service",
        name = {"name", "base-url"}
    )
    public ServiceRegistrationService serviceRegistrationService(
            ServiceProperties serviceProperties,
            PlatformApiProperties platformApiProperties,
            RouteScanner routeScanner,
            PluginManifestScanner pluginManifestScanner,
            Environment environment,
            ObjectMapper objectMapper) {
        return new ServiceRegistrationService(
            serviceProperties, platformApiProperties, routeScanner, pluginManifestScanner, environment, objectMapper);
    }
    
    /**
     * Service Heartbeat Service
     * 
     * <p>Responsible for periodically sending heartbeats to base.</p>
     * 
     * @param serviceProperties Service configuration
     * @param registrationService Service registration service
     * @param healthEndpoint Health endpoint
     * @return Service heartbeat service
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "brix.service",
        name = {"name", "base-url"}
    )
    public ServiceHeartbeatService serviceHeartbeatService(
            ServiceProperties serviceProperties,
            ServiceRegistrationService registrationService,
            HealthEndpoint healthEndpoint) {
        return new ServiceHeartbeatService(
            serviceProperties, registrationService, healthEndpoint);
    }
}
