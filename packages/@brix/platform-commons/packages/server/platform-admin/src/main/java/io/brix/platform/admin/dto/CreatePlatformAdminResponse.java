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
package io.brix.platform.admin.dto;

/**
 * Response DTO returned after successfully creating a platform administrator account.
 *
 * <h3>Security (SSOT §10 R-10)</h3>
 * <p>The {@code tempPassword} field is the ONLY point where the temporary password
 * is disclosed. It MUST NOT appear in audit logs, application logs, or any other
 * persistent storage. The client is responsible for delivering it to the new admin
 * through a secure out-of-band channel.
 *
 * @param adminId      newly created {@code sys_platform_admin.id}
 * @param identityId   newly created {@code sys_identity.id}
 * @param username     display name of the new admin
 * @param email        email / login identifier of the new admin
 * @param role         assigned platform admin role code
 * @param tempPassword one-time temporary password — valid until first login
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record CreatePlatformAdminResponse(
        Long adminId,
        Long identityId,
        String username,
        String email,
        String role,
        String tempPassword
) {}
