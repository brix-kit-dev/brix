/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Spring Environment Config Store adapter.
 *
 * <p>These properties control the behavior of the
 * {@link SpringEnvironmentConfigStoreCapability} auto-configuration.</p>
 *
 * <h3>YAML Example</h3>
 * <pre>{@code
 * brix:
 *   infra:
 *     config:
 *       enabled: true                    # Enable this adapter (default: true)
 *       log-resolved-keys: false         # Log each resolved key at DEBUG level
 *       health-indicator-enabled: true   # Expose config health in /actuator/health
 * }</pre>
 *
 * @author Brix Team
 * @version 3.1.0
 * @since 3.1.0
 */
@ConfigurationProperties(prefix = "brix.infra.config")
public class ConfigAdapterProperties {

    /**
     * Whether the Spring Environment config store adapter is enabled.
     * When {@code false}, the fallback adapter (system properties + env vars only) is used.
     */
    private boolean enabled = true;

    /**
     * Whether to log each resolved configuration key at DEBUG level.
     * Useful for troubleshooting configuration resolution issues during development.
     * Should be disabled in production to avoid leaking sensitive values in logs.
     */
    private boolean logResolvedKeys = false;

    /**
     * Whether to expose a config health indicator under {@code /actuator/health}.
     * Reports the number of active property sources and active Spring profiles.
     */
    private boolean healthIndicatorEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLogResolvedKeys() {
        return logResolvedKeys;
    }

    public void setLogResolvedKeys(boolean logResolvedKeys) {
        this.logResolvedKeys = logResolvedKeys;
    }

    public boolean isHealthIndicatorEnabled() {
        return healthIndicatorEnabled;
    }

    public void setHealthIndicatorEnabled(boolean healthIndicatorEnabled) {
        this.healthIndicatorEnabled = healthIndicatorEnabled;
    }
}
