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

/**
 * Raised when platform administrator provisioning requires the setup-link workflow.
 *
 * <p>The exception is intentionally domain-level, not HTTP-specific. Controller
 * advice maps it to {@code 501 Not Implemented} so deployments fail closed until
 * setup-token issuance and notification delivery are available.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public class PlatformAdminProvisioningUnavailableException extends RuntimeException {

    /** Machine-readable error code used by REST advice and clients. */
    public static final String CODE = "PLATFORM_ADMIN_SETUP_WORKFLOW_UNAVAILABLE";

    /** Safe client-facing message. */
    public static final String MESSAGE = "Platform administrator setup-link workflow is unavailable.";

    /**
     * Creates the exception with the stable public message.
     */
    public PlatformAdminProvisioningUnavailableException() {
        super(MESSAGE);
    }
}