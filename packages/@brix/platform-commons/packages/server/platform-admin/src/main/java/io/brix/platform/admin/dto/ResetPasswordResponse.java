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
 * Response DTO returned after resetting a platform administrator's password.
 *
 * <h3>Security (SSOT §10 R-10)</h3>
 * <p>The {@code tempPassword} field is the ONLY point where the temporary password
 * is disclosed. It MUST NOT appear in audit logs, application logs, or the
 * audit event reason field. The calling operator is responsible for delivering
 * the password to the target admin via a secure out-of-band channel.
 *
 * @param tempPassword one-time temporary password — expires in 24 hours
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record ResetPasswordResponse(
        String tempPassword
) {}
