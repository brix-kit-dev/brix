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
import io.brix.platform.admin.dto.PlatformAdminDto;
import io.brix.platform.admin.dto.RevokeAdminRequest;
import io.brix.platform.admin.dto.ResetPasswordResponse;

/**
 * Service contract for platform administrator management.
 *
 * <h3>Architectural Position</h3>
 * <p>Layer 2C ({@code platform-admin} module). All methods apply permission-independent
 * business rules (e.g. last formal-super-admin guard). Permission checks are enforced at the
 * controller layer via {@code @RequirePermission}.
 *
 * <h3>Audit Guarantee</h3>
 * <p>Every mutating operation MUST emit an audit event via {@code AuditService}.
 * Implementations MUST NOT skip audit on failure paths.
 *
 * <h3>R-12 Security Red-Line</h3>
 * <p>Lifecycle responses MUST NEVER contain plaintext credentials, setup tokens,
 * setup URLs, or MFA secrets. Setup links are delivered only through server-side
 * notification capabilities.
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
    * Creates a new platform administrator account through the setup-link workflow.
    *
    * <p>Implementations must fail closed before writing identity or admin rows when
    * setup-token issuance or notification delivery is unavailable.</p>
     *
     * @param request       creation parameters (username, email, role, notes)
     * @param operatorIdentityId identity_id of the operator performing this action
    * @return response containing identifiers and setup-link delivery status
     * @throws IllegalArgumentException if the email is already registered
    * @throws PlatformAdminProvisioningUnavailableException if setup-link delivery is unavailable
     */
    CreatePlatformAdminResponse createAdmin(CreatePlatformAdminRequest request, Long operatorIdentityId);

    /**
    * Revokes a platform administrator grant.
     *
     * <p>Business rules enforced:
     * <ul>
    *   <li>A grant cannot be revoked if it is the last active formal platform super admin.</li>
    *   <li>An account cannot revoke itself (self-revoke guard at controller layer).</li>
     * </ul>
     *
     * @param adminId            {@code sys_platform_admin.id} of the target account
    * @param request            reason for revoking
     * @param operatorIdentityId identity_id of the operator performing this action
    * @throws IllegalStateException    if this is the last active formal platform super admin
     * @throws jakarta.persistence.EntityNotFoundException if no admin exists with the given ID
     */
    void revokeAdmin(Long adminId, RevokeAdminRequest request, Long operatorIdentityId);

    /**
    * Reissues platform administrator setup through the setup-link workflow.
    *
    * <p>Implementations must fail closed before mutating credentials when
    * setup-token issuance or notification delivery is unavailable.</p>
     *
     * @param adminId            {@code sys_platform_admin.id} of the target account
     * @param operatorIdentityId identity_id of the operator performing this action
    * @return response containing setup-link delivery status
     * @throws jakarta.persistence.EntityNotFoundException if no admin exists with the given ID
    * @throws PlatformAdminProvisioningUnavailableException if setup-link delivery is unavailable
     */
    ResetPasswordResponse resetPassword(Long adminId, Long operatorIdentityId);

    /**
     * Allows a platform administrator to change their own password.
     *
     * <p>The old password and current TOTP code are verified before the change is
     * applied. On success the token_version is incremented to invalidate existing JWTs.
     *
     * @param identityId identity_id extracted from the caller's JWT
     * @param request    old and new passwords
     * @throws io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException
     *         with {@code AUTH_OLD_PASSWORD_MISMATCH} if the old password is wrong
     */
    void changeOwnPassword(Long identityId, ChangeOwnPasswordRequest request);
}
