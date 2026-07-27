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
package io.runtime.orchestrator.bootstrap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.runtime.orchestrator.autoconfigure.CapabilityAutoConfiguration;
import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.endpoint.DefaultPluginEndpointDispatcher;
import io.runtime.orchestrator.endpoint.PluginEndpointDispatcher;
import io.runtime.orchestrator.endpoint.RuntimeShellEndpointController;
import io.runtime.orchestrator.plugin.ClasspathPluginRuntimeDescriptorResolver;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.orchestrator.plugin.ServiceLoaderPluginDiscovery;
import io.runtime.sdk.capability.registry.CapabilityRegistry;
import io.runtime.sdk.plugin.BrixPlugin;

/**
 * Auto-configuration for the Host-facing Runtime Shell bootstrap API.
 *
 * <p>This class belongs to L2B Runtime Orchestrator, not Host source. It wires
 * ServiceLoader discovery, manifest descriptor resolution, capability-backed
 * plugin context creation, and the single {@link RuntimeShellBootstrap} API
 * exposed to Host code.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
@AutoConfiguration(after = CapabilityAutoConfiguration.class)
@ConditionalOnClass(BrixPlugin.class)
@ConditionalOnProperty(prefix = "brix.runtime-shell", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RuntimeShellBootstrapProperties.class)
public class RuntimeShellBootstrapAutoConfiguration {

    /**
     * Creates ServiceLoader-based plugin discovery.
     *
     * @return plugin discovery
     */
    @Bean
    @ConditionalOnMissingBean
    public ServiceLoaderPluginDiscovery serviceLoaderPluginDiscovery() {
        return new ServiceLoaderPluginDiscovery();
    }

    /**
     * Creates classpath manifest descriptor resolver.
     *
     * @return descriptor resolver
     */
    @Bean
    @ConditionalOnMissingBean
    public ClasspathPluginRuntimeDescriptorResolver classpathPluginRuntimeDescriptorResolver() {
        return new ClasspathPluginRuntimeDescriptorResolver();
    }

    /**
     * Creates the Runtime Shell endpoint dispatcher.
     *
     * @param properties bootstrap properties
     * @return endpoint dispatcher
     */
    @Bean
    @ConditionalOnMissingBean
    public PluginEndpointDispatcher pluginEndpointDispatcher(RuntimeShellBootstrapProperties properties) {
        return new DefaultPluginEndpointDispatcher(
            java.time.Duration.ofMillis(properties.getEndpointDeadlineMillis()));
    }

    /**
     * Creates the plugin runtime manager.
     *
     * @param discovery ServiceLoader discovery
     * @param descriptorResolver classpath descriptor resolver
     * @param capabilityRegistry optional capability registry
     * @param properties bootstrap properties
     * @return plugin runtime manager
     */
    @Bean
    @ConditionalOnMissingBean
    public PluginRuntimeManager pluginRuntimeManager(
            ServiceLoaderPluginDiscovery discovery,
            ClasspathPluginRuntimeDescriptorResolver descriptorResolver,
            ObjectProvider<CapabilityRegistry> capabilityRegistry,
            PluginEndpointDispatcher endpointDispatcher,
            AutowireCapableBeanFactory beanFactory,
            RuntimeShellBootstrapProperties properties) {
        CapabilityRegistry registry = capabilityRegistry.getIfAvailable(DefaultCapabilityRegistry::new);
        return new PluginRuntimeManager(
            discovery::discover,
            descriptorResolver,
            registry,
            properties.getRequiredPlugins(),
            endpointDispatcher,
            beanFactory::autowireBean);
    }

    /**
     * Creates the Host-facing Runtime Shell bootstrap API.
     *
     * @param pluginRuntimeManager plugin runtime manager
     * @return bootstrap API
     */
    @Bean
    @ConditionalOnMissingBean
    public RuntimeShellBootstrap runtimeShellBootstrap(PluginRuntimeManager pluginRuntimeManager) {
        return new RuntimeShellBootstrap(pluginRuntimeManager);
    }

    /**
     * Creates the Runtime Shell shutdown bridge.
     *
     * @param runtimeShellBootstrap Host-facing Runtime Shell bootstrap API
     * @return shutdown bridge
     */
    @Bean
    @ConditionalOnMissingBean
    public RuntimeShellBootstrapShutdown runtimeShellBootstrapShutdown(
            RuntimeShellBootstrap runtimeShellBootstrap) {
        return new RuntimeShellBootstrapShutdown(runtimeShellBootstrap);
    }

    /**
     * Actuator-only Runtime Shell health contribution.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    static class RuntimeShellActuatorConfiguration {

        /**
         * Creates actuator readiness health contribution when actuator is present.
         *
         * @param runtimeShellBootstrap Host-facing Runtime Shell bootstrap API
         * @return Runtime Shell readiness indicator
         */
        @Bean
        @ConditionalOnMissingBean(name = "runtimeShellReadinessHealthIndicator")
        RuntimeShellReadinessHealthIndicator runtimeShellReadinessHealthIndicator(
                RuntimeShellBootstrap runtimeShellBootstrap) {
            return new RuntimeShellReadinessHealthIndicator(runtimeShellBootstrap);
        }
    }

    /**
     * Spring Web endpoint bridge configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestController")
    static class RuntimeShellWebConfiguration {

        /**
         * Creates the protocol bridge for published Runtime Shell endpoints.
         *
         * @param dispatcher endpoint dispatcher
         * @return endpoint controller
         */
        @Bean
        @ConditionalOnMissingBean
        RuntimeShellEndpointController runtimeShellEndpointController(PluginEndpointDispatcher dispatcher) {
            return new RuntimeShellEndpointController(dispatcher);
        }
    }
}
