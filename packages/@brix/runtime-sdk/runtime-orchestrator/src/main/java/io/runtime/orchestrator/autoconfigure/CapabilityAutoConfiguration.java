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
 * 能力扫描与组装自动配置
 *
 * <h2>架构定位（v3.0.4 架构红线修复）</h2>
 * <p>
 * 本类从 host-shell-standalone 的 {@code StandaloneShellAutoConfiguration} 和
 * host-shell-embedded 的 {@code EmbeddedShellAutoConfiguration} 中提取出的公共能力扫描逻辑。
 * 遵循 <b>Host 极薄化</b> 原则：Host 层只负责组合配置和 Import，不包含业务逻辑。
 * </p>
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li><b>能力自动发现</b>：扫描所有带有 {@link Capability @Capability} 注解的 Bean</li>
 *   <li><b>能力注册</b>：将发现的能力注册到 {@link CapabilityRegistry}</li>
 *   <li><b>能力过滤</b>：根据配置过滤禁用的能力</li>
 *   <li><b>必需能力验证</b>：启动时验证必需能力是否已注册</li>
 *   <li><b>RuntimeContext 创建</b>：组装 {@link RegistryDrivenRuntimeContext}</li>
 * </ul>
 *
 * <h2>配置示例</h2>
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
 * <h2>Host 层使用方式</h2>
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
    "io.infra.adapter.kafka.autoconfigure.KafkaAdapterAutoConfiguration",
    "io.infra.adapter.redis.autoconfigure.RedisAdapterAutoConfiguration"
})
@ConditionalOnClass(RuntimeContext.class)
@ConditionalOnProperty(prefix = "brix.capability", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CapabilityProperties.class)
public class CapabilityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CapabilityAutoConfiguration.class);

    private final CapabilityProperties properties;

    /**
     * 构造能力自动配置
     *
     * @param properties 能力配置属性
     */
    public CapabilityAutoConfiguration(CapabilityProperties properties) {
        this.properties = properties;
        log.info("初始化 CapabilityAutoConfiguration: autoDiscovery={}, validateOnStartup={}",
                properties.isAutoDiscovery(), properties.isValidateOnStartup());
    }

    /**
     * 创建能力注册表 Bean
     *
     * <p>扫描所有带有 @Capability 注解的 Bean，根据配置过滤和注册能力。</p>
     *
     * <h3>扫描流程</h3>
     * <ol>
     *   <li>获取所有带有 @Capability 注解的 Bean</li>
     *   <li>过滤掉 disabled 列表中的能力</li>
     *   <li>注册到 DefaultCapabilityRegistry</li>
     *   <li>通过类型匹配补充注册 required/optional 列表中的能力</li>
     *   <li>验证必需能力是否全部注册</li>
     *   <li>冻结注册表</li>
     * </ol>
     *
     * @param applicationContext Spring 应用上下文
     * @param observabilityCapabilities 可观测性能力提供者（用于强制实例化）
     * @return 能力注册表
     */
    @Bean
    @ConditionalOnMissingBean(CapabilityRegistry.class)
    public CapabilityRegistry capabilityRegistry(
            ApplicationContext applicationContext,
            ObjectProvider<List<ObservabilityCapability>> observabilityCapabilities) {
        log.info("创建能力注册表...");

        // 强制实例化所有 ObservabilityCapability beans，确保它们在扫描前已被创建
        List<ObservabilityCapability> obsCapabilities = observabilityCapabilities.getIfAvailable();
        if (obsCapabilities != null && !obsCapabilities.isEmpty()) {
            log.debug("已强制实例化 {} 个 ObservabilityCapability Bean", obsCapabilities.size());
        }

        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        Set<String> disabledCapabilities = new HashSet<>(properties.getDisabled());

        // 自动发现并注册带有 @Capability 注解的 Bean
        if (properties.isAutoDiscovery()) {
            scanAndRegisterAnnotatedCapabilities(applicationContext, registry, disabledCapabilities);
        }

        // 通过类型匹配注册 required/optional 列表中的能力
        registerCapabilitiesByType(applicationContext, registry, disabledCapabilities);

        // 验证必需能力是否全部注册
        if (properties.isValidateOnStartup()) {
            validateRequiredCapabilities(registry);
        }

        // 冻结注册表，防止运行时篡改
        registry.freeze();
        log.info("能力注册表已冻结，共注册 {} 个能力", registry.getAllDescriptors().size());

        return registry;
    }

    /**
     * 创建模块注册表 Bean
     *
     * <p>扫描所有实现 {@link io.runtime.sdk.capability.LifecycleCapability} 的 Bean，
     * 自动注册到 {@link ModuleRegistry}。这是插件动态发现的基础。</p>
     *
     * @param applicationContext Spring 应用上下文
     * @return 模块注册表
     */
    @Bean
    @ConditionalOnMissingBean(ModuleRegistry.class)
    public ModuleRegistry moduleRegistry(ApplicationContext applicationContext) {
        log.info("创建模块注册表...");

        DefaultModuleRegistry registry = new DefaultModuleRegistry();

        // 扫描所有 LifecycleCapability Bean 并注册
        Map<String, io.runtime.sdk.capability.LifecycleCapability> modules =
                applicationContext.getBeansOfType(io.runtime.sdk.capability.LifecycleCapability.class);

        log.info("发现 {} 个 LifecycleCapability 模块", modules.size());

        for (io.runtime.sdk.capability.LifecycleCapability module : modules.values()) {
            registry.register(module);
            log.debug("注册模块: {}", module.getMetadata().getModuleId());
        }

        log.info("模块注册表创建完成，共注册 {} 个模块", registry.size());
        return registry;
    }

    /**
     * 创建模块生命周期管理器 Bean
     *
     * <p>【v3.1.0 新增】负责管理所有模块的生命周期，包括初始化、启动、健康检查和停止。
     * 遵循 v3.0.4 蓝图 LifecycleCapability 规范，实现完整的插件生命周期管理。</p>
     *
     * <h3>生命周期阶段</h3>
     * <ol>
     *   <li>INIT - 初始化阶段（能力验证、依赖检查）</li>
     *   <li>START - 启动阶段（按依赖顺序启动）</li>
     *   <li>RUNNING - 运行阶段（定期健康检查）</li>
     *   <li>STOP - 停止阶段（按逆序优雅停止）</li>
     * </ol>
     *
     * @param moduleRegistry 模块注册表
     * @param runtimeContext 运行时上下文（用于构建上下文工厂）
     * @return 模块生命周期管理器
     */
    @Bean
    @ConditionalOnMissingBean(ModuleLifecycleManager.class)
    public ModuleLifecycleManager moduleLifecycleManager(ModuleRegistry moduleRegistry, RuntimeContext runtimeContext) {
        log.info("创建模块生命周期管理器...");

        // 创建基于 RuntimeContext 的上下文工厂
        DefaultModuleLifecycleManager.RuntimeContextFactory contextFactory = moduleId -> {
            // 对于每个模块，创建独立的运行时上下文
            // 在实际场景中，可以为每个模块创建隔离的上下文
            return runtimeContext;
        };

        DefaultModuleLifecycleManager lifecycleManager = new DefaultModuleLifecycleManager(
                moduleRegistry, contextFactory);

        log.info("模块生命周期管理器创建完成，准备管理 {} 个模块的生命周期", moduleRegistry.size());
        return lifecycleManager;
    }

    /**
     * 创建 RuntimeContext Bean
     *
     * <p>使用统一的 {@link RegistryDrivenRuntimeContext} 实现，
     * 所有能力访问均通过 {@link CapabilityRegistry} 委托。</p>
     *
     * @param capabilityRegistry 能力注册表
     * @return 运行时上下文
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(RuntimeContext.class)
    public RuntimeContext runtimeContext(CapabilityRegistry capabilityRegistry) {
        log.info("创建 RegistryDrivenRuntimeContext: moduleId={}, tenantId={}",
                properties.getModuleId(), properties.getTenantId());

        RegistryDrivenRuntimeContext context = new RegistryDrivenRuntimeContext(
                capabilityRegistry,
                properties.getModuleId(),
                properties.getTenantId()
        );

        log.info("RegistryDrivenRuntimeContext 创建完成: {}", context);
        return context;
    }

    /**
     * 创建 RegistryDrivenRuntimeContext 类型的 Bean（向后兼容）
     *
     * <p>某些场景可能需要直接注入 {@link RegistryDrivenRuntimeContext} 类型，
     * 本方法提供类型安全的转换。</p>
     *
     * @param runtimeContext 运行时上下文
     * @return RegistryDrivenRuntimeContext 实例
     * @throws IllegalStateException 如果 runtimeContext 不是 RegistryDrivenRuntimeContext 类型
     */
    @Bean
    @ConditionalOnMissingBean(RegistryDrivenRuntimeContext.class)
    public RegistryDrivenRuntimeContext registryDrivenRuntimeContext(RuntimeContext runtimeContext) {
        if (runtimeContext instanceof RegistryDrivenRuntimeContext registryDriven) {
            return registryDriven;
        }
        throw new IllegalStateException("RuntimeContext 不是 RegistryDrivenRuntimeContext 类型");
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 扫描并注册带有 @Capability 注解的 Bean
     *
     * @param ctx Spring 应用上下文
     * @param registry 能力注册表
     * @param disabledCapabilities 禁用的能力类型名称集合
     */
    private void scanAndRegisterAnnotatedCapabilities(ApplicationContext ctx,
                                                       DefaultCapabilityRegistry registry,
                                                       Set<String> disabledCapabilities) {
        Map<String, Object> capabilityBeans = ctx.getBeansWithAnnotation(Capability.class);
        log.info("发现 {} 个带有 @Capability 注解的 Bean", capabilityBeans.size());

        for (Map.Entry<String, Object> entry : capabilityBeans.entrySet()) {
            Object bean = entry.getValue();
            Capability annotation = bean.getClass().getAnnotation(Capability.class);

            if (annotation != null) {
                CapabilityDescriptor descriptor = CapabilityDescriptor.fromAnnotation(annotation, bean.getClass());
                String typeName = descriptor.getType().getName();

                // 检查是否被禁用
                if (disabledCapabilities.contains(typeName)) {
                    log.info("跳过禁用的能力: {}", typeName);
                    continue;
                }

                @SuppressWarnings("unchecked")
                Class<Object> capabilityType = (Class<Object>) descriptor.getType();
                registry.register(capabilityType, bean, descriptor);
                log.debug("注册能力: {} -> {}", typeName, bean.getClass().getSimpleName());
            }
        }
    }

    /**
     * 通过类型匹配注册能力
     *
     * <p>遍历 required 和 optional 列表中的能力类型，
     * 如果尚未注册且存在对应的 Bean，则进行注册。</p>
     *
     * @param ctx Spring 应用上下文
     * @param registry 能力注册表
     * @param disabledCapabilities 禁用的能力类型名称集合
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

                // 如果已注册，跳过
                if (registry.isAvailable(capabilityType)) {
                    continue;
                }

                // 尝试从 Spring 上下文获取 Bean
                Map<String, ?> beans = ctx.getBeansOfType(capabilityType);
                if (!beans.isEmpty()) {
                    Object bean = beans.values().iterator().next();
                    @SuppressWarnings("unchecked")
                    Class<Object> type = (Class<Object>) capabilityType;
                    registry.registerIfAbsent(type, bean);
                    log.debug("通过类型匹配注册能力: {} -> {}", typeName, bean.getClass().getSimpleName());
                }
            } catch (ClassNotFoundException e) {
                log.warn("能力类型未找到: {}", typeName);
            }
        }
    }

    /**
     * 验证必需能力是否全部注册
     *
     * <p>遍历 required 列表，检查每个能力是否已注册。
     * 如果有必需能力未注册，抛出 {@link IllegalStateException}。</p>
     *
     * @param registry 能力注册表
     * @throws IllegalStateException 如果有必需能力未注册
     */
    private void validateRequiredCapabilities(DefaultCapabilityRegistry registry) {
        for (String typeName : properties.getRequired()) {
            try {
                Class<?> capabilityType = Class.forName(typeName);
                if (!registry.isAvailable(capabilityType)) {
                    throw new IllegalStateException(
                            "必需能力未注册: " + typeName +
                                    "。请确保提供该能力的适配器已添加到依赖中，" +
                                    "或在配置中将其移至 optional 列表。");
                }
            } catch (ClassNotFoundException e) {
                log.warn("必需能力类型未找到（可能是配置错误）: {}", typeName);
            }
        }
        log.info("必需能力验证通过");
    }
}
