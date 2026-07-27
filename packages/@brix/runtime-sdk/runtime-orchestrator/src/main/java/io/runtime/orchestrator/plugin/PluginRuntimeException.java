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
package io.runtime.orchestrator.plugin;

/**
 * Runtime Shell plugin lifecycle exception.
 *
 * <p>This exception is thrown by L2B runtime orchestration when discovery,
 * manifest resolution, capability resolution, wiring, startup, or shutdown
 * violates the v3.0.10 Runtime Shell contract.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public class PluginRuntimeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message failure message
     */
    public PluginRuntimeException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and cause.
     *
     * @param message failure message
     * @param cause root cause
     */
    public PluginRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
