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
package io.infra.adapter.fallback;

import java.security.Principal;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;

import io.runtime.sdk.capability.AuthContextCapability;
import io.runtime.sdk.capability.DataScope;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * Fallback authentication context capability implementation.
 *
 * <p>Provides anonymous access mode that allows all permissions.
 * This implementation is for <strong>development and testing environments only</strong>.</p>
 *
 * <h3>Security Warning</h3>
 * <p>This implementation grants unrestricted access to all permissions and roles.
 * <strong>NEVER enable this in production environments.</strong>
 * For production, use the actual authentication implementation from platform-auth.</p>
 *
 * <h3>Production Protection (v3.1.0)</h3>
 * <ul>
 *   <li>{@code @Profile("!production")} - Bean excluded when production profile is active</li>
 *   <li>{@code @ConditionalOnProperty} - Requires explicit opt-in via configuration</li>
 * </ul>
 *
 * <p><b>Technical Notes (Chinese):</b></p>
 * <pre>
 * [生产环境保护机制说明]
 * 此类通过双重保护确保不会在生产环境中启用:
 * 1. @Profile("!production") - 当 spring.profiles.active 包含 production 时，此 Bean 不会注册
 * 2. @ConditionalOnProperty - 需要显式设置 brix.fallback.auth.enabled=true 才会启用
 * </pre>
 *
 * @author Brix Team
 * @version 3.1.0
 * @since 3.0.0
 * @see AuthContextCapability
 */
@Profile("!production")
@ConditionalOnProperty(
    prefix = "brix.fallback.auth",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false
)
@Capability(
    type = AuthContextCapability.class,
    name = "fallback-auth-context",
    description = "Anonymous access fallback implementation, allows all permissions. FOR DEVELOPMENT ONLY.",
    level = CapabilityLevel.EXPERIMENTAL,
    aliases = {"fallbackAuth"}
)
public class FallbackAuthContextCapability implements AuthContextCapability {

    /**
     * Anonymous principal constant.
     *
     * <p>Technical Note: Provides fixed "anonymous" identity for development environment
     * permission bypass scenarios. Production must use real JWT/OAuth implementation
     * from platform-auth.</p>
     */
    private final Principal anonymousPrincipal = () -> "anonymous";

    /**
     * Returns the current authenticated principal.
     *
     * <p>In fallback mode, always returns an anonymous principal with name "anonymous".</p>
     *
     * @return the anonymous principal
     */
    @Override
    public Principal getCurrentPrincipal() {
        return anonymousPrincipal;
    }

    /**
     * Checks if the current principal has the specified permission.
     *
     * <p><strong>WARNING:</strong> Always returns {@code true} in fallback mode.
     * This is intentional for development convenience but represents a security risk.</p>
     *
     * @param permission the permission to check
     * @return always {@code true} in fallback mode
     */
    @Override
    public boolean hasPermission(String permission) {
        // WARNING: Allows all permissions - development environment only
        return true;
    }

    /**
     * Checks if the current principal has the specified role.
     *
     * <p><strong>WARNING:</strong> Always returns {@code true} in fallback mode.</p>
     *
     * @param role the role to check
     * @return always {@code true} in fallback mode
     */
    @Override
    public boolean hasRole(String role) {
        // WARNING: Allows all roles - development environment only
        return true;
    }

    /**
     * Returns the authorized data scopes for the current principal.
     *
     * <p>In fallback mode, returns {@link DataScope#all()} granting access to all data.</p>
     *
     * @return a set containing {@link DataScope#all()}
     */
    @Override
    public Set<DataScope> getAuthorizedScopes() {
        return Set.of(DataScope.all());
    }
}
