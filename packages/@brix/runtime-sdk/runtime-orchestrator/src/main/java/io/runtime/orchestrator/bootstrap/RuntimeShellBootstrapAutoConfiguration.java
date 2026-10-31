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
import io.runtime.orchestrator.endpoint.EndpointMetrics;
import io.runtime.orchestrator.endpoint.EndpointTracing;
import io.runtime.orchestrator.endpoint.PluginEndpointDispatcher;
import io.runtime.orchestrator.endpoint.RuntimeShellEndpointController;
import io.runtime.orchestrator.external.ExternalHttpGateway;
import io.runtime.orchestrator.external.ExternalHttpMetrics;
import io.runtime.orchestrator.external.ExternalHttpProperties;
import io.runtime.orchestrator.external.ExternalHttpRuntime;
import io.runtime.orchestrator.external.ExternalHttpTracing;
import io.runtime.orchestrator.external.ExternalHttpTransport;
import io.runtime.orchestrator.external.ExternalServiceDirectory;
import io.runtime.orchestrator.external.ExternalTargetPolicy;
import io.runtime.orchestrator.external.JdkExternalHttpTransport;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.OwnerUnitOfWorkEndpointBoundary;
import io.runtime.orchestrator.plugin.ClasspathPluginRuntimeDescriptorResolver;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.orchestrator.plugin.ServiceLoaderPluginDiscovery;
import io.runtime.orchestrator.query.InProcessQueryTransport;
import io.runtime.orchestrator.query.QueryGateway;
import io.runtime.orchestrator.query.QueryMetrics;
import io.runtime.orchestrator.query.QueryProperties;
import io.runtime.orchestrator.query.QueryRuntime;
import io.runtime.orchestrator.task.TaskProperties;
import io.runtime.orchestrator.query.QuerySettings;
import io.runtime.orchestrator.query.QueryTracing;
import io.runtime.orchestrator.operational.OperationalModuleRuntimeManager;
import io.runtime.orchestrator.operational.ServiceLoaderOperationalModuleDiscovery;
import io.runtime.orchestrator.internalcontract.InternalContractBinder;
import io.runtime.orchestrator.internalcontract.ServiceLoaderInternalContractProviderDiscovery;
import io.runtime.sdk.capability.AuthCapability;
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
@EnableConfigurationProperties({RuntimeShellBootstrapProperties.class, QueryProperties.class, TaskProperties.class,
    ExternalHttpProperties.class, ExternalHttpProperties.Directory.class})
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
     * @param ownerPersistence the owners' persistence, for the units of work around handlers
     * @param json the host's mapper, which reads typed requests and writes their responses
     * @param tracing starts each call's server span; without one each call only gets a new trace id
     * @param metrics records each call; without them nothing is recorded
     * @return endpoint dispatcher
     */
    @Bean
    @ConditionalOnMissingBean
    public PluginEndpointDispatcher pluginEndpointDispatcher(
            RuntimeShellBootstrapProperties properties,
            ObjectProvider<OwnerPersistenceProvider> ownerPersistence,
            ObjectProvider<tools.jackson.databind.ObjectMapper> json,
            ObjectProvider<EndpointTracing> tracing,
            ObjectProvider<EndpointMetrics> metrics) {
        return new DefaultPluginEndpointDispatcher(
            properties.endpointLimits(),
            new OwnerUnitOfWorkEndpointBoundary(ownerPersistence.getIfAvailable(OwnerPersistenceProvider::none)),
            json.getIfAvailable(() -> tools.jackson.databind.json.JsonMapper.builder().build()),
            tracing.getIfAvailable(() -> EndpointTracing.NOOP),
            metrics.getIfAvailable(() -> EndpointMetrics.NOOP));
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
     * @param queryRuntime the typed queries of this host
     * @param tasks {@code brix.tasks.*}, whose limits a plugin's tasks are checked against at startup
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
            ObjectProvider<OwnerPersistenceProvider> ownerPersistence,
            AutowireCapableBeanFactory beanFactory,
            RuntimeShellBootstrapProperties properties,
            QueryRuntime queryRuntime,
            ObjectProvider<TaskProperties> tasks,
            ExternalHttpRuntime externalHttp) {
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
            internalContracts.getIfAvailable(),
            ownerPersistence.getIfAvailable(OwnerPersistenceProvider::none),
            queryRuntime,
            tasks.getIfAvailable(TaskProperties::new).limits(),
            externalHttp);
    }

    /**
     * Creates the transport external calls are sent with (standard-link plan L8.4).
     *
     * @return the JDK's HTTP client, which follows no redirects
     */
    @Bean
    @ConditionalOnMissingBean
    public ExternalHttpTransport externalHttpTransport() {
        return new JdkExternalHttpTransport();
    }

    /**
     * Creates the host's external calls: the directory, the target policy and the gateway (standard-link plan L8.3,
     * L8.4). Under the {@code prod} and {@code production} profiles every address must be {@code https}.
     *
     * @param properties the limits under {@code brix.external-http}
     * @param directory the addresses under {@code brix.external-services}
     * @param environment tells whether a production profile is active
     * @param transport sends each attempt
     * @param tracing starts each attempt's client span; without one the call only carries the current trace
     * @param metrics records each call; without them nothing is recorded
     * @return the external calls
     */
    @Bean
    @ConditionalOnMissingBean
    public ExternalHttpRuntime externalHttpRuntime(
            ExternalHttpProperties properties,
            ExternalHttpProperties.Directory directory,
            org.springframework.core.env.Environment environment,
            ExternalHttpTransport transport,
            ObjectProvider<ExternalHttpTracing> tracing,
            ObjectProvider<ExternalHttpMetrics> metrics) {
        return new ExternalHttpRuntime(
            ExternalServiceDirectory.of(directory.baseUrls(), environment.matchesProfiles("prod", "production")),
            new ExternalHttpGateway(properties.limits(), transport,
                new ExternalTargetPolicy(properties.getSelfAddresses()),
                tracing.getIfAvailable(() -> ExternalHttpTracing.NOOP),
                metrics.getIfAvailable(() -> ExternalHttpMetrics.NOOP)));
    }

    /**
     * Creates the typed queries of this host (root BRX-RS-MESSAGE-002 to MESSAGE-009): a gateway with the in-process
     * transport when the Host Profile sets {@code brix.query.max-deadline}, none otherwise, in which case a plugin that
     * provides or calls a query does not start (MESSAGE-005).
     *
     * @param properties {@code brix.query.*}
     * @param ownerPersistence the owners' persistence
     * @param tracing the query spans, recorded by an adapter when one is assembled
     * @param metrics the query metrics, recorded when Micrometer is assembled
     * @return the query runtime
     */
    @Bean
    @ConditionalOnMissingBean
    public QueryRuntime queryRuntime(
            QueryProperties properties,
            ObjectProvider<OwnerPersistenceProvider> ownerPersistence,
            ObjectProvider<QueryTracing> tracing,
            ObjectProvider<QueryMetrics> metrics) {
        QuerySettings settings = properties.toSettings();
        if (settings == null) {
            return QueryRuntime.unconfigured();
        }
        java.time.Clock clock = java.time.Clock.systemUTC();
        QueryTracing spans = tracing.getIfAvailable(() -> QueryTracing.NOOP);
        return QueryRuntime.of(new QueryGateway(
            settings,
            new InProcessQueryTransport(ownerPersistence.getIfAvailable(OwnerPersistenceProvider::none), spans, clock),
            spans,
            metrics.getIfAvailable(() -> QueryMetrics.NOOP),
            clock));
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
    public InternalContractBinder internalContractBinder(
            CapabilityRegistry capabilityRegistry,
            ObjectProvider<OwnerPersistenceProvider> ownerPersistence) {
        if (!(capabilityRegistry instanceof DefaultCapabilityRegistry defaultRegistry)) {
            throw new IllegalStateException(
                "Operational Runtime requires the L2B DefaultCapabilityRegistry internal namespace");
        }
        return new InternalContractBinder(
            defaultRegistry,
            capabilityRegistry,
            Thread.currentThread().getContextClassLoader(),
            ownerPersistence.getIfAvailable(OwnerPersistenceProvider::none));
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
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
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
         * @param authCapabilityProvider request authentication context provider
         * @return endpoint controller
         */
        @Bean
        @ConditionalOnMissingBean
        RuntimeShellEndpointController runtimeShellEndpointController(
                PluginEndpointDispatcher dispatcher,
                ObjectProvider<AuthCapability> authCapabilityProvider) {
            return new RuntimeShellEndpointController(dispatcher, authCapabilityProvider);
        }
    }

    /** External call meters, when the host has Micrometer (standard-link plan L8.4). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class ExternalHttpMeters {

        @Bean
        @ConditionalOnMissingBean
        ExternalHttpMetrics externalHttpMetrics(ObjectProvider<io.micrometer.core.instrument.MeterRegistry> registry) {
            return new io.runtime.orchestrator.external.MicrometerExternalHttpMetrics(registry::getIfAvailable);
        }
    }

    /** Endpoint meters, when the host has Micrometer (standard-link plan L7.5). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class EndpointMeters {

        @Bean
        @ConditionalOnMissingBean
        EndpointMetrics endpointMetrics(ObjectProvider<io.micrometer.core.instrument.MeterRegistry> registry) {
            return new io.runtime.orchestrator.endpoint.MicrometerEndpointMetrics(registry::getIfAvailable);
        }
    }

    /** Query meters, when the host has Micrometer (query design §5.7). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class QueryMeters {

        @Bean
        @ConditionalOnMissingBean
        QueryMetrics queryMetrics(ObjectProvider<io.micrometer.core.instrument.MeterRegistry> registry) {
            return new io.runtime.orchestrator.query.MicrometerQueryMetrics(registry::getIfAvailable);
        }
    }
}
