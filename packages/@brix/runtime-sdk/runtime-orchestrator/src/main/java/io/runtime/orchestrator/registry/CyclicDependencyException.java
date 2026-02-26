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
package io.runtime.orchestrator.registry;

import java.util.List;

/**
 * 循环依赖异常
 * 
 * <p>当模块之间存在循环依赖时抛出此异常。循环依赖会导致拓扑排序失败，
 * 系统无法确定正确的模块启动顺序。</p>
 * 
 * <h3>示例</h3>
 * <pre>{@code
 * // 循环依赖场景
 * // module-a 依赖 module-b
 * // module-b 依赖 module-c  
 * // module-c 依赖 module-a  <- 形成循环
 * 
 * // 异常将包含循环路径信息
 * // cycle: [module-a, module-b, module-c, module-a]
 * }</pre>
 * 
 * <h3>解决方案</h3>
 * <ul>
 *   <li>重新设计模块边界，打破循环依赖</li>
 *   <li>使用事件解耦模块间的直接依赖</li>
 *   <li>将公共部分抽取到独立模块</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class CyclicDependencyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 循环依赖路径
     */
    private final List<String> cyclePath;

    /**
     * 创建循环依赖异常
     * 
     * @param cyclePath 循环依赖路径，如 [A, B, C, A]
     */
    public CyclicDependencyException(List<String> cyclePath) {
        super(buildMessage(cyclePath));
        this.cyclePath = List.copyOf(cyclePath);
    }

    /**
     * 创建循环依赖异常（带原因）
     * 
     * @param cyclePath 循环依赖路径
     * @param cause     原因异常
     */
    public CyclicDependencyException(List<String> cyclePath, Throwable cause) {
        super(buildMessage(cyclePath), cause);
        this.cyclePath = List.copyOf(cyclePath);
    }

    /**
     * 获取循环依赖路径
     * 
     * @return 不可变的循环路径列表
     */
    public List<String> getCyclePath() {
        return cyclePath;
    }

    /**
     * 获取循环起始模块
     * 
     * @return 循环起始模块 ID
     */
    public String getCycleStart() {
        return cyclePath.isEmpty() ? null : cyclePath.get(0);
    }

    /**
     * 构建错误消息
     */
    private static String buildMessage(List<String> cyclePath) {
        return "检测到模块循环依赖，无法确定启动顺序。循环路径: " + String.join(" -> ", cyclePath);
    }
}
