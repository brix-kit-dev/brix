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
package io.brix.platform.tenant.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.brix.platform.auth.PlatformPermissions;
import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.context.SecurityContextHolder;
import io.brix.platform.tenant.dto.AuditEvent;
import io.runtime.sdk.capability.IdentityTenantCapability;
import io.runtime.sdk.capability.IdentityTenantCapability.IdentityRecord;
import io.runtime.sdk.capability.IdentityTenantCapability.PlatformAdminRecord;
import io.runtime.sdk.capability.JwtIssuerCapability;
import io.runtime.sdk.capability.JwtIssuerCapability.PlatformAdminTokenRequest;
import io.runtime.sdk.capability.JwtIssuerCapability.PlatformAdminViewTokenRequest;
import io.runtime.sdk.capability.ViewModeCapability;
import io.runtime.sdk.capability.ViewModeResolutionException;
import io.runtime.sdk.capability.ViewModeSwitchDeniedException;

/**
 * Server-side implementation of the {@link ViewModeCapability} contract
 * (Phase 2 / C-4).
 *
 * <p>Bridges the Layer 2A capability contract to the platform-auth
 * infrastructure: derives the current view mode from
 * {@link SecurityContextHolder}, validates switch authority, asks
 * {@link JwtIssuerCapability} to mint a fresh JWT, and writes an audit
 * record via {@link AuditService}.</p>
 *
 * <h3>Authorization Rules</h3>
 * <ul>
 *   <li>Only platform admins may invoke {@link #switchTo(SwitchRequest)}.</li>
 *   <li>A session that is already in viewing mode (carries
 *       {@code original_sub}) is considered "platform admin" for this purpose
 *       and may switch back to {@link ViewMode#PLATFORM_ADMIN} or hop to a
 *       different tenant.</li>
 *   <li>The {@code original_sub} value is preserved across hops &mdash; the
 *       audit trail always points back to the platform admin who started the
 *       chain.</li>
 * </ul>
 *
 * <h3>Wire-format Mapping</h3>
 * <ul>
 *   <li>{@link ViewMode#PLATFORM_ADMIN} &rarr; standard platform-admin token
 *       (no {@code tenant_id}, no {@code original_sub}).</li>
 *   <li>{@link ViewMode#TENANT_ACTOR} / {@link ViewMode#TENANT_SUBJECT} &rarr;
 *       platform-admin viewing token (role retained, {@code tenant_id} +
 *       {@code original_sub} populated).</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>Stateless &mdash; safe to register as a singleton bean. Per-request
 * state is read from {@link SecurityContextHolder}'s ThreadLocal.</p>
 *
 * @author Brix Platform Team
 * @since 3.3.0
 * @see ViewModeCapability
 * @see JwtIssuerCapability
 * @see AuditService
 */
public class ViewModeCapabilityImpl implements ViewModeCapability {

    private static final Logger log = LoggerFactory.getLogger(ViewModeCapabilityImpl.class);

    /** Audit-log action code for view-mode switches. */
    private static final String AUDIT_ACTION_SWITCH = "VIEW_MODE_SWITCH";
    /** Audit-log resource-type code for view-mode entries. */
    private static final String AUDIT_RESOURCE_TYPE = "VIEW_MODE";
    /** MDC key populated by the web tracing filter / ViewMode controller. */
    private static final String TRACE_ID_MDC_KEY = "traceId";

    private final SecurityContextHolder securityContextHolder;
    private final JwtIssuerCapability jwtIssuerCapability;
    private final IdentityTenantCapability identityTenantCapability;
    private final AuditService auditService;

