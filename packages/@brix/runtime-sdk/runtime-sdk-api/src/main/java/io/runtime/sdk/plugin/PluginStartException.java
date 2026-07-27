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
package io.runtime.sdk.plugin;

/**
 * Unchecked exception thrown when a plugin fails to start.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public class PluginStartException extends RuntimeException {

    private final PluginIdentity pluginIdentity;

    /**
     * Creates a plugin start exception.
     *
     * @param pluginIdentity plugin identity
     * @param message failure message
     */
    public PluginStartException(PluginIdentity pluginIdentity, String message) {
        super(message);
        this.pluginIdentity = pluginIdentity;
    }

    /**
     * Creates a plugin start exception.
     *
     * @param pluginIdentity plugin identity
     * @param message failure message
     * @param cause failure cause
     */
    public PluginStartException(PluginIdentity pluginIdentity, String message, Throwable cause) {
        super(message, cause);
        this.pluginIdentity = pluginIdentity;
    }

    /**
     * Returns the plugin identity associated with the failure.
     *
     * @return plugin identity
     */
    public PluginIdentity pluginIdentity() {
        return pluginIdentity;
    }
}
