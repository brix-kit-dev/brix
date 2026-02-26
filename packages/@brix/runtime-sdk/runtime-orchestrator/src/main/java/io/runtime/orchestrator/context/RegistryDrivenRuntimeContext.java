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
 * 基于注册表驱动的运行时上下文实现
 *
 * <p>这是 {@link RuntimeContext} 的标准实现，所有能力通过 {@link CapabilityRegistry} 动态获取。
 * 本类从原 host-shell-standalone 的 {@code StandaloneShellContext} 和
 * host-shell-embedded 的 {@code EmbeddedShellContext} 合并而来，
 * 统一了两种 Host 模式的 RuntimeContext 实现。</p>
 *
 * <h2>架构定位（v3.0 运行壳架构蓝图）</h2>
 * <p>
 * 本类属于 <b>runtime-orchestrator</b>（编排层），实现 runtime-sdk-api（契约层）定义的
 * {@link RuntimeContext} 接口。蓝图明确要求契约层（Layer 2）仅包含纯接口定义，
 * 而实现类应位于编排层。Standalone 和 Embedded Host 共享同一个 Context 实现，
 * 通过 Host 层的 AutoConfiguration 注入不同的能力组合。
 * </p>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><b>注册表驱动</b>：所有能力通过 CapabilityRegistry 获取，不在 Context 中硬编码字段</li>
 *   <li><b>声明式组装</b>：能力由 Host 层的 AutoConfiguration 决定注入什么</li>
 *   <li><b>向后兼容</b>：{@link RuntimeContext} 接口的 default 方法提供快捷访问</li>
 *   <li><b>Host 无关</b>：Standalone 和 Embedded 共享同一个 Context 实现</li>
 * </ul>
 *
 * <h2>与旧实现的区别</h2>
 * <table>
 *   <tr><th>维度</th><th>旧 EmbeddedShellContext</th><th>旧 StandaloneShellContext</th><th>本类</th></tr>
 *   <tr><td>能力获取</td><td>硬编码字段</td><td>注册表驱动</td><td>注册表驱动</td></tr>
 *   <tr><td>注册表类型</td><td>EmbeddedCapabilityRegistry（非标准）</td><td>StandaloneCapabilityRegistry</td><td>DefaultCapabilityRegistry</td></tr>
 *   <tr><td>Builder</td><td>有</td><td>无</td><td>无（注册逻辑在 Host AutoConfiguration 中）</td></tr>
 * </table>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // Host 层 AutoConfiguration 中创建
 * DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
 * registry.register(EventBusCapability.class, kafkaEventBus);
 * registry.freeze();
 *
 * RuntimeContext context = new RegistryDrivenRuntimeContext(registry, "platform", "default");
 *
 * // 插件层使用
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

    /** 能力注册表（运行时只读） */
    private final CapabilityRegistry registry;

    /** 模块标识符 */
    private final String moduleId;

    /** 租户标识符 */
    private final String tenantId;

    /**
     * 构造基于注册表驱动的运行时上下文
     *
     * @param registry 能力注册表（通常已冻结）
     * @param moduleId 模块唯一标识
     * @param tenantId 租户标识，null 时默认为 "default"
     * @throws NullPointerException registry 或 moduleId 为 null 时
     */
    public RegistryDrivenRuntimeContext(CapabilityRegistry registry,
                                         String moduleId,
                                         String tenantId) {
        this.registry = Objects.requireNonNull(registry, "能力注册表不能为空");
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId 不能为空");
        this.tenantId = tenantId != null ? tenantId : "default";

        log.info("创建 RegistryDrivenRuntimeContext: moduleId={}, tenantId={}, capabilities={}",
                moduleId, this.tenantId, registry.size());
    }

    // ==================== RuntimeContext 接口实现 ====================

    /**
     * 获取能力注册表
     *
     * <p>通过注册表可以获取任意已注册的能力、查询元数据、检查可用性。
     * 这是获取所有能力的核心入口。</p>
     *
     * @return 能力注册表实例
     */
    @Override
    public CapabilityRegistry getCapabilityRegistry() {
        return registry;
    }

    /**
     * 获取租户标识符
     *
     * @return 租户 ID
     */
    @Override
    public String getTenantId() {
        return tenantId;
    }

    /**
     * 获取模块标识符
     *
     * @return 模块 ID
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
