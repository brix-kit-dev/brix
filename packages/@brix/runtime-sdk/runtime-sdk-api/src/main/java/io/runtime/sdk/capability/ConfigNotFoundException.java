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
package io.runtime.sdk.capability;

/**
 * Configuration Not Found Exception
 * 
 * <p>Thrown when attempting to get a required configuration but the configuration does not exist.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ConfigStoreCapability#getRequired(String, Class)
 */
public class ConfigNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Configuration key
     */
    private final String configKey;

    /**
     * Creates a configuration not found exception
     * 
     * @param message the exception message
     */
    public ConfigNotFoundException(String message) {
        super(message);
        this.configKey = extractKeyFromMessage(message);
    }

    /**
     * Creates a configuration not found exception
     * 
     * @param configKey the configuration key
     * @param message   the exception message
     */
    public ConfigNotFoundException(String configKey, String message) {
        super(message);
        this.configKey = configKey;
    }

    /**
     * Gets the configuration key
     * 
     * @return the configuration key
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * Extracts the configuration key from the message
     */
    private static String extractKeyFromMessage(String message) {
        if (message != null && message.contains(":")) {
            return message.substring(message.lastIndexOf(":") + 1).trim();
        }
        return null;
    }
}
