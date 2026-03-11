/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration center properties.
 *
 * @since 3.0.0
 */
@ConfigurationProperties(prefix = "platform.config")
public class ConfigProperties {

    /**
     * Whether to enable the configuration center.
     */
    private boolean enabled = true;

    /**
     * Configuration refresh interval (milliseconds).
     */
    private long refreshInterval = 30000;

    /**
     * Whether to enable configuration encryption.
     */
    private boolean encryptEnabled = false;

    /**
     * Encryption key (only effective when encryptEnabled=true).
     */
    private String encryptKey;

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(long refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    public boolean isEncryptEnabled() {
        return encryptEnabled;
    }

    public void setEncryptEnabled(boolean encryptEnabled) {
        this.encryptEnabled = encryptEnabled;
    }

    public String getEncryptKey() {
        return encryptKey;
    }

    public void setEncryptKey(String encryptKey) {
        this.encryptKey = encryptKey;
    }
}
