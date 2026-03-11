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
package io.runtime.orchestrator.event;

/**
 * Event Dispatch Exception.
 * 
 * <p>Thrown when an error occurs during event dispatch, typically due to:</p>
 * <ul>
 *   <li>Handler method invocation failed</li>
 *   <li>Handler threw exception</li>
 *   <li>Serialization/deserialization failed</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class EventDispatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates event dispatch exception.
     * 
     * @param message error message
     */
    public EventDispatchException(String message) {
        super(message);
    }

    /**
     * Creates event dispatch exception (with cause).
     * 
     * @param message error message
     * @param cause   cause exception
     */
    public EventDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
