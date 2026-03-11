/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.orchestrator.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.endpoint.PluginRegistryEndpoint;
import io.runtime.orchestrator.manifest.UIManifestLoader;
import io.runtime.orchestrator.registry.ModuleRegistry;

/**
 * Auto-configuration for plugin registry endpoint.
 *
 * <h2>Architecture Position (Manifest-Driven Dynamic Discovery)</h2>
 * <p>
 * Auto-configures {@link PluginRegistryEndpoint}, providing /api/plugins REST endpoint.
 * Activates only in web applications when {@link ModuleRegistry} bean exists.
 * </p>
 *
 * <h2>Activation Conditions</h2>
 * <ul>
 *   <li>Web application (DispatcherServlet present)</li>
 *   <li>ModuleRegistry bean exists</li>
 *   <li>@RestController annotation support present</li>
 *   <li>Configuration brix.plugin-registry.enabled=true (default true)</li>
 * </ul>
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * brix:
 *   plugin-registry:
 *     enabled: true
 * host:
 *   mode: product
 *   plugins:
 *     base-url: /plugins
 * }</pre>
 *
 * @author Runtime SDK Team
 * @since 3.0.5
 * @see PluginRegistryEndpoint
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnClass(RestController.class)
@ConditionalOnBean(ModuleRegistry.class)
@ConditionalOnProperty(prefix = "brix.plugin-registry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PluginRegistryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistryAutoConfiguration.class);

    /**
     * Creates UI Manifest loader bean.
     *
     * @param objectMapper JSON serializer
     * @return UI Manifest loader
     */
    @Bean
    @ConditionalOnMissingBean(UIManifestLoader.class)
    public UIManifestLoader uiManifestLoader(ObjectMapper objectMapper) {
        log.info("Creating UIManifestLoader - loading UI manifests from classpath");
        return new UIManifestLoader(objectMapper);
    }

    /**
     * Creates plugin registry endpoint bean.
     *
     * @param moduleRegistry module registry
     * @param manifestLoader UI Manifest loader
     * @return plugin registry endpoint
     */
    @Bean
    @ConditionalOnMissingBean(PluginRegistryEndpoint.class)
    public PluginRegistryEndpoint pluginRegistryEndpoint(
            ModuleRegistry moduleRegistry,
            UIManifestLoader manifestLoader) {
        log.info("Creating PluginRegistryEndpoint - /api/plugins endpoint will be available");
        return new PluginRegistryEndpoint(moduleRegistry, manifestLoader);
    }
}
