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

/**
 * Module Not Found Exception.
 * 
 * <p>Thrown when the requested module does not exist in the registry.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class ModuleNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Module ID.
     */
    private final String moduleId;

    /**
     * Creates module not found exception.
     * 
     * @param moduleId module ID
     */
    public ModuleNotFoundException(String moduleId) {
        super("Module not found: " + moduleId);
        this.moduleId = moduleId;
    }

    /**
     * Gets module ID.
     * 
     * @return module ID
     */
    public String getModuleId() {
        return moduleId;
    }
}
