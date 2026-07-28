/*
 * Copyright 2026 Brix Authors
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.context.RegistryDrivenRuntimeContext;
import io.runtime.orchestrator.lifecycle.DefaultModuleLifecycleManager;
import io.runtime.orchestrator.lifecycle.ModuleLifecycleManager;
import io.runtime.orchestrator.registry.DefaultModuleRegistry;
import io.runtime.orchestrator.registry.ModuleRegistry;
import io.runtime.sdk.capability.ObservabilityCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityDescriptor;
import io.runtime.sdk.capability.registry.CapabilityRegistry;
import io.runtime.sdk.context.RuntimeContext;

/**
 * Auto-configuration for capability scanning and assembly.
 *
 * <h2>Architecture Position</h2>
 * <p>
 * This class extracts common capability scanning logic from host-shell-standalone's
 * {@code StandaloneShellAutoConfiguration} and host-shell-embedded's 
 * {@code EmbeddedShellAutoConfiguration}. Follows the <b>Ultra-Thin Host</b> principle:
 * Host layer only handles assembly configuration and Import, containing no business logic.
 * </p>
 *
 * <h2>Core Responsibilities</h2>
 * <ul>
 *   <li><b>Auto-discovery</b>: Scans all beans annotated with {@link Capability @Capability}</li>
 *   <li><b>Registration</b>: Registers discovered capabilities to {@link CapabilityRegistry}</li>
 *   <li><b>Filtering</b>: Filters out disabled capabilities based on configuration</li>
 *   <li><b>Validation</b>: Verifies required capabilities are registered at startup</li>
 *   <li><b>RuntimeContext creation</b>: Assembles {@link RegistryDrivenRuntimeContext}</li>
 * </ul>
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * brix:
 *   capability:
 *     auto-discovery: true
 *     validate-on-startup: true
 *     required:
 *       - io.runtime.sdk.capability.EventBusCapability
 *     disabled:
 *       - io.runtime.sdk.capability.ResilienceCapability
 * }</pre>
 *
 * <h2>Host Layer Usage</h2>
 * <pre>{@code
 * @AutoConfiguration
 * @Import(CapabilityAutoConfiguration.class)
 * @EnableConfigurationProperties(StandaloneShellProperties.class)
 * public class StandaloneShellAutoConfiguration {
 *     // EMPTY — ultra-thin Host
 * }
 * }</pre>
 *
 * @author Brix Platform Authors
 * @since 3.0.4
 * @see CapabilityRegistry
 * @see CapabilityProperties
 * @see DefaultCapabilityRegistry
 */
