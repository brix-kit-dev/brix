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
package io.runtime.manifest.model;

/**
 * Event Subscribe Configuration.
 *
 * <p>Declares events subscribed by the module and their handlers for declarative event binding.</p>
 *
 * <p>Extracted from ModuleManifest.java as part of v3.2 architecture refactoring
 * to keep each file under 500 lines per code quality guidelines.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see EventsConfig
 */
public class EventSubscribeConfig {

    /**
     * Event type (fully qualified class name).
     */
    private String type;

    /**
     * Handler method (fully qualified class name.method name).
     */
    private String handler;

    /**
     * Retry configuration.
     */
    private RetryConfig retry;

    /**
     * Whether idempotent handling is required.
     */
    private boolean idempotent = true;

    /**
     * Whether subscription is optional (no error if event doesn't exist).
     */
    private boolean optional = false;

    // ==================== Getters and Setters ====================

    public String getType() { 
        return type; 
    }
    
    public void setType(String type) { 
        this.type = type; 
    }
    
    public String getHandler() { 
        return handler; 
    }
    
    public void setHandler(String handler) { 
        this.handler = handler; 
    }
    
    public RetryConfig getRetry() { 
        return retry; 
    }
    
    public void setRetry(RetryConfig retry) { 
        this.retry = retry; 
    }
    
    public boolean isIdempotent() { 
        return idempotent; 
    }
    
    public void setIdempotent(boolean idempotent) { 
        this.idempotent = idempotent; 
    }
    
    public boolean isOptional() { 
        return optional; 
    }
    
    public void setOptional(boolean optional) { 
        this.optional = optional; 
    }

    // ==================== Convenience Methods ====================

    /**
     * Gets handler class name.
     *
     * @return Fully qualified handler class name
     */
    public String getHandlerClass() {
        if (handler == null || !handler.contains(".")) {
            return null;
        }
        int lastDot = handler.lastIndexOf('.');
        return handler.substring(0, lastDot);
    }

    /**
     * Gets handler method name.
     *
     * @return Handler method name
     */
    public String getHandlerMethod() {
        if (handler == null || !handler.contains(".")) {
            return handler;
        }
        int lastDot = handler.lastIndexOf('.');
        return handler.substring(lastDot + 1);
    }

    @Override
    public String toString() {
        return "EventSubscribeConfig{type='" + type + "', handler='" + handler + "'}";
    }
}
