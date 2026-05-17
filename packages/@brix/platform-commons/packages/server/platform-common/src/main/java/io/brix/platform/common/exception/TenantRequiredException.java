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
package io.brix.platform.common.exception;

import java.io.Serial;

/**
 * Thrown when an operation requires a valid tenant context but none is available.
 *
 * <p>This typically occurs when a request reaches a tenant-scoped endpoint
 * without a resolvable tenant ID (missing JWT {@code tid} claim, no
 * {@code X-Tenant-ID} header, etc.). It maps to HTTP 403 (Forbidden)
 * because the request is structurally valid but cannot proceed without
 * tenant context.</p>
 *
 * <h3>Architecture Position</h3>
 * <ul>
 *   <li>Layer: 2C — Platform Commons (platform-common)</li>
 *   <li>Caught by: {@code GlobalExceptionHandler} in platform-common-starter</li>
 *   <li>Blueprint ref: v3.0.9 §14 Multi-Tenant Architecture, R16</li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 * @see PlatformErrorCode#TENANT_REQUIRED
 */
public class TenantRequiredException extends PlatformException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs the exception with a default message.
     */
    public TenantRequiredException() {
        super(PlatformErrorCode.TENANT_REQUIRED);
    }

    /**
     * Constructs the exception with a custom detail message.
     *
     * @param message additional context about why the tenant was required
     */
    public TenantRequiredException(String message) {
        super(PlatformErrorCode.TENANT_REQUIRED, message);
    }
}
