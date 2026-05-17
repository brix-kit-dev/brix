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
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.brix.platform.admin.dto.ChangeOwnPasswordRequest;
import io.brix.platform.admin.dto.CreatePlatformAdminRequest;
import io.brix.platform.admin.dto.CreatePlatformAdminResponse;
import io.brix.platform.admin.dto.DisableAdminRequest;
import io.brix.platform.admin.dto.PlatformAdminDto;
import io.brix.platform.admin.dto.ResetPasswordResponse;
import io.brix.platform.admin.service.PlatformAdminService;
import io.brix.platform.auth.PlatformPermissions;
import io.brix.platform.auth.annotation.RequirePermission;
import io.runtime.sdk.capability.AuthContextCapability;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import jakarta.validation.Valid;

/**
 * Platform administrator management endpoints.
 *
 * <h3>Route Prefix</h3>
 * <p>{@code /api/platform/admins}
 *
 * <h3>Permission Model (SSOT §9 R-3)</h3>
 * <p>All endpoints use {@code @RequirePermission(PlatformPermissions.XXX)}.
 * Role-name-based guards ({@code @RequireRole}) are PROHIBITED by architecture contract.
 *
 * <h3>Self-Disable Guard</h3>
 * <p>The disable endpoint rejects requests where the caller attempts to disable
 * their own account. This guard is enforced at the controller layer; the service
 * handles the last-SUPER_ADMIN guard.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@RestController
@RequestMapping("/api/platform/admins")
public class PlatformAdminController {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminController.class);

    private final PlatformAdminService adminService;
    private final AuthContextCapability authContext;

    public PlatformAdminController(PlatformAdminService adminService, AuthContextCapability authContext) {
        this.adminService = adminService;
        this.authContext = authContext;
    }

    // ========================================================================
    // GET /api/platform/admins  — list all admins
    // ========================================================================

    /**
     * Lists all platform administrator accounts.
     *
     * @return 200 with list of admin DTOs
     */
    @GetMapping
    @RequirePermission(PlatformPermissions.ADMIN_READ)
    public ResponseEntity<List<PlatformAdminDto>> listAdmins() {
        return ResponseEntity.ok(adminService.listAdmins());
    }

    // ========================================================================
    // POST /api/platform/admins  — create a new admin
    // ========================================================================

    /**
     * Creates a new platform administrator account.
     *
     * <p><b>R-10:</b> The one-time temporary password is returned in the response body only.
     * It is never logged.
     *
     * @param request creation parameters
     * @return 201 Created with admin details including the temporary password
     */
    @PostMapping
    @RequirePermission(PlatformPermissions.ADMIN_CREATE)
    public ResponseEntity<CreatePlatformAdminResponse> createAdmin(
            @Valid @RequestBody CreatePlatformAdminRequest request) {

        Long operatorId = requireIdentityId();
        CreatePlatformAdminResponse response = adminService.createAdmin(request, operatorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ========================================================================
    // PATCH /api/platform/admins/{id}/disable  — disable an admin
    // ========================================================================

    /**
     * Disables (suspends) a platform administrator account.
     *
     * <p>Self-disable is rejected at this layer. The service rejects the last-SUPER_ADMIN case.
     *
     * @param adminId target admin ID
     * @param request reason for disabling
     * @return 204 No Content on success
     */
    @PatchMapping("/{id}/disable")
    @RequirePermission(PlatformPermissions.ADMIN_DISABLE)
    public ResponseEntity<Void> disableAdmin(
            @PathVariable("id") Long adminId,
            @Valid @RequestBody DisableAdminRequest request) {

        Long operatorId = requireIdentityId();
        PlatformAdminDto target = adminService.getAdmin(adminId);

        // Self-disable guard: an admin cannot disable their own account
        if (target.identityId() != null && target.identityId().equals(operatorId)) {
            throw new AuthFlowException(
                    "PLATFORM_ADMIN_SELF_DISABLE",
                    "An administrator cannot disable their own account.");
        }

        adminService.disableAdmin(adminId, request, operatorId);
        return ResponseEntity.noContent().build();
    }

    // ========================================================================
    // POST /api/platform/admins/{id}/reset-password  — operator-initiated password reset
    // ========================================================================

    /**
     * Resets a platform administrator's password to a system-generated temporary value.
     *
     * <p><b>R-10:</b> The temporary password appears in the response body only.
     *
     * @param adminId target admin ID
     * @return 200 with temporary password (R-10: only disclosure point)
     */
    @PostMapping("/{id}/reset-password")
    @RequirePermission(PlatformPermissions.ADMIN_RESET_PASSWORD)
    public ResponseEntity<ResetPasswordResponse> resetPassword(
            @PathVariable("id") Long adminId) {

        Long operatorId = requireIdentityId();
        ResetPasswordResponse response = adminService.resetPassword(adminId, operatorId);
        return ResponseEntity.ok(response);
    }

    // ========================================================================
    // POST /api/platform/admins/me/change-password  — self-service password change
    // ========================================================================

    /**
     * Allows a platform admin to change their own password.
     *
     * <p>Requires the current password for re-authentication before the change is applied.
     * On success, all outstanding JWTs for this identity are invalidated (token_version++).
     *
     * @param request old and new passwords
     * @return 204 No Content on success
     */
    @PostMapping("/me/change-password")
    @RequirePermission(PlatformPermissions.ADMIN_CHANGE_OWN_PASSWORD)
    public ResponseEntity<Void> changeOwnPassword(@Valid @RequestBody ChangeOwnPasswordRequest request) {
        Long identityId = requireIdentityId();
        adminService.changeOwnPassword(identityId, request);
        return ResponseEntity.noContent().build();
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    private Long requireIdentityId() {
        Principal principal = authContext.getCurrentPrincipal();
        if (principal == null || principal.getName() == null) {
            throw new AuthFlowException(
                    AuthFlowException.CODE_IDENTITY_NOT_FOUND,
                    "Authentication required.");
        }
        try {
            return Long.parseLong(principal.getName());
        } catch (NumberFormatException e) {
            log.warn("[PlatformAdmin] Non-numeric principal: {}", principal.getName());
            throw new AuthFlowException(
                    AuthFlowException.CODE_IDENTITY_NOT_FOUND,
                    "Invalid identity token (non-numeric subject).");
        }
    }
}
