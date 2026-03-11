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
package io.runtime.orchestrator.context;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.registry.CapabilityRegistry;
import io.runtime.sdk.context.RuntimeContext;

/**
 * Registry-driven RuntimeContext Implementation.
 *
 * <p>This is the standard implementation of {@link RuntimeContext}, where all capabilities
 * are dynamically retrieved through {@link CapabilityRegistry}.
 * This class was merged from the original {@code StandaloneShellContext} in host-shell-standalone
 * and {@code EmbeddedShellContext} in host-shell-embedded, unifying the RuntimeContext
 * implementation across both Host modes.</p>
 *
 * <h2>Architecture Position (Runtime Shell Architecture)</h2>
 * <p>
 * This class belongs to <b>runtime-orchestrator</b> (orchestration layer), implementing
 * the {@link RuntimeContext} interface defined in runtime-sdk-api (contract layer).
 * The architecture requires the contract layer (Layer 2) to contain only pure interface
 * definitions, while implementations should reside in the orchestration layer.
 * Standalone and Embedded Hosts share the same Context implementation, with different
 * capability combinations injected via Host layer AutoConfiguration.
 * </p>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>Registry-driven</b>: All capabilities retrieved via CapabilityRegistry, no hardcoded fields in Context</li>
 *   <li><b>Declarative Assembly</b>: Host layer AutoConfiguration determines which capabilities to inject</li>
 *   <li><b>Backward Compatible</b>: {@link RuntimeContext} interface default methods provide convenient access</li>
 *   <li><b>Host-agnostic</b>: Standalone and Embedded share the same Context implementation</li>
 * </ul>
 *
 * <h2>Comparison with Legacy Implementations</h2>
 * <table>
 *   <tr><th>Dimension</th><th>Legacy EmbeddedShellContext</th><th>Legacy StandaloneShellContext</th><th>This Class</th></tr>
 *   <tr><td>Capability Access</td><td>Hardcoded fields</td><td>Registry-driven</td><td>Registry-driven</td></tr>
 *   <tr><td>Registry Type</td><td>EmbeddedCapabilityRegistry (non-standard)</td><td>StandaloneCapabilityRegistry</td><td>DefaultCapabilityRegistry</td></tr>
 *   <tr><td>Builder</td><td>Yes</td><td>No</td><td>No (registration logic in Host AutoConfiguration)</td></tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Created in Host layer AutoConfiguration
 * DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
 * registry.register(EventBusCapability.class, kafkaEventBus);
 * registry.freeze();
 *
 * RuntimeContext context = new RegistryDrivenRuntimeContext(registry, "platform", "default");
 *
 * // Plugin layer usage
 * EventBusCapability eventBus = context.getEventBus();
 * context.getCapability(LockCapability.class).ifPresent(lock -> ...);
 * }</pre>
 *
 * @author Brix Platform Authors
 * @since 3.0.0
 * @see RuntimeContext
 * @see CapabilityRegistry
 * @see io.runtime.orchestrator.capability.DefaultCapabilityRegistry
 */
public class RegistryDrivenRuntimeContext implements RuntimeContext {

    private static final Logger log = LoggerFactory.getLogger(RegistryDrivenRuntimeContext.class);

    /** Capability registry (read-only at runtime) */
    private final CapabilityRegistry registry;

    /** Module identifier */
    private final String moduleId;

    /** Tenant identifier */
    private final String tenantId;

    /**
     * Constructs registry-driven runtime context.
     *
     * @param registry capability registry (usually frozen)
     * @param moduleId module unique identifier
     * @param tenantId tenant identifier, defaults to "default" if null
     * @throws NullPointerException when registry or moduleId is null
     */
    public RegistryDrivenRuntimeContext(CapabilityRegistry registry,
                                         String moduleId,
                                         String tenantId) {
        this.registry = Objects.requireNonNull(registry, "Capability registry cannot be null");
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId cannot be null");
        this.tenantId = tenantId != null ? tenantId : "default";

        log.info("Creating RegistryDrivenRuntimeContext: moduleId={}, tenantId={}, capabilities={}",
                moduleId, this.tenantId, registry.size());
    }

    // ==================== RuntimeContext Interface Implementation ====================

    /**
     * Gets capability registry.
     *
     * <p>Through the registry, you can get any registered capability, query metadata, check availability.
     * This is the core entry point for accessing all capabilities.</p>
     *
     * @return capability registry instance
     */
    @Override
    public CapabilityRegistry getCapabilityRegistry() {
        return registry;
    }

    /**
     * Gets tenant identifier.
     *
     * @return tenant ID
     */
    @Override
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets module identifier.
     *
     * @return module ID
     */
    @Override
    public String getModuleId() {
        return moduleId;
    }

    @Override
    public String toString() {
        return "RegistryDrivenRuntimeContext{"
                + "moduleId='" + moduleId + '\''
                + ", tenantId='" + tenantId + '\''
                + ", capabilities=" + registry.size()
                + '}';
    }
}
