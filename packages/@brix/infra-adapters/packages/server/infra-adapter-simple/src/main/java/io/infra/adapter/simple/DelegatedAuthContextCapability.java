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
package io.infra.adapter.simple;

import java.security.Principal;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.infra.adapter.simple.auth.DelegatedAuthConfig;
import io.infra.adapter.simple.auth.DelegatedPrincipal;
import io.infra.adapter.simple.auth.TokenIntrospectionService;
import io.runtime.sdk.capability.AuthContextCapability;
import io.runtime.sdk.capability.DataScope;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * Delegated Authentication Context Capability Implementation
 * 
 * <p>Implements authentication capability for embedded deployment mode,
 * delegating authentication to customer's SSO system via OAuth 2.0 
 * Token Introspection.</p>
 * 
 * <h3>Key Features</h3>
 * <ul>
 *   <li><b>Token Delegation</b>: Validates Bearer Token via customer SSO</li>
 *   <li><b>Caching</b>: Local cache for validation results</li>
 *   <li><b>Context Propagation</b>: ThreadLocal-based token storage</li>
 * </ul>
 * 
 * <h3>Architecture Note (架构说明)</h3>
 * <p>Token validation logic extracted to {@link TokenIntrospectionService}
 * following Single Responsibility Principle. This class focuses solely on
 * implementing the AuthContextCapability interface.</p>
 * 
 * <p>【中文技术要点】
 * 委托认证能力实现，将 Token 验证逻辑提取到 TokenIntrospectionService，
 * 本类仅负责实现 AuthContextCapability 接口和管理请求上下文。</p>
 *
 * @author Brix Platform Authors
 * @since 3.0.0
 * @see AuthContextCapability
 * @see TokenIntrospectionService
 */
@Capability(
    type = AuthContextCapability.class,
    name = "delegated-auth-context",
    description = "OAuth 2.0 Token Introspection based delegated authentication",
    level = CapabilityLevel.STANDARD,
    aliases = {"delegatedAuth"}
)
public class DelegatedAuthContextCapability implements AuthContextCapability {

    private static final Logger log = LoggerFactory.getLogger(DelegatedAuthContextCapability.class);

    private final TokenIntrospectionService tokenService;

    /** Current request token holder (ThreadLocal) */
    private static final ThreadLocal<String> CURRENT_TOKEN = new ThreadLocal<>();

    /** Current request principal holder (ThreadLocal) */
    private static final ThreadLocal<DelegatedPrincipal> CURRENT_PRINCIPAL = new ThreadLocal<>();

    /**
     * Creates a delegated authentication context.
     *
     * @param config Configuration parameters
     */
    public DelegatedAuthContextCapability(DelegatedAuthConfig config) {
        Objects.requireNonNull(config, "config cannot be null");
        
        this.tokenService = new TokenIntrospectionService(
                config.getTokenValidationUrl(),
                config.getClientId(),
                config.getClientSecret(),
                config.getCacheTtl()
        );

        log.info("DelegatedAuthContextCapability initialized");
    }

    // ==================== Static Methods: Context Management ====================

    /**
     * Sets the current request token.
     * 
     * <p>Called by Filter at request start to set Bearer Token.</p>
     *
     * @param token Bearer Token (without "Bearer " prefix)
     */
    public static void setCurrentToken(String token) {
        CURRENT_TOKEN.set(token);
        CURRENT_PRINCIPAL.remove();
        log.debug("Current token set: {}...", 
                token != null ? token.substring(0, Math.min(10, token.length())) : "null");
    }

    /**
     * Clears current request context.
     * 
     * <p>Called by Filter at request end to clean up ThreadLocal.</p>
     */
    public static void clearContext() {
        CURRENT_TOKEN.remove();
        CURRENT_PRINCIPAL.remove();
        log.debug("Authentication context cleared");
    }

    /**
     * Gets the current token.
     *
     * @return Current request token, may be null
     */
    public static String getCurrentToken() {
        return CURRENT_TOKEN.get();
    }

    // ==================== AuthContextCapability Implementation ====================

    @Override
    public Principal getCurrentPrincipal() {
        // Check cached principal
        DelegatedPrincipal cached = CURRENT_PRINCIPAL.get();
        if (cached != null) {
            return cached;
        }

        // Get current token
        String token = CURRENT_TOKEN.get();
        if (token == null || token.isEmpty()) {
            log.debug("No token in current request");
            return null;
        }

        // Validate token
        DelegatedPrincipal principal = tokenService.validateToken(token);
        if (principal != null) {
            CURRENT_PRINCIPAL.set(principal);
        }

        return principal;
    }

    @Override
    public boolean hasPermission(String permission) {
        Objects.requireNonNull(permission, "permission cannot be null");

        DelegatedPrincipal principal = (DelegatedPrincipal) getCurrentPrincipal();
        if (principal == null) {
            return false;
        }

        boolean has = principal.getPermissions().contains(permission);
        log.debug("Permission check: permission={}, result={}", permission, has);
        return has;
    }

    @Override
    public boolean hasRole(String role) {
        Objects.requireNonNull(role, "role cannot be null");

        DelegatedPrincipal principal = (DelegatedPrincipal) getCurrentPrincipal();
        if (principal == null) {
            return false;
        }

        boolean has = principal.getRoles().contains(role);
        log.debug("Role check: role={}, result={}", role, has);
        return has;
    }

    @Override
    public Set<DataScope> getAuthorizedScopes() {
        DelegatedPrincipal principal = (DelegatedPrincipal) getCurrentPrincipal();
        if (principal == null) {
            return Collections.emptySet();
        }
        return principal.getDataScopes();
    }

    /**
     * Gets the token introspection service.
     * 
     * <p>Useful for cache management operations.</p>
     *
     * @return TokenIntrospectionService instance
     */
    public TokenIntrospectionService getTokenService() {
        return tokenService;
    }
}
