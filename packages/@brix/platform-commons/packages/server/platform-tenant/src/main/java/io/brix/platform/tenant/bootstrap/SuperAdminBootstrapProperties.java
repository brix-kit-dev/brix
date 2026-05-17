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
 * Configuration properties that drive the first-time {@code SUPER_ADMIN}
 * bootstrap performed by {@link SuperAdminBootstrapRunner}.
 *
 * <h3>Architectural Position</h3>
 * <p>Layer 2C (platform-tenant). Hosts (Layer 3) only contribute the values
 * via YAML / environment variables; they never instantiate or extend this
 * class. This honours the Host-Ultra-Thin constraint (constraint&nbsp;6).</p>
 *
 * <h3>Security</h3>
 * <ul>
 *   <li>Credentials must be supplied via {@link ConfigStoreCapability}-backed
 *       configuration (env vars, secret manager, mounted secret file). They
 *       must <b>never</b> be hard-coded into source or committed YAML.</li>
 *   <li>Bootstrap is idempotent and runs only when no active SUPER_ADMIN
 *       exists.</li>
 *   <li>The created identity is flagged for forced password change on first
 *       login and MFA enrolment, per blueprint security red-line.</li>
 * </ul>
 *
 * <h3>Properties</h3>
 * <ul>
 *   <li>{@code brix.platform.bootstrap.super-admin.enabled} (default {@code false})</li>
 *   <li>{@code brix.platform.bootstrap.super-admin.email} (required when enabled)</li>
 *   <li>{@code brix.platform.bootstrap.super-admin.password} (required when enabled)</li>
 *   <li>{@code brix.platform.bootstrap.super-admin.username} (default {@code "Super Admin"})</li>
 *   <li>{@code brix.platform.bootstrap.super-admin.require-mfa} (default {@code true})</li>
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

    /** Plaintext password supplied via secret store; hashed before persistence. */
    private String password;

    /** Display name applied to the bootstrap identity. */
    private String username = "Super Admin";

    /**
     * Whether the created PlatformAdmin record requires MFA. Default {@code true}
     * to comply with the security red-line; only flip to {@code false} for
     * isolated CI runs that have no MFA service.
     */
    private boolean requireMfa = true;

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isRequireMfa() {
        return requireMfa;
    }

    public void setRequireMfa(boolean requireMfa) {
        this.requireMfa = requireMfa;
    }
}
