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
package io.runtime.sdk.capability.registry;

import java.util.Objects;
import java.util.Set;

/**
 * Capability Descriptor
 * 
 * <p>Contains capability metadata information for runtime querying and observability.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public final class CapabilityDescriptor {

    private final Class<?> type;
    private final String name;
    private final String description;
    private final CapabilityLevel level;
    private final int priority;
    private final boolean required;
    private final Set<String> aliases;
    private final String implementationClass;
    private final String providerModule;

    private CapabilityDescriptor(Builder builder) {
        this.type = Objects.requireNonNull(builder.type, "type cannot be null");
        this.name = builder.name != null ? builder.name : type.getSimpleName();
        this.description = builder.description != null ? builder.description : "";
        this.level = builder.level != null ? builder.level : CapabilityLevel.STANDARD;
        this.priority = builder.priority;
        this.required = builder.required;
        this.aliases = builder.aliases != null ? Set.copyOf(builder.aliases) : Set.of();
        this.implementationClass = builder.implementationClass;
        this.providerModule = builder.providerModule;
    }

    // ==================== Getters ====================

    /**
     * Get capability interface type
     * 
     * @return capability interface type
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * Get capability name
     * 
     * @return capability name
     */
    public String getName() {
        return name;
    }

    /**
     * Get capability description
     * 
     * @return capability description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get capability level
     * 
     * @return capability level
     */
    public CapabilityLevel getLevel() {
        return level;
    }

    /**
     * Get priority
     * 
     * @return priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Whether capability is required
     * 
     * @return whether required
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Get aliases
     * 
     * @return alias set
     */
    public Set<String> getAliases() {
        return aliases;
    }

    /**
     * Get implementation class name
     * 
     * @return fully qualified implementation class name
     */
    public String getImplementationClass() {
        return implementationClass;
    }

    /**
     * Get provider module
     * 
     * @return provider module name
     */
    public String getProviderModule() {
        return providerModule;
    }

    // ==================== Builder ====================

    /**
     * Create builder
     * 
     * @param type capability interface type
     * @return builder instance
     */
    public static Builder builder(Class<?> type) {
        return new Builder(type);
    }

    /**
     * Create descriptor from @Capability annotation
     * 
     * @param annotation          the annotation instance
     * @param implementationClass the implementation class
     * @return the descriptor instance
     */
    public static CapabilityDescriptor fromAnnotation(Capability annotation, Class<?> implementationClass) {
        Class<?> type = annotation.type() != Void.class ? annotation.type() : inferCapabilityType(implementationClass);
        
        return builder(type)
                .name(annotation.name().isEmpty() ? type.getSimpleName() : annotation.name())
                .description(annotation.description())
                .level(annotation.level())
                .priority(annotation.priority())
                .required(annotation.required())
                .aliases(Set.of(annotation.aliases()))
                .implementationClass(implementationClass.getName())
                .build();
    }

    /**
     * Infer capability type
     */
    private static Class<?> inferCapabilityType(Class<?> implementationClass) {
        for (Class<?> iface : implementationClass.getInterfaces()) {
            if (iface.getSimpleName().endsWith("Capability")) {
                return iface;
            }
        }
        // Check parent class interfaces
        Class<?> superclass = implementationClass.getSuperclass();
        while (superclass != null && superclass != Object.class) {
            for (Class<?> iface : superclass.getInterfaces()) {
                if (iface.getSimpleName().endsWith("Capability")) {
                    return iface;
                }
            }
            superclass = superclass.getSuperclass();
        }
        throw new IllegalArgumentException("Cannot infer capability type, please specify type in @Capability annotation: " + implementationClass.getName());
    }

    /**
     * Descriptor builder
     */
    public static class Builder {
        private final Class<?> type;
        private String name;
        private String description;
        private CapabilityLevel level;
        private int priority;
        private boolean required;
        private Set<String> aliases;
        private String implementationClass;
        private String providerModule;

        private Builder(Class<?> type) {
            this.type = type;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder level(CapabilityLevel level) {
            this.level = level;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder aliases(Set<String> aliases) {
            this.aliases = aliases;
            return this;
        }

        public Builder implementationClass(String implementationClass) {
            this.implementationClass = implementationClass;
            return this;
        }

        public Builder providerModule(String providerModule) {
            this.providerModule = providerModule;
            return this;
        }

        public CapabilityDescriptor build() {
            return new CapabilityDescriptor(this);
        }
    }

    @Override
    public String toString() {
        return "CapabilityDescriptor{" +
                "type=" + type.getSimpleName() +
                ", name='" + name + '\'' +
                ", level=" + level +
                ", priority=" + priority +
                ", required=" + required +
                ", impl=" + implementationClass +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CapabilityDescriptor that = (CapabilityDescriptor) o;
        return Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }
}
