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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.context.SecurityContextHolder;
import io.brix.platform.tenant.dto.AuditEvent;
import io.runtime.sdk.capability.IdentityTenantCapability;
import io.runtime.sdk.capability.IdentityTenantCapability.IdentityRecord;
import io.runtime.sdk.capability.IdentityTenantCapability.PlatformAdminRecord;
import io.runtime.sdk.capability.JwtIssuerCapability;
import io.runtime.sdk.capability.JwtIssuerCapability.PlatformAdminTokenRequest;
import io.runtime.sdk.capability.JwtIssuerCapability.PlatformAdminViewTokenRequest;
import io.runtime.sdk.capability.ViewModeCapability.SwitchRequest;
import io.runtime.sdk.capability.ViewModeCapability.SwitchResult;
import io.runtime.sdk.capability.ViewModeCapability.ViewMode;
import io.runtime.sdk.capability.ViewModeResolutionException;
import io.runtime.sdk.capability.ViewModeSwitchDeniedException;

/**
 * Unit tests for {@link ViewModeCapabilityImpl} (Phase 2 / C-4).
 *
 * <p>Covers the contract-defined behaviours:</p>
 * <ul>
 *   <li>Non-platform-admin callers are denied.</li>
 *   <li>Switching to {@code PLATFORM_ADMIN} delegates to
 *       {@link JwtIssuerCapability#issuePlatformAdminToken}.</li>
 *   <li>Switching to a tenant view delegates to
 *       {@link JwtIssuerCapability#issuePlatformAdminViewToken} and preserves
 *       the original {@code sub} across hops.</li>
 *   <li>Every successful switch writes an audit record.</li>
 *   <li>Read-side {@code getCurrent} / {@code getOriginalSub} reflect the
 *       active session.</li>
 * </ul>
 *
 * @since 3.3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ViewModeCapabilityImpl")
class ViewModeCapabilityImplTest {

    private static final long ADMIN_IDENTITY_ID = 42L;
    private static final long ADMIN_ID = 7L;
    private static final long TARGET_TENANT_ID = 100L;
    private static final long TOKEN_TTL_SECONDS = 3600L;

    @Mock
    private SecurityContextHolder securityContextHolder;

    @Mock
    private JwtIssuerCapability jwtIssuerCapability;

    @Mock
    private IdentityTenantCapability identityTenantCapability;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ViewModeCapabilityImpl capability;

    // ============================================================
    // Authorisation
    // ============================================================

    @Test
    @DisplayName("switchTo: denies callers that are neither platform admin nor in viewing mode")
    void deniesNonPlatformAdminCaller() {
        AuthenticatedUser caller = new AuthenticatedUser();
        caller.setUserId("99");
        // platformRole/originalSub left null → not admin, not impersonating
        when(securityContextHolder.requireCurrentUser()).thenReturn(caller);

        ViewModeSwitchDeniedException ex = assertThrows(ViewModeSwitchDeniedException.class,
                () -> capability.switchTo(new SwitchRequest(ViewMode.PLATFORM_ADMIN, null)));
        assertNotNull(ex);

        verify(auditService, never()).log(any());
        verify(jwtIssuerCapability, never()).issuePlatformAdminToken(any());
        verify(jwtIssuerCapability, never()).issuePlatformAdminViewToken(any());
    }

    // ============================================================
    // PLATFORM_ADMIN target
    // ============================================================

    @Test
    @DisplayName("switchTo(PLATFORM_ADMIN): mints a standard admin token via issuePlatformAdminToken")
    void switchToPlatformAdminTarget() {
        AuthenticatedUser caller = newPlatformAdminCaller();
        when(securityContextHolder.requireCurrentUser()).thenReturn(caller);
        stubAdminLookup();
        when(jwtIssuerCapability.getAccessTokenExpirationSeconds()).thenReturn(TOKEN_TTL_SECONDS);
        when(jwtIssuerCapability.issuePlatformAdminToken(any()))
                .thenReturn("admin-token");

        SwitchResult result = capability.switchTo(new SwitchRequest(ViewMode.PLATFORM_ADMIN, null));

        assertEquals("admin-token", result.accessToken());
        assertEquals(ViewMode.PLATFORM_ADMIN, result.mode());
        assertNull(result.tenantId());
        assertNull(result.originalSub());

        ArgumentCaptor<PlatformAdminTokenRequest> req =
                ArgumentCaptor.forClass(PlatformAdminTokenRequest.class);
        verify(jwtIssuerCapability).issuePlatformAdminToken(req.capture());
        assertEquals(ADMIN_ID, req.getValue().adminId());
        assertEquals(ADMIN_IDENTITY_ID, req.getValue().identityId());

        verify(jwtIssuerCapability, never()).issuePlatformAdminViewToken(any());
        verify(auditService).log(any(AuditEvent.class));
    }

    // ============================================================
    // TENANT_ACTOR target — fresh viewing session
    // ============================================================

    @Test
    @DisplayName("switchTo(TENANT_ACTOR): mints a viewing token with original_sub = caller.sub")
    void switchToTenantActorFreshSession() {
        AuthenticatedUser caller = newPlatformAdminCaller();
        when(securityContextHolder.requireCurrentUser()).thenReturn(caller);
        stubAdminLookup();
        when(jwtIssuerCapability.getAccessTokenExpirationSeconds()).thenReturn(TOKEN_TTL_SECONDS);
        when(jwtIssuerCapability.issuePlatformAdminViewToken(any()))
                .thenReturn("view-token");

        SwitchResult result = capability.switchTo(
                new SwitchRequest(ViewMode.TENANT_ACTOR, TARGET_TENANT_ID));

        assertEquals("view-token", result.accessToken());
        assertEquals(ViewMode.TENANT_ACTOR, result.mode());
        assertEquals(TARGET_TENANT_ID, result.tenantId());
        assertEquals(String.valueOf(ADMIN_IDENTITY_ID), result.originalSub());

        ArgumentCaptor<PlatformAdminViewTokenRequest> req =
                ArgumentCaptor.forClass(PlatformAdminViewTokenRequest.class);
        verify(jwtIssuerCapability).issuePlatformAdminViewToken(req.capture());
        assertEquals(TARGET_TENANT_ID, req.getValue().viewTenantId());
        assertEquals(String.valueOf(ADMIN_IDENTITY_ID), req.getValue().originalSub());

        verify(jwtIssuerCapability, never()).issuePlatformAdminToken(any());
        verify(auditService).log(any(AuditEvent.class));
    }

    // ============================================================
    // Hop between tenants — preserves original_sub
    // ============================================================

    @Test
    @DisplayName("switchTo: preserves original_sub when caller is already in viewing mode")
    void preservesOriginalSubAcrossHops() {
        AuthenticatedUser caller = newPlatformAdminCaller();
        // Already viewing tenant 50 — original sub from earlier hop is preserved
        caller.setOriginalSub("12345");
        caller.setTenantId("50");
        when(securityContextHolder.requireCurrentUser()).thenReturn(caller);
        when(identityTenantCapability.findIdentityById(12345L))
                .thenReturn(Optional.of(new IdentityRecord(
                        12345L, "root@example.com", "root", "x", "ACTIVE", false, 1L)));
        when(identityTenantCapability.findActivePlatformAdmin(12345L))
                .thenReturn(Optional.of(new PlatformAdminRecord(
                        ADMIN_ID, 12345L, "SUPER_ADMIN", false)));
        when(jwtIssuerCapability.getAccessTokenExpirationSeconds()).thenReturn(TOKEN_TTL_SECONDS);
        when(jwtIssuerCapability.issuePlatformAdminViewToken(any()))
                .thenReturn("hop-token");

        SwitchResult result = capability.switchTo(
                new SwitchRequest(ViewMode.TENANT_ACTOR, TARGET_TENANT_ID));

        assertEquals("12345", result.originalSub(),
                "original_sub must point to the platform admin who started the chain");

        ArgumentCaptor<PlatformAdminViewTokenRequest> req =
                ArgumentCaptor.forClass(PlatformAdminViewTokenRequest.class);
        verify(jwtIssuerCapability).issuePlatformAdminViewToken(req.capture());
        assertEquals("12345", req.getValue().originalSub());
        assertEquals(TARGET_TENANT_ID, req.getValue().viewTenantId());
    }

    // ============================================================
    // Read side
    // ============================================================

    @Test
    @DisplayName("getCurrent: PLATFORM_ADMIN when caller is not impersonating")
    void getCurrentReturnsPlatformAdmin() {
        AuthenticatedUser caller = newPlatformAdminCaller();
        when(securityContextHolder.requireCurrentUser()).thenReturn(caller);

        assertEquals(ViewMode.PLATFORM_ADMIN, capability.getCurrent());
    }

    @Test
    @DisplayName("getCurrent: TENANT_ACTOR when caller carries original_sub + tenant_id")
    void getCurrentReturnsTenantActorWhenImpersonating() {
        AuthenticatedUser caller = newPlatformAdminCaller();
        caller.setOriginalSub("999");
        caller.setTenantId("123");
        when(securityContextHolder.requireCurrentUser()).thenReturn(caller);

        assertEquals(ViewMode.TENANT_ACTOR, capability.getCurrent());
    }

    @Test
    @DisplayName("getOriginalSub: empty for ordinary admin sessions")
    void getOriginalSubEmptyByDefault() {
        AuthenticatedUser caller = newPlatformAdminCaller();
        when(securityContextHolder.requireCurrentUser()).thenReturn(caller);

        assertEquals(Optional.empty(), capability.getOriginalSub());
    }

    @Test
    @DisplayName("getCurrent: surfaces ViewModeResolutionException when no auth context")
    void getCurrentMissingAuthContextThrows() {
        when(securityContextHolder.requireCurrentUser())
                .thenThrow(new IllegalStateException("no auth"));

        ViewModeResolutionException ex = assertThrows(
                ViewModeResolutionException.class, () -> capability.getCurrent());
        assertNotNull(ex);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private AuthenticatedUser newPlatformAdminCaller() {
        AuthenticatedUser u = new AuthenticatedUser();
        u.setUserId(String.valueOf(ADMIN_IDENTITY_ID));
        u.setPlatformRole("SUPER_ADMIN");
        return u;
    }

    private void stubAdminLookup() {
        when(identityTenantCapability.findIdentityById(ADMIN_IDENTITY_ID))
                .thenReturn(Optional.of(new IdentityRecord(
                        ADMIN_IDENTITY_ID, "admin@example.com", "admin",
                        "x", "ACTIVE", false, 1L)));
        when(identityTenantCapability.findActivePlatformAdmin(ADMIN_IDENTITY_ID))
                .thenReturn(Optional.of(new PlatformAdminRecord(
                        ADMIN_ID, ADMIN_IDENTITY_ID, "SUPER_ADMIN", false)));
    }
}
