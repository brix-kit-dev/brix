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
package io.runtime.sdk.support;

/**
 * Module Initialization Exception
 * 
 * <p>Thrown when a module encounters an error during initialization phase (onInit).</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class ModuleInitializationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Module ID
     */
    private final String moduleId;

    /**
     * Create module initialization exception
     * 
     * @param moduleId module ID
     * @param cause    cause exception
     */
    public ModuleInitializationException(String moduleId, Throwable cause) {
        super("Failed to initialize module: " + moduleId, cause);
        this.moduleId = moduleId;
    }

    /**
     * Create module initialization exception
     * 
     * @param moduleId module ID
     * @param message  exception message
     */
    public ModuleInitializationException(String moduleId, String message) {
        super("Failed to initialize module [" + moduleId + "]: " + message);
        this.moduleId = moduleId;
    }

    /**
     * Get module ID
     * 
     * @return module ID
     */
    public String getModuleId() {
        return moduleId;
    }
}
