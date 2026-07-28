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
import org.springframework.beans.factory.ObjectProvider;

import io.runtime.orchestrator.autoconfigure.CapabilityAutoConfiguration;
import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.endpoint.DefaultPluginEndpointDispatcher;
import io.runtime.orchestrator.endpoint.PluginEndpointDispatcher;
import io.runtime.orchestrator.endpoint.RuntimeShellEndpointController;
import io.runtime.orchestrator.plugin.ClasspathPluginRuntimeDescriptorResolver;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.orchestrator.plugin.ServiceLoaderPluginDiscovery;
import io.runtime.orchestrator.operational.OperationalModuleRuntimeManager;
import io.runtime.orchestrator.operational.ServiceLoaderOperationalModuleDiscovery;
import io.runtime.orchestrator.internalcontract.InternalContractBinder;
import io.runtime.orchestrator.internalcontract.ServiceLoaderInternalContractProviderDiscovery;
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
     * @param internalContracts internal contract binder
     * @param beanFactory Spring bean factory used only to initialize ServiceLoader providers
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
            ObjectProvider<InternalContractBinder> internalContracts,
            AutowireCapableBeanFactory beanFactory,
            RuntimeShellBootstrapProperties properties) {
        CapabilityRegistry registry = capabilityRegistry.getIfAvailable(DefaultCapabilityRegistry::new);
        return new PluginRuntimeManager(
            discovery::discover,
            descriptorResolver,
            registry,
            properties.getRequiredPlugins(),
            endpointDispatcher,
            beanFactory::autowireBean,
            () -> new ServiceLoaderInternalContractProviderDiscovery(
                Thread.currentThread().getContextClassLoader()).discover().stream()
                .map(ServiceLoaderInternalContractProviderDiscovery
                    .DiscoveredInternalContractProvider::provider)
                .toList(),
            internalContracts.getIfAvailable());
    }

    /**
     * Creates the operational ServiceLoader discovery.
     *
     * @return operational discovery
     */
    @Bean
    @ConditionalOnMissingBean
    public ServiceLoaderOperationalModuleDiscovery serviceLoaderOperationalModuleDiscovery() {
        return new ServiceLoaderOperationalModuleDiscovery();
    }

    /**
     * Creates the read-only Host Runtime operational view.
     *
     * @param properties bootstrap properties
     * @return Runtime view
     */
    @Bean
    @ConditionalOnMissingBean
    public HostRuntimeOperationalView hostRuntimeOperationalView(RuntimeShellBootstrapProperties properties) {
        java.util.List<String> required = new java.util.ArrayList<>(properties.getRequiredPlugins());
        required.addAll(properties.getRequiredOperationalModules());
        return new HostRuntimeOperationalView(required);
    }

    /**
     * Creates the L2B internal contract binder shared by plugin-owned providers
     * and operational consumers.
     *
     * @param capabilityRegistry capability registry with the internal namespace implementation
     * @return internal contract binder
     */
    @Bean
    @ConditionalOnMissingBean
    public InternalContractBinder internalContractBinder(CapabilityRegistry capabilityRegistry) {
        if (!(capabilityRegistry instanceof DefaultCapabilityRegistry defaultRegistry)) {
            throw new IllegalStateException(
                "Operational Runtime requires the L2B DefaultCapabilityRegistry internal namespace");
        }
        return new InternalContractBinder(
            defaultRegistry,
            capabilityRegistry,
            Thread.currentThread().getContextClassLoader());
    }

    /**
     * Creates the O0-O8 operational module manager.
     *
     * @param discovery operational module discovery
     * @param capabilityRegistry capability registry
     * @param runtimeView read-only Host Runtime view
     * @param properties bootstrap properties
     * @return operational manager
     */
    @Bean
    @ConditionalOnMissingBean
    public OperationalModuleRuntimeManager operationalModuleRuntimeManager(
            ServiceLoaderOperationalModuleDiscovery discovery,
            CapabilityRegistry capabilityRegistry,
            InternalContractBinder internalContracts,
            HostRuntimeOperationalView runtimeView,
            RuntimeShellBootstrapProperties properties) {
        if (!(capabilityRegistry instanceof DefaultCapabilityRegistry)) {
            throw new IllegalStateException(
                "Operational Runtime requires the L2B DefaultCapabilityRegistry internal namespace");
        }
        return new OperationalModuleRuntimeManager(
            discovery::discover,
            () -> new ServiceLoaderInternalContractProviderDiscovery(
                Thread.currentThread().getContextClassLoader()).discover().stream()
                .map(ServiceLoaderInternalContractProviderDiscovery
                    .DiscoveredInternalContractProvider::provider)
                .toList(),
            internalContracts,
            runtimeView,
            properties.getRequiredOperationalModules(),
            properties.getRuntimeVersion());
    }

    /**
     * Creates the H0-H4/B0-B3 Host coordinator.
     *
     * @param plugins plugin manager
     * @param operationalModules operational manager
     * @param dispatcher single L2B dispatcher
     * @param runtimeView read-only Runtime view
     * @param properties bootstrap properties
     * @return Host coordinator
     */
    @Bean
    @ConditionalOnMissingBean
    public HostBootstrapCoordinator hostBootstrapCoordinator(
            PluginRuntimeManager plugins,
            OperationalModuleRuntimeManager operationalModules,
            PluginEndpointDispatcher dispatcher,
            HostRuntimeOperationalView runtimeView,
            RuntimeShellBootstrapProperties properties,
            ObjectProvider<RuntimeShellFatalAction> fatalActionProvider) {
        if (properties.getFatalExitCode() <= 0) {
            throw new IllegalStateException("brix.runtime-shell.fatal-exit-code must be positive");
        }
        RuntimeShellFatalAction fatalAction = fatalActionProvider.getIfAvailable();
        if (fatalAction == null
                && properties.getHostMode() != RuntimeShellBootstrapProperties.HostMode.EMBEDDED) {
            throw new IllegalStateException(
                "Standalone and Local Hosts must provide a Host-owned RuntimeShellFatalAction");
        }
        if (fatalAction == null) {
            fatalAction = ignored -> { };
        }
        return new HostBootstrapCoordinator(
            plugins,
            operationalModules,
            dispatcher,
            runtimeView,
            fatalAction);
    }

    /**
     * Creates the Host-facing Runtime Shell bootstrap API.
     *
     * @param pluginRuntimeManager plugin runtime manager
     * @return bootstrap API
     */
    @Bean
    @ConditionalOnMissingBean
    public RuntimeShellBootstrap runtimeShellBootstrap(HostBootstrapCoordinator coordinator) {
        return new RuntimeShellBootstrap(coordinator);
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
