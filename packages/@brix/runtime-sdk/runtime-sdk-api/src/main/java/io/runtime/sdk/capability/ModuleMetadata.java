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
package io.runtime.sdk.capability;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Module Metadata
 * 
 * <p>Describes basic module information for registration, monitoring, and management.
 * Metadata is typically loaded from the module-manifest.yaml file.</p>
 * 
 * <h3>Core Fields</h3>
 * <ul>
 *   <li><b>moduleId</b>: Unique module identifier</li>
 *   <li><b>moduleName</b>: Display name</li>
 *   <li><b>version</b>: Semantic version number</li>
 *   <li><b>description</b>: Module description</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * ModuleMetadata metadata = ModuleMetadata.builder()
 *     .moduleId("brix-app-booking")
 *     .moduleName("Booking Management")
 *     .version("3.0.0")
 *     .description("Provides booking creation, query, and cancellation features")
 *     .build();
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see LifecycleCapability#getMetadata()
 */
public final class ModuleMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique module identifier
     */
    private final String moduleId;

    /**
     * Module display name
     */
    private final String moduleName;

    /**
     * Module version (semantic versioning)
     */
    private final String version;

    /**
     * Module description
     */
    private final String description;

    /**
     * Startup order (lower numbers start first)
     */
    private final int startupOrder;

    /**
     * List of dependent module IDs
     */
    private final String[] dependsOn;

    /**
     * Extension properties
     */
    private final Map<String, Object> attributes;

    /**
     * Registration time
     */
    private final Instant registeredAt;

    /**
     * Private constructor, create via Builder
     */
    private ModuleMetadata(Builder builder) {
        this.moduleId = Objects.requireNonNull(builder.moduleId, "moduleId cannot be null");
        this.moduleName = Objects.requireNonNull(builder.moduleName, "moduleName cannot be null");
        this.version = Objects.requireNonNull(builder.version, "version cannot be null");
        this.description = builder.description;
        this.startupOrder = builder.startupOrder;
        this.dependsOn = builder.dependsOn != null ? builder.dependsOn.clone() : new String[0];
        this.attributes = builder.attributes != null 
            ? Collections.unmodifiableMap(builder.attributes) 
            : Collections.emptyMap();
        this.registeredAt = Instant.now();
    }

    // ==================== Getter Methods ====================

    /**
     * Get module ID
     * 
     * @return unique module identifier
     */
    public String getModuleId() {
        return moduleId;
    }

    /**
     * Get module name
     * 
     * @return module display name
     */
    public String getModuleName() {
        return moduleName;
    }

    /**
     * Get module version
     * 
     * @return semantic version number
     */
    public String getVersion() {
        return version;
    }

    /**
     * Get module description
     * 
     * @return module description, may be null
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get startup order
     * 
     * @return startup priority, default 100
     */
    public int getStartupOrder() {
        return startupOrder;
    }

    /**
     * Get dependent module list
     * 
     * @return array of dependent module IDs
     */
    public String[] getDependsOn() {
        return dependsOn.clone();
    }

    /**
     * Get extension properties
     * 
     * @return immutable property map
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Get registration time
     * 
     * @return module registration time
     */
    public Instant getRegisteredAt() {
        return registeredAt;
    }

    /**
     * Get extension property value
     * 
     * @param key  property key
     * @param type value type
     * @param <T>  type parameter
     * @return property value, returns null if not present
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    // ==================== Builder ====================

    /**
     * Create Builder
     * 
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * ModuleMetadata Builder
     */
    public static class Builder {
        private String moduleId;
        private String moduleName;
        private String version;
        private String description;
        private int startupOrder = 100;
        private String[] dependsOn;
        private Map<String, Object> attributes;

        public Builder moduleId(String moduleId) {
            this.moduleId = moduleId;
            return this;
        }

        public Builder moduleName(String moduleName) {
            this.moduleName = moduleName;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder startupOrder(int startupOrder) {
            this.startupOrder = startupOrder;
            return this;
        }

        public Builder dependsOn(String... dependsOn) {
            this.dependsOn = dependsOn;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        public ModuleMetadata build() {
            return new ModuleMetadata(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModuleMetadata that = (ModuleMetadata) o;
        return Objects.equals(moduleId, that.moduleId) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleId, version);
    }

    @Override
    public String toString() {
        return String.format("ModuleMetadata[id=%s, name=%s, version=%s]", moduleId, moduleName, version);
    }
}
