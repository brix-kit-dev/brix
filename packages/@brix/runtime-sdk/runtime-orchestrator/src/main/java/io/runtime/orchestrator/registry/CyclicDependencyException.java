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
 * Cyclic Dependency Exception.
 * 
 * <p>Thrown when cyclic dependencies exist between modules. Cyclic dependencies cause
 * topological sorting to fail, and the system cannot determine the correct module startup order.</p>
 * 
 * <h3>Example</h3>
 * <pre>{@code
 * // Cyclic dependency scenario
 * // module-a depends on module-b
 * // module-b depends on module-c  
 * // module-c depends on module-a  <- forms a cycle
 * 
 * // Exception will contain cycle path information
 * // cycle: [module-a, module-b, module-c, module-a]
 * }</pre>
 * 
 * <h3>Solutions</h3>
 * <ul>
 *   <li>Redesign module boundaries to break cyclic dependencies</li>
 *   <li>Use events to decouple direct dependencies between modules</li>
 *   <li>Extract common parts to a separate module</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class CyclicDependencyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Cyclic dependency path.
     */
    private final List<String> cyclePath;

    /**
     * Creates cyclic dependency exception.
     * 
     * @param cyclePath cyclic dependency path, e.g., [A, B, C, A]
     */
    public CyclicDependencyException(List<String> cyclePath) {
        super(buildMessage(cyclePath));
        this.cyclePath = List.copyOf(cyclePath);
    }

    /**
     * Creates cyclic dependency exception (with cause).
     * 
     * @param cyclePath cyclic dependency path
     * @param cause     cause exception
     */
    public CyclicDependencyException(List<String> cyclePath, Throwable cause) {
        super(buildMessage(cyclePath), cause);
        this.cyclePath = List.copyOf(cyclePath);
    }

    /**
     * Gets cyclic dependency path.
     * 
     * @return immutable cycle path list
     */
    public List<String> getCyclePath() {
        return cyclePath;
    }

    /**
     * Gets the starting module of the cycle.
     * 
     * @return cycle starting module ID
     */
    public String getCycleStart() {
        return cyclePath.isEmpty() ? null : cyclePath.get(0);
    }

    /**
     * Builds error message.
     */
    private static String buildMessage(List<String> cyclePath) {
        return "Detected cyclic module dependency, cannot determine startup order. Cycle path: " + String.join(" -> ", cyclePath);
    }
}
