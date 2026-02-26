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
package io.runtime.orchestrator.lifecycle;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * 能力缺失异常
 * 
 * <p>当模块所需的必需能力（required capability）在 Host 中不存在时抛出。
 * 此异常会导致模块启动失败，属于严重错误，需要检查 Host 配置或模块依赖声明。</p>
 * 
 * <h3>触发场景</h3>
 * <ul>
 *   <li>模块声明需要 event-bus 能力，但 Host 未提供 EventBusCapability 实现</li>
 *   <li>模块声明需要 scheduling 能力（在 required 中），但 Host 未提供 SchedulingCapability 实现</li>
 * </ul>
 * 
 * <h3>处理建议</h3>
 * <ul>
 *   <li>检查 Host 是否正确注册了所有必需能力的实现</li>
 *   <li>检查模块的 module-manifest.yaml 中的 capabilities.required 配置是否正确</li>
 *   <li>如果能力不是必需的，考虑将其移到 capabilities.optional 中</li>
 * </ul>
 * 
 * <h3>示例</h3>
 * <pre>{@code
 * // 模块 manifest 中声明需要 scheduling 能力
 * capabilities:
 *   required:
 *     - scheduling  # 如果 Host 未提供，将抛出此异常
 *     
 * // 捕获异常
 * try {
 *     lifecycleManager.initialize(moduleId);
 * } catch (CapabilityMissingException e) {
 *     logger.error("模块 {} 启动失败：缺少能力 {}", 
 *         e.getModuleId(), e.getMissingCapabilities());
 *     // 记录审计日志...
 * }
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ModuleLifecycleManager
 */
public class CapabilityMissingException extends ModuleLifecycleException {

    private static final long serialVersionUID = 1L;

    /**
     * 缺失的能力集合
     */
    private final Set<String> missingCapabilities;

    /**
     * 创建能力缺失异常（单个能力）
     * 
     * @param moduleId   模块 ID
     * @param capability 缺失的能力标识
     */
    public CapabilityMissingException(String moduleId, String capability) {
        this(moduleId, Collections.singleton(capability));
    }

    /**
     * 创建能力缺失异常（多个能力）
     * 
     * @param moduleId     模块 ID
     * @param capabilities 缺失的能力集合
     */
    public CapabilityMissingException(String moduleId, Collection<String> capabilities) {
        super(moduleId, LifecyclePhase.INIT, buildMessage(moduleId, capabilities));
        this.missingCapabilities = Set.copyOf(capabilities);
    }

    /**
     * 创建能力缺失异常（带原因）
     * 
     * @param moduleId     模块 ID
     * @param capabilities 缺失的能力集合
     * @param cause        原因异常
     */
    public CapabilityMissingException(String moduleId, Collection<String> capabilities, Throwable cause) {
        super(moduleId, LifecyclePhase.INIT, cause);
        this.missingCapabilities = Set.copyOf(capabilities);
    }

    /**
     * 获取缺失的能力集合
     * 
     * @return 不可变的缺失能力集合
     */
    public Set<String> getMissingCapabilities() {
        return missingCapabilities;
    }

    /**
     * 检查是否缺少指定能力
     * 
     * @param capability 能力标识
     * @return 如果该能力缺失返回 true
     */
    public boolean isMissing(String capability) {
        return missingCapabilities.contains(capability);
    }

    /**
     * 构建错误消息
     */
    private static String buildMessage(String moduleId, Collection<String> capabilities) {
        return String.format(
            "模块 [%s] 需要以下能力但 Host 未提供: %s。" +
            "请检查 Host 配置或将这些能力移到 capabilities.optional 中。",
            moduleId, capabilities
        );
    }
}
