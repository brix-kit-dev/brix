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
 * 能力扫描与组装配置属性
 *
 * <h2>架构定位（v3.0.4 架构红线修复）</h2>
 * <p>
 * 本类定义能力扫描的配置属性，从原 Host 层的 {@code StandaloneShellProperties.CapabilitiesConfig}
 * 提取并增强。所有 Host 模式（Standalone/Embedded）共享此配置结构。
 * </p>
 *
 * <h2>配置示例</h2>
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
 * <h2>配置优先级</h2>
 * <p>
 * Host 层的特定配置（如 {@code brix.shell.standalone.capabilities}）
 * 可以通过 Spring Boot 的配置绑定覆盖此默认配置。
 * </p>
 *
 * @author Brix Platform Authors
 * @since 3.0.4
 * @see CapabilityAutoConfiguration
 */
@ConfigurationProperties(prefix = "brix.capability")
public class CapabilityProperties {

    /**
     * 是否启用能力自动配置
     *
     * <p>设置为 false 将完全禁用 {@link CapabilityAutoConfiguration}。</p>
     */
    private boolean enabled = true;

    /**
     * 模块 ID
     *
     * <p>用于标识当前运行模块，在 {@link io.runtime.sdk.context.RuntimeContext} 中使用。
     * 建议与 Maven artifactId 保持一致。</p>
     */
    private String moduleId = "default-module";

    /**
     * 租户 ID
     *
     * <p>多租户场景下的默认租户标识。
     * 运行时可通过 TenantContext 动态切换。</p>
     */
    private String tenantId = "default";

    /**
     * 是否启用能力自动发现
     *
     * <p>启用时，自动扫描所有带有 {@link io.runtime.sdk.capability.registry.Capability @Capability}
     * 注解的 Bean 并注册到 {@link io.runtime.sdk.capability.registry.CapabilityRegistry}。</p>
     */
    private boolean autoDiscovery = true;

    /**
     * 是否在启动时验证必需能力
     *
     * <p>启用时，启动过程会检查 {@link #required} 列表中的所有能力是否已注册。
     * 如果有必需能力未注册，将抛出异常阻止启动。</p>
     */
    private boolean validateOnStartup = true;

    /**
     * 必需能力类型列表
     *
     * <p>列出应用启动所必需的能力类型全限定名。
     * 如果这些能力未注册，且 {@link #validateOnStartup} 为 true，启动将失败。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * required:
     *   - io.runtime.sdk.capability.EventBusCapability
     *   - io.runtime.sdk.capability.StateStoreCapability
     * }</pre>
     */
    private List<String> required = new ArrayList<>();

    /**
     * 可选能力类型列表
     *
     * <p>列出应用可选使用的能力类型全限定名。
     * 如果这些能力未注册，不会影响启动。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * optional:
     *   - io.runtime.sdk.capability.LockCapability
     *   - io.runtime.sdk.capability.SchedulingCapability
     * }</pre>
     */
    private List<String> optional = new ArrayList<>();

    /**
     * 禁用能力类型列表
     *
     * <p>列出需要禁用的能力类型全限定名。
     * 即使存在对应的适配器 Bean，这些能力也不会被注册。</p>
     *
     * <p>使用场景：</p>
     * <ul>
     *   <li>测试环境中禁用某些能力</li>
     *   <li>特定部署场景不需要某些能力</li>
     *   <li>临时禁用有问题的能力实现</li>
     * </ul>
     *
     * <p>示例：</p>
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
