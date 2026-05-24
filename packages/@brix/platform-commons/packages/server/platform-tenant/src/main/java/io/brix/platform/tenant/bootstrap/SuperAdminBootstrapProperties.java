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
package io.brix.platform.tenant.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties that drive the first-time bootstrap anchor
 * performed by {@link SuperAdminBootstrapRunner}.
 *
 * <h3>Architectural Position</h3>
 * <p>Layer 2C (platform-tenant). Hosts (Layer 3) only contribute the values
 * via YAML / environment variables; they never instantiate or extend this
 * class. This honours the Host-Ultra-Thin constraint (constraint&nbsp;6).</p>
 *
 * <h3>Security</h3>
 * <p>The runner creates a passwordless {@code BOOTSTRAP} anchor in
 * {@code PENDING_SETUP}. It never accepts or stores a bootstrap password. The
 * short-lived setup code is used only to open a dedicated bootstrap session.</p>
 *
 * <h3>Properties</h3>
 * <ul>
 *   <li>{@code brix.platform.bootstrap.super-admin.enabled} (default {@code false})</li>
 *   <li>{@code brix.platform.bootstrap.super-admin.email} (required when enabled)</li>
 *   <li>{@code brix.platform.bootstrap.super-admin.username} (default {@code "Super Admin"})</li>
 *   <li>{@code brix.platform.bootstrap.super-admin.setup-code} (required when enabled)</li>
 *   <li>{@code brix.platform.bootstrap.super-admin.setup-code-ttl-seconds} (default {@code 900})</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@ConfigurationProperties(prefix = "brix.platform.bootstrap.super-admin")
public class SuperAdminBootstrapProperties {

    /** Whether the bootstrap runner is active. Disabled by default. */
    private boolean enabled = false;

    /** Globally unique email used as the bootstrap identity login. */
    private String email;

    /** Display name applied to the bootstrap identity. */
    private String username = "Bootstrap Setup";

    /** One-time setup code used to open the dedicated bootstrap setup session. */
    private String setupCode;

    /** Setup-code TTL in seconds. */
    private long setupCodeTtlSeconds = 900L;

    /** Bootstrap setup JWT session TTL in seconds. */
    private long bootstrapSessionTtlSeconds = 300L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSetupCode() {
        return setupCode;
    }

    public void setSetupCode(String setupCode) {
        this.setupCode = setupCode;
    }

    public long getSetupCodeTtlSeconds() {
        return setupCodeTtlSeconds;
    }

    public void setSetupCodeTtlSeconds(long setupCodeTtlSeconds) {
        this.setupCodeTtlSeconds = setupCodeTtlSeconds;
    }

    public long getBootstrapSessionTtlSeconds() {
        return bootstrapSessionTtlSeconds;
    }

    public void setBootstrapSessionTtlSeconds(long bootstrapSessionTtlSeconds) {
        this.bootstrapSessionTtlSeconds = bootstrapSessionTtlSeconds;
    }
}