    /**
     * Constructs a ViewModeCapabilityImpl with the required dependencies.
     *
     * @param securityContextHolder    resolves the calling user (required)
     * @param jwtIssuerCapability      mints fresh JWTs (required)
     * @param identityTenantCapability looks up identity / platform-admin
     *                                 records (required)
     * @param auditService             writes the audit trail (required)
     */
    public ViewModeCapabilityImpl(
            SecurityContextHolder securityContextHolder,
            JwtIssuerCapability jwtIssuerCapability,
            IdentityTenantCapability identityTenantCapability,
            AuditService auditService) {
        if (securityContextHolder == null) {
            throw new IllegalArgumentException("securityContextHolder is required");
        }
        if (jwtIssuerCapability == null) {
            throw new IllegalArgumentException("jwtIssuerCapability is required");
        }
        if (identityTenantCapability == null) {
            throw new IllegalArgumentException("identityTenantCapability is required");
        }
        if (auditService == null) {
            throw new IllegalArgumentException("auditService is required");
        }
        this.securityContextHolder = securityContextHolder;
        this.jwtIssuerCapability = jwtIssuerCapability;
        this.identityTenantCapability = identityTenantCapability;
        this.auditService = auditService;
        log.info("ViewModeCapabilityImpl initialized");
    }

    // ========== Read-side ==========

    @Override
    public ViewMode getCurrent() {
        AuthenticatedUser caller = currentUserOrThrow();
        return resolveCurrentMode(caller);
    }

    @Override
    public Optional<String> getOriginalSub() {
        AuthenticatedUser caller = currentUserOrThrow();
        return Optional.ofNullable(caller.getOriginalSub());
    }

    // ========== Write-side ==========

    @Override
    public SwitchResult switchTo(SwitchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("SwitchRequest must not be null");
        }
        AuthenticatedUser caller = currentUserOrThrow();
        ViewMode previousMode = resolveCurrentMode(caller);
        String traceId = currentTraceId();
        if (!isAuthorizedSwitcher(caller)) {
            log.warn("[ViewMode] switch denied: traceId={}, caller is not a platform admin (sub={})",
                traceId, caller.getUserId());
            throw new ViewModeSwitchDeniedException(
                    "Only platform admins may switch view mode");
        }

        // Determine the platform-admin identity that originated the chain.
        String originalSub = caller.getOriginalSub() != null
                ? caller.getOriginalSub()
                : caller.getUserId();

        // Look up the platform-admin record so we have an authoritative
        // adminId / adminRole / permissions list, regardless of whether the
        // caller is currently in viewing mode (which may have masked some of
        // those JWT claims).
        Long originalIdentityId = parseLong(originalSub);
        IdentityRecord identity = identityTenantCapability.findIdentityById(originalIdentityId)
                .orElseThrow(() -> new ViewModeSwitchDeniedException(
                        "Originating identity not found: " + originalIdentityId));
        PlatformAdminRecord admin = identityTenantCapability.findActivePlatformAdmin(originalIdentityId)
                .orElseThrow(() -> new ViewModeSwitchDeniedException(
                        "Originating identity is not an active platform admin: "
                                + originalIdentityId));

        List<String> permissions = PlatformPermissions.defaultPermissionsFor(admin.adminRole());
        long expiresIn = jwtIssuerCapability.getAccessTokenExpirationSeconds();

        SwitchResult result;
        if (request.mode() == ViewMode.PLATFORM_ADMIN) {
            String token = jwtIssuerCapability.issuePlatformAdminToken(
                    new PlatformAdminTokenRequest(
                            admin.adminId(),
                            identity.id(),
                            identity.email(),
                            identity.username(),
                            admin.adminRole(),
                            permissions,
                            identity.tokenVersion()));
            result = new SwitchResult(token, expiresIn, ViewMode.PLATFORM_ADMIN, null, null);
        } else {
            String token = jwtIssuerCapability.issuePlatformAdminViewToken(
                    new PlatformAdminViewTokenRequest(
                            admin.adminId(),
                            identity.id(),
                            identity.email(),
                            identity.username(),
                            admin.adminRole(),
                            permissions,
                            identity.tokenVersion(),
                            request.tenantId(),
                            originalSub));
            result = new SwitchResult(
                    token, expiresIn, request.mode(), request.tenantId(), originalSub);
        }

