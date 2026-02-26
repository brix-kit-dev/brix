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
 * 能力描述符
 * 
 * <p>包含能力的元数据信息，用于运行时查询和可观测性。</p>
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
        this.type = Objects.requireNonNull(builder.type, "type 不能为空");
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
     * 获取能力接口类型
     * 
     * @return 能力接口类型
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * 获取能力名称
     * 
     * @return 能力名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取能力描述
     * 
     * @return 能力描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取能力级别
     * 
     * @return 能力级别
     */
    public CapabilityLevel getLevel() {
        return level;
    }

    /**
     * 获取优先级
     * 
     * @return 优先级
     */
    public int getPriority() {
        return priority;
    }

    /**
     * 是否为必需能力
     * 
     * @return 是否必需
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * 获取别名集合
     * 
     * @return 别名集合
     */
    public Set<String> getAliases() {
        return aliases;
    }

    /**
     * 获取实现类名
     * 
     * @return 实现类全限定名
     */
    public String getImplementationClass() {
        return implementationClass;
    }

    /**
     * 获取提供者模块
     * 
     * @return 提供者模块名称
     */
    public String getProviderModule() {
        return providerModule;
    }

    // ==================== Builder ====================

    /**
     * 创建构建器
     * 
     * @param type 能力接口类型
     * @return 构建器实例
     */
    public static Builder builder(Class<?> type) {
        return new Builder(type);
    }

    /**
     * 从 @Capability 注解创建描述符
     * 
     * @param annotation 注解实例
     * @param implementationClass 实现类
     * @return 描述符实例
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
     * 推断能力类型
     */
    private static Class<?> inferCapabilityType(Class<?> implementationClass) {
        for (Class<?> iface : implementationClass.getInterfaces()) {
            if (iface.getSimpleName().endsWith("Capability")) {
                return iface;
            }
        }
        // 检查父类接口
        Class<?> superclass = implementationClass.getSuperclass();
        while (superclass != null && superclass != Object.class) {
            for (Class<?> iface : superclass.getInterfaces()) {
                if (iface.getSimpleName().endsWith("Capability")) {
                    return iface;
                }
            }
            superclass = superclass.getSuperclass();
        }
        throw new IllegalArgumentException("无法推断能力类型，请在 @Capability 注解中明确指定 type: " + implementationClass.getName());
    }

    /**
     * 描述符构建器
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
