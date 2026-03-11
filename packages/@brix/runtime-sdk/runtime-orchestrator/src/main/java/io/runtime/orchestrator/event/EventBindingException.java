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
 * Event Binding Exception.
 * 
 * <p>Thrown when event handler binding fails, typically due to:</p>
 * <ul>
 *   <li>Handler class does not exist</li>
 *   <li>Handler method does not exist or signature mismatch</li>
 *   <li>Unable to obtain handler instance</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class EventBindingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates event binding exception.
     * 
     * @param message error message
     */
    public EventBindingException(String message) {
        super(message);
    }

    /**
     * Creates event binding exception (with cause).
     * 
     * @param message error message
     * @param cause   cause exception
     */
    public EventBindingException(String message, Throwable cause) {
        super(message, cause);
    }
}
