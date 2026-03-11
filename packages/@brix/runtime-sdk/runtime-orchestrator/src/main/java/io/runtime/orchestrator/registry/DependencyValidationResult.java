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
 * Dependency Validation Result.
 * 
 * <p>Contains detailed result information from module dependency validation.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class DependencyValidationResult {

    /**
     * List of missing dependencies.
     */
    private final List<String> missingDependencies;

    /**
     * List of circular dependencies.
     */
    private final List<String> circularDependencies;

    /**
     * Creates dependency validation result.
     * 
     * @param missingDependencies list of missing dependencies
     * @param circularDependencies list of circular dependencies
     */
    public DependencyValidationResult(List<String> missingDependencies, 
                                       List<String> circularDependencies) {
        this.missingDependencies = Collections.unmodifiableList(missingDependencies);
        this.circularDependencies = Collections.unmodifiableList(circularDependencies);
    }

    /**
     * Checks if validation passed.
     * 
     * @return true if no dependency issues
     */
    public boolean isValid() {
        return missingDependencies.isEmpty() && circularDependencies.isEmpty();
    }

    /**
     * Gets list of missing dependencies.
     * 
     * @return list of missing dependencies
     */
    public List<String> getMissingDependencies() {
        return missingDependencies;
    }

    /**
     * Gets list of circular dependencies.
     * 
     * @return list of circular dependencies
     */
    public List<String> getCircularDependencies() {
        return circularDependencies;
    }

    /**
     * Gets error message.
     * 
     * @return message describing all dependency issues
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
