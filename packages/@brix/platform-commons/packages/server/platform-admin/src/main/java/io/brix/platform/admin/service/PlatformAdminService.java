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
package io.brix.platform.admin.service;

import java.util.List;

import io.brix.platform.admin.dto.ChangeOwnPasswordRequest;
import io.brix.platform.admin.dto.CreatePlatformAdminRequest;
import io.brix.platform.admin.dto.CreatePlatformAdminResponse;
import io.brix.platform.admin.dto.DisableAdminRequest;
import io.brix.platform.admin.dto.PlatformAdminDto;
import io.brix.platform.admin.dto.ResetPasswordResponse;

/**
 * Service contract for platform administrator management.
 *
 * <h3>Architectural Position</h3>
 * <p>Layer 2C ({@code platform-admin} module). All methods apply permission-independent
 * business rules (e.g. last-SUPER_ADMIN guard). Permission checks are enforced at the
 * controller layer via {@code @RequirePermission}.
 *
 * <h3>Audit Guarantee</h3>
 * <p>Every mutating operation MUST emit an audit event via {@code AuditService}.
 * Implementations MUST NOT skip audit on failure paths.
 *
 * <h3>R-10 Security Red-Line</h3>
 * <p>Temporary passwords MUST NEVER appear in audit log {@code description} or
 * {@code reason} fields. The only disclosure point is the response DTO.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public interface PlatformAdminService {

    /**
     * Returns all platform administrator accounts (active and suspended).
     *
     * @return unmodifiable list of admin DTOs ordered by creation time descending
     */
    List<PlatformAdminDto> listAdmins();

    /**
     * Returns a single platform administrator by primary key.
     *
     * @param adminId {@code sys_platform_admin.id}
     * @return admin DTO
     * @throws jakarta.persistence.EntityNotFoundException if no admin exists with the given ID
     */
    PlatformAdminDto getAdmin(Long adminId);

    /**
     * Creates a new platform administrator account.
     *
     * <p>Steps performed:
     * <ol>
     *   <li>Validates that the email is not already registered.</li>
     *   <li>Creates a new {@code sys_identity} record.</li>
     *   <li>Generates a cryptographically secure temporary password and stores its hash.</li>
     *   <li>Creates a {@code sys_platform_admin} record with the requested role.</li>
     *   <li>Emits a {@code SUPER_ADMIN_CREATED} audit event.</li>
     * </ol>
     *
     * <p><b>R-10:</b> The temporary password is returned in the response DTO ONLY.
     * It MUST NOT appear in the audit event description.
     *
     * @param request       creation parameters (username, email, role, notes)
     * @param operatorIdentityId identity_id of the operator performing this action
     * @return response containing admin details and the one-time temporary password
     * @throws IllegalArgumentException if the email is already registered
     */
    CreatePlatformAdminResponse createAdmin(CreatePlatformAdminRequest request, Long operatorIdentityId);

    /**
     * Disables (suspends) a platform administrator account.
     *
     * <p>Business rules enforced:
     * <ul>
     *   <li>An account cannot be disabled if it is the last active {@code SUPER_ADMIN}.</li>
     *   <li>An account cannot disable itself (self-disable guard at controller layer).</li>
     * </ul>
     *
     * @param adminId            {@code sys_platform_admin.id} of the target account
     * @param request            reason for disabling
     * @param operatorIdentityId identity_id of the operator performing this action
     * @throws IllegalStateException    if this is the last active SUPER_ADMIN
     * @throws jakarta.persistence.EntityNotFoundException if no admin exists with the given ID
     */
    void disableAdmin(Long adminId, DisableAdminRequest request, Long operatorIdentityId);

    /**
     * Resets a platform administrator's password to a system-generated temporary value.
     *
     * <p>After reset, {@code identity.password_must_change = true} and
     * {@code platform_admin.temp_password_expires_at} is set to 24 hours from now.
     * The existing token_version is incremented to invalidate outstanding JWTs.
     *
     * <p><b>R-10:</b> The returned {@code tempPassword} MUST NOT be logged or stored in
     * any audit field.
     *
     * @param adminId            {@code sys_platform_admin.id} of the target account
     * @param operatorIdentityId identity_id of the operator performing this action
     * @return response containing the one-time temporary password
     * @throws jakarta.persistence.EntityNotFoundException if no admin exists with the given ID
     */
    ResetPasswordResponse resetPassword(Long adminId, Long operatorIdentityId);

    /**
     * Allows a platform administrator to change their own password.
     *
     * <p>The old password is verified before the change is applied.
     * On success the token_version is incremented to invalidate existing JWTs,
     * and {@code password_must_change} is cleared.
     *
     * @param identityId identity_id extracted from the caller's JWT
     * @param request    old and new passwords
     * @throws io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException
     *         with {@code AUTH_OLD_PASSWORD_MISMATCH} if the old password is wrong
     */
    void changeOwnPassword(Long identityId, ChangeOwnPasswordRequest request);
}
