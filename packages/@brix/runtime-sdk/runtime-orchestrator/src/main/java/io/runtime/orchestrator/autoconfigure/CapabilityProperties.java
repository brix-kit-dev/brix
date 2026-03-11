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

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for capability scanning and assembly.
 *
 * <h2>Architecture Position</h2>
 * <p>
 * This class defines capability scanning configuration properties, extracted from
 * the original Host layer's {@code StandaloneShellProperties.CapabilitiesConfig}
 * and enhanced. All Host modes (Standalone/Embedded) share this configuration structure.
 * </p>
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * brix:
 *   capability:
 *     enabled: true
 *     module-id: my-module
 *     tenant-id: default
 *     auto-discovery: true
 *     validate-on-startup: true
 *     required:
 *       - io.runtime.sdk.capability.EventBusCapability
 *       - io.runtime.sdk.capability.StateStoreCapability
 *     optional:
 *       - io.runtime.sdk.capability.LockCapability
 *     disabled:
 *       - io.runtime.sdk.capability.ResilienceCapability
 * }</pre>
 *
 * <h2>Configuration Priority</h2>
 * <p>
 * Host layer specific configurations (e.g., {@code brix.shell.standalone.capabilities})
 * can override this default configuration through Spring Boot's configuration binding.
 * </p>
 *
 * @author Brix Platform Authors
 * @since 3.0.4
 * @see CapabilityAutoConfiguration
 */
@ConfigurationProperties(prefix = "brix.capability")
public class CapabilityProperties {

    /**
     * Whether to enable capability auto-configuration.
     *
     * <p>Set to false to completely disable {@link CapabilityAutoConfiguration}.</p>
     */
    private boolean enabled = true;

    /**
     * Module ID.
     *
     * <p>Used to identify the current running module in {@link io.runtime.sdk.context.RuntimeContext}.
     * Recommended to keep consistent with Maven artifactId.</p>
     */
    private String moduleId = "default-module";

    /**
     * Tenant ID.
     *
     * <p>Default tenant identifier for multi-tenant scenarios.
     * Can be dynamically switched via TenantContext at runtime.</p>
     */
    private String tenantId = "default";

    /**
     * Whether to enable capability auto-discovery.
     *
     * <p>When enabled, automatically scans all beans annotated with
     * {@link io.runtime.sdk.capability.registry.Capability @Capability}
     * and registers them to {@link io.runtime.sdk.capability.registry.CapabilityRegistry}.</p>
     */
    private boolean autoDiscovery = true;

    /**
     * Whether to validate required capabilities on startup.
     *
     * <p>When enabled, startup process checks if all capabilities in {@link #required} list are registered.
     * If any required capability is not registered, an exception will be thrown to prevent startup.</p>
     */
    private boolean validateOnStartup = true;

    /**
     * List of required capability types.
     *
     * <p>Lists fully qualified names of capability types required for application startup.
     * If these capabilities are not registered and {@link #validateOnStartup} is true, startup will fail.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * required:
     *   - io.runtime.sdk.capability.EventBusCapability
     *   - io.runtime.sdk.capability.StateStoreCapability
     * }</pre>
     */
    private List<String> required = new ArrayList<>();

    /**
     * List of optional capability types.
     *
     * <p>Lists fully qualified names of capability types that are optionally used.
     * If these capabilities are not registered, it will not affect startup.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * optional:
     *   - io.runtime.sdk.capability.LockCapability
     *   - io.runtime.sdk.capability.SchedulingCapability
     * }</pre>
     */
    private List<String> optional = new ArrayList<>();

    /**
     * List of disabled capability types.
     *
     * <p>Lists fully qualified names of capability types to disable.
     * Even if corresponding adapter beans exist, these capabilities will not be registered.</p>
     *
     * <p>Use cases:</p>
     * <ul>
     *   <li>Disable certain capabilities in test environments</li>
     *   <li>Specific deployment scenarios don't need certain capabilities</li>
     *   <li>Temporarily disable problematic capability implementations</li>
     * </ul>
     *
     * <p>Example:</p>
     * <pre>{@code
     * disabled:
     *   - io.runtime.sdk.capability.ResilienceCapability
     * }</pre>
     */
    private List<String> disabled = new ArrayList<>();

    // ==================== Getters & Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isAutoDiscovery() {
        return autoDiscovery;
    }

    public void setAutoDiscovery(boolean autoDiscovery) {
        this.autoDiscovery = autoDiscovery;
    }

    public boolean isValidateOnStartup() {
        return validateOnStartup;
    }

    public void setValidateOnStartup(boolean validateOnStartup) {
        this.validateOnStartup = validateOnStartup;
    }

    public List<String> getRequired() {
        return required;
    }

    public void setRequired(List<String> required) {
        this.required = required;
    }

    public List<String> getOptional() {
        return optional;
    }

    public void setOptional(List<String> optional) {
        this.optional = optional;
    }

    public List<String> getDisabled() {
        return disabled;
    }

    public void setDisabled(List<String> disabled) {
        this.disabled = disabled;
    }
}
