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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * Response DTO returned after successfully creating a platform administrator account.
 *
 * <h3>Security</h3>
 * <p>This DTO deliberately exposes only stable identifiers and a delivery marker.
 * It never carries plaintext credentials, setup tokens, setup URLs, or MFA secrets.
 * Setup-link delivery is performed by the server-side notification capability.</p>
 *
 * @param id            newly created {@code sys_platform_admin.id}
 * @param identityId    newly created {@code sys_identity.id}
 * @param setupLinkSent whether the setup link was accepted by the notification channel
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record CreatePlatformAdminResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        @JsonSerialize(using = ToStringSerializer.class)
        Long identityId,
        boolean setupLinkSent
) {}
