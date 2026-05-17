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
package io.brix.platform.admin.controller;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.brix.platform.admin.dto.PlatformLoginRequest;
import io.brix.platform.admin.dto.PlatformLoginResponse;
import io.brix.platform.auth.AuditAction;
import io.brix.platform.auth.PlatformPermissions;
import io.brix.platform.auth.annotation.Anonymous;
import io.brix.platform.auth.annotation.RequirePermission;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.service.AuditService;
import io.runtime.sdk.capability.AuthContextCapability;
import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import io.runtime.sdk.capability.AuthFlowCapability.LoginCommand;
import io.runtime.sdk.capability.AuthFlowCapability.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Platform admin authentication endpoints.
 *
 * <h3>Route Prefix</h3>
 * <p>{@code /api/platform/auth} — distinct from tenant auth at {@code /api/auth}.
 *
 * <h3>Login Delegation</h3>
 * <p>{@link AuthFlowCapability#login} already handles the platform admin routing
 * path internally (SSOT §3 — no reimplementation needed here).
 *
 * <h3>Audit</h3>
 * <p>Both login success and failure are audited. Failed logins use
 * {@code createdBy = null} because the identity may not be known.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@RestController
@RequestMapping("/api/platform/auth")
public class PlatformAuthController {

    private static final Logger log = LoggerFactory.getLogger(PlatformAuthController.class);

    private final AuthFlowCapability authFlow;
    private final AuthContextCapability authContext;
    private final AuditService auditService;

    public PlatformAuthController(
            AuthFlowCapability authFlow,
            AuthContextCapability authContext,
            AuditService auditService) {
        this.authFlow = authFlow;
        this.authContext = authContext;
        this.auditService = auditService;
    }

    /**
     * Platform admin login.
     *
     * <p>Delegates to {@link AuthFlowCapability#login}. The capability implementation
     * returns {@code platformAdminMode = true} and a token with {@code scope=PLATFORM}
     * and {@code platform_role} claim when the identity is an active platform admin.
     *
     * @param request   login credentials
     * @param httpRequest raw HTTP request for client-IP extraction
     * @return login result DTO (status + tokens + permissions)
     */
    @Anonymous
    @PostMapping("/login")
    public ResponseEntity<PlatformLoginResponse> login(
            @Valid @RequestBody PlatformLoginRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = extractClientIp(httpRequest);

        try {
            LoginResult result = authFlow.login(new LoginCommand(request.loginId(), request.password(), clientIp));

            // Enforce that only platform admin logins are served on this endpoint
            if (!result.platformAdminMode()) {
                // Identity exists but is not a platform admin — return 401 to avoid info disclosure
                log.warn("[PlatformAuth] Non-platform-admin login attempt for loginId={}", request.loginId());
                auditService.log(AuditEvent.builder()
                        .action(AuditAction.SUPER_ADMIN_LOGIN_FAILED)
                        .resourceType("PLATFORM_AUTH")
                        .description("Login rejected: identity is not a platform admin.")
                        .clientIp(clientIp)
                        .success(false)
                        .build());
                throw new AuthFlowException(AuthFlowException.CODE_INVALID_CREDENTIALS,
                        "Invalid credentials.");
            }

            // Audit success
            auditService.log(AuditEvent.builder()
                    .createdBy(result.identityId())
                    .action(AuditAction.SUPER_ADMIN_LOGIN_SUCCESS)
                    .resourceType("PLATFORM_AUTH")
                    .resourceId(result.identityId() != null ? result.identityId().toString() : null)
                    .description("Platform admin login succeeded.")
                    .clientIp(clientIp)
                    .success(true)
                    .build());

            PlatformLoginResponse response = new PlatformLoginResponse(
                    result.status().name(),
                    result.accessToken(),
                    result.refreshToken(),
                    result.expiresIn() != null ? result.expiresIn() : 0L,
                    result.primaryRole(),
                    result.permissions(),
                        result.identityId(),
                        result.email(),
                        result.displayName(),
                    result.mustChangePassword(),
                    result.identityToken()
            );
            return ResponseEntity.ok(response);

        } catch (AuthFlowException ex) {
            // Audit failure (don't re-audit if we already did above)
            if (!AuditAction.SUPER_ADMIN_LOGIN_FAILED.equals(ex.getMessage())) {
                auditService.log(AuditEvent.builder()
                        .action(AuditAction.SUPER_ADMIN_LOGIN_FAILED)
                        .resourceType("PLATFORM_AUTH")
                        .description("Platform admin login failed. Code: " + ex.getErrorCode())
                        .clientIp(clientIp)
                        .success(false)
                        .errorCode(ex.getErrorCode())
                        .build());
            }
            throw ex; // re-throw; handled by AuthFlowExceptionAdvice
        }
    }

    /**
     * Platform admin logout.
     *
     * <p>The JWT blacklist / token version invalidation approach means the client
     * simply discards the token. Server-side, we audit the event. For token version
     * invalidation, clients should call {@code /api/auth/change-password} or the
     * admin can be disabled.
     *
     * @return 204 No Content
     */
    @RequirePermission(PlatformPermissions.BYPASS_PERMISSION_CHECK)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        Long identityId = resolveIdentityId();
        String clientIp = extractClientIp(httpRequest);

        auditService.log(AuditEvent.builder()
                .createdBy(identityId)
                .action(AuditAction.SUPER_ADMIN_LOGOUT)
                .resourceType("PLATFORM_AUTH")
                .resourceId(identityId != null ? identityId.toString() : null)
                .description("Platform admin logged out.")
                .clientIp(clientIp)
                .success(true)
                .build());

        log.info("[PlatformAuth] Platform admin logout: identityId={}", identityId);
        return ResponseEntity.noContent().build();
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    private Long resolveIdentityId() {
        Principal principal = authContext.getCurrentPrincipal();
        if (principal == null || principal.getName() == null) {
            return null;
        }
        try {
            return Long.parseLong(principal.getName());
        } catch (NumberFormatException e) {
            log.warn("[PlatformAuth] Non-numeric principal: {}", principal.getName());
            return null;
        }
    }

    private static String extractClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }
}
