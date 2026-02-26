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

import java.util.Collections;
import java.util.List;

/**
 * 依赖验证结果
 * 
 * <p>包含模块依赖验证的详细结果信息。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class DependencyValidationResult {

    /**
     * 缺失的依赖列表
     */
    private final List<String> missingDependencies;

    /**
     * 循环依赖列表
     */
    private final List<String> circularDependencies;

    /**
     * 创建依赖验证结果
     * 
     * @param missingDependencies 缺失依赖列表
     * @param circularDependencies 循环依赖列表
     */
    public DependencyValidationResult(List<String> missingDependencies, 
                                       List<String> circularDependencies) {
        this.missingDependencies = Collections.unmodifiableList(missingDependencies);
        this.circularDependencies = Collections.unmodifiableList(circularDependencies);
    }

    /**
     * 判断验证是否通过
     * 
     * @return 如果没有依赖问题返回 true
     */
    public boolean isValid() {
        return missingDependencies.isEmpty() && circularDependencies.isEmpty();
    }

    /**
     * 获取缺失依赖列表
     * 
     * @return 缺失依赖列表
     */
    public List<String> getMissingDependencies() {
        return missingDependencies;
    }

    /**
     * 获取循环依赖列表
     * 
     * @return 循环依赖列表
     */
    public List<String> getCircularDependencies() {
        return circularDependencies;
    }

    /**
     * 获取错误消息
     * 
     * @return 描述所有依赖问题的消息
     */
    public String getErrorMessage() {
        if (isValid()) {
            return "All dependencies are satisfied";
        }

        StringBuilder sb = new StringBuilder("Dependency validation failed:\n");
        
        if (!missingDependencies.isEmpty()) {
            sb.append("  Missing dependencies:\n");
            for (String missing : missingDependencies) {
                sb.append("    - ").append(missing).append("\n");
            }
        }
        
        if (!circularDependencies.isEmpty()) {
            sb.append("  Circular dependencies detected in:\n");
            for (String circular : circularDependencies) {
                sb.append("    - ").append(circular).append("\n");
            }
        }
        
        return sb.toString();
    }

    @Override
    public String toString() {
        return "DependencyValidationResult{" +
               "valid=" + isValid() +
               ", missingDependencies=" + missingDependencies +
               ", circularDependencies=" + circularDependencies +
               '}';
    }
}