        recordAudit(caller, request, originalSub, originalIdentityId, previousMode, traceId);
        log.info("[ViewMode] switch ok: traceId={}, originalSub={}, previous={}, target={}, tenantId={}",
            traceId, originalSub, previousMode, request.mode(), request.tenantId());
        return result;
    }

    // ========== Helpers ==========

    /**
     * Resolves the calling user, throwing the contract-defined exception when
     * no authenticated context is present.
     */
    private AuthenticatedUser currentUserOrThrow() {
        AuthenticatedUser user;
        try {
            user = securityContextHolder.requireCurrentUser();
        } catch (RuntimeException ex) {
            throw new ViewModeResolutionException(
                    "No authenticated user in current context", ex);
        }
        if (user == null) {
            throw new ViewModeResolutionException(
                    "No authenticated user in current context");
        }
        return user;
    }

    /**
     * Derives the {@link ViewMode} from the JWT shape carried by {@code user}.
     */
    private ViewMode resolveCurrentMode(AuthenticatedUser user) {
        if (user.isImpersonating() && user.getTenantId() != null) {
            // Wire format currently shared between actor / subject views;
            // until the contract is extended with a dedicated claim, expose
            // the more conservative TENANT_ACTOR perspective.
            return ViewMode.TENANT_ACTOR;
        }
        return ViewMode.PLATFORM_ADMIN;
    }

    /**
     * A caller may switch view mode iff they are currently a platform admin or
     * a platform admin who has already started a viewing session.
     */
    private boolean isAuthorizedSwitcher(AuthenticatedUser user) {
        return user.isPlatformAdmin() || user.isImpersonating();
    }

        private void recordAudit(
            AuthenticatedUser caller,
            SwitchRequest request,
            String originalSub,
            Long originalIdentityId,
            ViewMode previousMode,
            String traceId) {
        Long callerIdentity = parseNullableLong(caller.getUserId());
        AuditEvent event = AuditEvent.builder()
                .createdBy(callerIdentity)
                .tenantId(request.tenantId())
                .action(AUDIT_ACTION_SWITCH)
                .resourceType(AUDIT_RESOURCE_TYPE)
                .resourceId(request.mode().name())
            .requestId(traceId)
            .context(buildAuditContext(
                traceId,
                previousMode,
                request,
                originalSub,
                originalIdentityId,
                callerIdentity,
                caller.getTenantId()))
                .description("ViewMode switch by originalSub=" + originalSub
                        + " to mode=" + request.mode()
                        + (request.tenantId() != null ? ", tenantId=" + request.tenantId() : ""))
                .build();
        auditService.log(event);
    }

    /**
     * Parses an identity ID extracted from a JWT {@code sub} / {@code original_sub}
     * claim. Throws a switch-denied exception if the value is not a valid long.
     */
    private static Long parseLong(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            throw new ViewModeSwitchDeniedException(
                    "Invalid originating identity reference: " + raw, ex);
        }
    }

    /**
     * Lenient variant for audit-trail fields where a non-numeric (or null)
     * subject should not abort the operation.
     */
    private static Long parseNullableLong(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        return traceId != null && !traceId.isBlank() ? traceId : null;
    }

    private static String buildAuditContext(
            String traceId,
            ViewMode previousMode,
            SwitchRequest request,
            String originalSub,
            Long originalIdentityId,
            Long actorIdentityId,
            String previousTenantId) {
        return "{"
                + "\"traceId\":" + jsonString(traceId) + ","
                + "\"previousViewMode\":" + jsonString(previousMode.name()) + ","
                + "\"requestedViewMode\":" + jsonString(request.mode().name()) + ","
                + "\"previousTenantId\":" + jsonString(previousTenantId) + ","
                + "\"requestedTenantId\":" + jsonNumber(request.tenantId()) + ","
                + "\"originalSub\":" + jsonString(originalSub) + ","
                + "\"originalIdentityId\":" + jsonNumber(originalIdentityId) + ","
                + "\"actorIdentityId\":" + jsonNumber(actorIdentityId)
                + "}";
    }

    private static String jsonNumber(Long value) {
        return value == null ? "null" : value.toString();
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                + "\"";
    }
}