@AutoConfiguration(afterName = {
    "io.infra.adapter.fallback.FallbackCapabilitiesAutoConfiguration",
    "io.infra.adapter.otel.autoconfigure.OTelAdapterAutoConfiguration",
    "io.infra.adapter.kafka.config.KafkaAutoConfiguration",
    "io.infra.adapter.redis.config.RedisAutoConfiguration"
})
@ConditionalOnClass(RuntimeContext.class)
@ConditionalOnProperty(prefix = "brix.capability", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CapabilityProperties.class)
public class CapabilityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CapabilityAutoConfiguration.class);

    private final CapabilityProperties properties;

    /**
     * Constructs capability auto-configuration.
     *
     * @param properties capability configuration properties
     */
    public CapabilityAutoConfiguration(CapabilityProperties properties) {
        this.properties = properties;
        log.info("Initializing CapabilityAutoConfiguration: autoDiscovery={}, validateOnStartup={}",
                properties.isAutoDiscovery(), properties.isValidateOnStartup());
    }

    /**
     * Creates the capability registry bean.
     *
     * <p>Scans all beans annotated with @Capability, filters and registers capabilities based on configuration.</p>
     *
     * <h3>Scanning Process</h3>
     * <ol>
     *   <li>Retrieves all beans annotated with @Capability</li>
     *   <li>Filters out capabilities in the disabled list</li>
     *   <li>Registers to DefaultCapabilityRegistry</li>
     *   <li>Supplements registration for capabilities in required/optional lists via type matching</li>
     *   <li>Validates all required capabilities are registered</li>
     *   <li>Freezes the registry</li>
     * </ol>
     *
     * @param applicationContext Spring application context
     * @param observabilityCapabilities observability capability provider (for forced instantiation)
     * @return capability registry
     */
    @Bean
    @ConditionalOnMissingBean(CapabilityRegistry.class)
    public CapabilityRegistry capabilityRegistry(
            ApplicationContext applicationContext,
            ObjectProvider<List<ObservabilityCapability>> observabilityCapabilities) {
        log.info("Creating capability registry...");

        // Force instantiation of all ObservabilityCapability beans to ensure they are created before scanning
        List<ObservabilityCapability> obsCapabilities = observabilityCapabilities.getIfAvailable();
        if (obsCapabilities != null && !obsCapabilities.isEmpty()) {
            log.debug("Force instantiated {} ObservabilityCapability beans", obsCapabilities.size());
        }

        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        Set<String> disabledCapabilities = new HashSet<>(properties.getDisabled());

        // Auto-discover and register beans annotated with @Capability
        if (properties.isAutoDiscovery()) {
            scanAndRegisterAnnotatedCapabilities(applicationContext, registry, disabledCapabilities);
        }

        // Register capabilities in required/optional lists via type matching
        registerCapabilitiesByType(applicationContext, registry, disabledCapabilities);

        // Validate all required capabilities are registered
        if (properties.isValidateOnStartup()) {
            validateRequiredCapabilities(registry);
        }

        // Freeze registry to prevent runtime tampering
        registry.freeze();
        log.info("Capability registry frozen, {} capabilities registered", registry.getAllDescriptors().size());

        return registry;
    }

    /**
     * Creates the module registry bean.
     *
     * <p>Scans all beans implementing {@link io.runtime.sdk.capability.LifecycleCapability},
     * auto-registers them to {@link ModuleRegistry}. This is the foundation for plugin dynamic discovery.</p>
     *
     * @param applicationContext Spring application context
     * @return module registry
     */
    @Bean
    @ConditionalOnMissingBean(ModuleRegistry.class)
    public ModuleRegistry moduleRegistry(ApplicationContext applicationContext) {
        log.info("Creating module registry...");

        DefaultModuleRegistry registry = new DefaultModuleRegistry();

        // Scan and register all LifecycleCapability beans
        Map<String, io.runtime.sdk.capability.LifecycleCapability> modules =
                applicationContext.getBeansOfType(io.runtime.sdk.capability.LifecycleCapability.class);

        log.info("Discovered {} LifecycleCapability modules", modules.size());

        for (io.runtime.sdk.capability.LifecycleCapability module : modules.values()) {
            registry.register(module);
            log.debug("Registered module: {}", module.getMetadata().getModuleId());
        }

        log.info("Module registry created, {} modules registered", registry.size());
        return registry;
    }

    /**
     * Creates the module lifecycle manager bean.
     *
     * <p>[v3.1.0 New] Manages lifecycle for all modules including initialization, startup,
     * health checks, and shutdown. Follows LifecycleCapability specification for complete
     * plugin lifecycle management.</p>
     *
     * <h3>Lifecycle Phases</h3>
     * <ol>
 *   <li>INIT - Initialization phase (capability validation, dependency checks)</li>
 *   <li>START - Startup phase (start in dependency order)</li>
 *   <li>RUNNING - Running phase (periodic health checks)</li>
 *   <li>STOP - Shutdown phase (graceful stop in reverse order)</li>
 * </ol>
     *
     * @param moduleRegistry module registry
     * @param runtimeContext runtime context (for building context factory)
     * @return module lifecycle manager
     */
    @Bean
    @ConditionalOnMissingBean(ModuleLifecycleManager.class)
    public ModuleLifecycleManager moduleLifecycleManager(ModuleRegistry moduleRegistry, RuntimeContext runtimeContext) {
        log.info("Creating module lifecycle manager...");

        // Create context factory based on RuntimeContext
        DefaultModuleLifecycleManager.RuntimeContextFactory contextFactory = moduleId -> {
            // For each module, create an independent runtime context
            // In actual scenarios, isolated contexts can be created for each module
            return runtimeContext;
        };

        DefaultModuleLifecycleManager lifecycleManager = new DefaultModuleLifecycleManager(
                moduleRegistry, contextFactory);

        log.info("Module lifecycle manager created, ready to manage {} module lifecycles", moduleRegistry.size());
        return lifecycleManager;
    }

    /**
     * Creates the RuntimeContext bean.
     *
     * <p>Uses the unified {@link RegistryDrivenRuntimeContext} implementation,
     * all capability access is delegated through {@link CapabilityRegistry}.</p>
     *
     * @param capabilityRegistry capability registry
     * @return runtime context
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(RuntimeContext.class)
    public RuntimeContext runtimeContext(CapabilityRegistry capabilityRegistry) {
        log.info("Creating RegistryDrivenRuntimeContext: moduleId={}, tenantId={}",
                properties.getModuleId(), properties.getTenantId());

        RegistryDrivenRuntimeContext context = new RegistryDrivenRuntimeContext(
                capabilityRegistry,
                properties.getModuleId(),
                properties.getTenantId()
        );

        log.info("RegistryDrivenRuntimeContext created: {}", context);
        return context;
    }

    /**
     * Creates the RegistryDrivenRuntimeContext typed bean (for backward compatibility).
     *
     * <p>Some scenarios may require direct injection of {@link RegistryDrivenRuntimeContext} type,
     * this method provides type-safe conversion.</p>
     *
     * @param runtimeContext runtime context
     * @return RegistryDrivenRuntimeContext instance
     * @throws IllegalStateException if runtimeContext is not of RegistryDrivenRuntimeContext type
     */
    @Bean
    @ConditionalOnMissingBean(RegistryDrivenRuntimeContext.class)
    public RegistryDrivenRuntimeContext registryDrivenRuntimeContext(RuntimeContext runtimeContext) {
        if (runtimeContext instanceof RegistryDrivenRuntimeContext registryDriven) {
            return registryDriven;
        }
        throw new IllegalStateException("RuntimeContext is not of RegistryDrivenRuntimeContext type");
    }

    // ==================== Private Helper Methods ====================

    /**
     * Scans and registers beans annotated with @Capability.
     *
     * @param ctx Spring application context
     * @param registry capability registry
     * @param disabledCapabilities set of disabled capability type names
     */
    private void scanAndRegisterAnnotatedCapabilities(ApplicationContext ctx,
                                                       DefaultCapabilityRegistry registry,
                                                       Set<String> disabledCapabilities) {
        // Eagerly instantiate every bean whose declared type carries @Capability.
        //
        // Spring's getBeansWithAnnotation(...) only returns beans that have already
        // been instantiated; for capabilities created via @Bean factory methods in
        // an Adapter's @AutoConfiguration (e.g. infra-adapter-minio's
        // FileStorageCapability bean), the runtime class — and therefore the
        // @Capability annotation on it — is unknown until the bean is created.
        // getBeanNamesForAnnotation, in contrast, also inspects factory-method
        // return types' classes, so it discovers Adapter-provided capabilities
        // without needing to know the contract type up-front. We then force-
        // instantiate each so the subsequent getBeansWithAnnotation() call sees
        // them. This preserves the Capability Contract scanning model
        // (Blueprint v3.0.x §3) for both directly-declared and factory-method
        // -provided capability beans, with no per-Adapter wiring in the Host.
        String[] capabilityBeanNames = ctx.getBeanNamesForAnnotation(Capability.class);
        for (String name : capabilityBeanNames) {
            if (name != null) {
                ctx.getBean(name);
            }
        }

        Map<String, Object> capabilityBeans = ctx.getBeansWithAnnotation(Capability.class);
        log.info("Found {} beans annotated with @Capability", capabilityBeans.size());

        for (Map.Entry<String, Object> entry : capabilityBeans.entrySet()) {
            Object bean = entry.getValue();
            Capability annotation = bean.getClass().getAnnotation(Capability.class);

            if (annotation != null) {
                CapabilityDescriptor descriptor = CapabilityDescriptor.fromAnnotation(annotation, bean.getClass());
                String typeName = descriptor.getType().getName();

                // Check if disabled
                if (disabledCapabilities.contains(typeName)) {
                    log.info("Skipping disabled capability: {}", typeName);
                    continue;
                }

                @SuppressWarnings("unchecked")
                Class<Object> capabilityType = (Class<Object>) descriptor.getType();
                registry.register(capabilityType, bean, descriptor);
                log.debug("Registered capability: {} -> {}", typeName, bean.getClass().getSimpleName());

                // Honor SDK alias semantics: when a capability contract extends another
                // capability contract in the io.runtime.sdk.capability package (e.g.
                // AuthCapability extends AuthContextCapability), also register the bean
                // under each such super-interface so that lookups by the legacy/aliased
                // contract type succeed. Per SDK javadoc, aliased contracts are
                // "fully equivalent at runtime, no forced migration required".
                registerCapabilityAliasSuperInterfaces(registry, capabilityType, bean, disabledCapabilities);
            }
        }
    }

    /**
     * Registers the given capability bean under any of its super-interfaces that are
     * themselves capability contracts (interfaces declared under
     * {@code io.runtime.sdk.capability}). The registration is idempotent — existing
     * registrations are preserved.
     *
     * <p>Walks the full super-interface graph (transitively) so that multi-level
     * alias chains are honored.</p>
     *
     * @param registry             the capability registry being populated
     * @param capabilityType       the primary contract type the bean was registered under
     * @param bean                 the capability instance
     * @param disabledCapabilities set of disabled capability type names (skipped)
     */
    private void registerCapabilityAliasSuperInterfaces(DefaultCapabilityRegistry registry,
                                                        Class<?> capabilityType,
                                                        Object bean,
                                                        Set<String> disabledCapabilities) {
        Set<Class<?>> visited = new HashSet<>();
        Deque<Class<?>> stack = new ArrayDeque<>();
        for (Class<?> directParent : capabilityType.getInterfaces()) {
            stack.push(directParent);
        }
        while (!stack.isEmpty()) {
            Class<?> parent = stack.pop();
            if (!visited.add(parent)) {
                continue;
            }
            // Only treat interfaces in the SDK capability contract package as aliases.
            if (parent.isInterface()
                    && parent.getName().startsWith("io.runtime.sdk.capability.")
                    && !disabledCapabilities.contains(parent.getName())
                    && !registry.isAvailable(parent)) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                boolean registered = registry.registerIfAbsent((Class) parent, bean);
                if (registered) {
                    log.debug("Registered capability alias: {} -> {} (alias of {})",
                            parent.getSimpleName(),
                            bean.getClass().getSimpleName(),
                            capabilityType.getSimpleName());
                }
            }
            for (Class<?> grandParent : parent.getInterfaces()) {
                stack.push(grandParent);
            }
        }
    }

    /**
     * Registers capabilities via type matching.
     *
     * <p>Iterates through capability types in required and optional lists,
     * registers them if not yet registered and corresponding beans exist.</p>
     *
     * @param ctx Spring application context
     * @param registry capability registry
     * @param disabledCapabilities set of disabled capability type names
     */
    private void registerCapabilitiesByType(ApplicationContext ctx,
                                             DefaultCapabilityRegistry registry,
                                             Set<String> disabledCapabilities) {
        Set<String> allCapabilityTypes = new HashSet<>();
        allCapabilityTypes.addAll(properties.getRequired());
        allCapabilityTypes.addAll(properties.getOptional());

        for (String typeName : allCapabilityTypes) {
            if (disabledCapabilities.contains(typeName)) {
                continue;
            }

            try {
                Class<?> capabilityType = Class.forName(typeName);

                // Skip if already registered
                if (registry.isAvailable(capabilityType)) {
                    continue;
                }

                // Try to get bean from Spring context
                Map<String, ?> beans = ctx.getBeansOfType(capabilityType);
                if (!beans.isEmpty()) {
                    Object bean = beans.values().iterator().next();
                    @SuppressWarnings("unchecked")
                    Class<Object> type = (Class<Object>) capabilityType;
                    registry.registerIfAbsent(type, bean);
                    log.debug("Registered capability via type matching: {} -> {}", typeName, bean.getClass().getSimpleName());
                }
            } catch (ClassNotFoundException e) {
                log.warn("Capability type not found: {}", typeName);
            }
        }
    }

    /**
     * Validates that all required capabilities are registered.
     *
     * <p>Iterates through required list, checks if each capability is registered.
     * Throws {@link IllegalStateException} if any required capability is not registered.</p>
     *
     * @param registry capability registry
     * @throws IllegalStateException if any required capability is not registered
     */
    private void validateRequiredCapabilities(DefaultCapabilityRegistry registry) {
        for (String typeName : properties.getRequired()) {
            try {
                Class<?> capabilityType = Class.forName(typeName);
                if (!registry.isAvailable(capabilityType)) {
                    throw new IllegalStateException(
                            "Required capability not registered: " + typeName +
                                    ". Please ensure the adapter providing this capability is added to dependencies, " +
                                    "or move it to the optional list in configuration.");
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(
                        "Required capability type not found: " + typeName +
                                ". Required capabilities must be resolvable at startup; " +
                                "add the dependency that declares this capability contract, " +
                                "or move it to the optional list in configuration.",
                        e);
            }
        }
        log.info("Required capabilities validation passed");
    }
}
