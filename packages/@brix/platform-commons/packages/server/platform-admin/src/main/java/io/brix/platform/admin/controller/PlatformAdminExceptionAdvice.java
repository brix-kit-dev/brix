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

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import io.brix.platform.admin.dto.PlatformAdminErrorResponse;
import io.brix.platform.admin.service.PlatformAdminProvisioningUnavailableException;
import io.brix.platform.admin.service.SetupLinkDeliveryException;
import io.runtime.sdk.capability.TenantQuotaCapability.QuotaAdmissionException;

/**
 * Exception mapping for platform administrator lifecycle endpoints.
 *
 * <p>This advice is scoped to the platform-admin controller package so it does
 * not affect tenant or plugin controllers.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "io.brix.platform.admin.controller")
public class PlatformAdminExceptionAdvice {

    /**
     * Maps fail-closed provisioning attempts to a deterministic HTTP response.
     *
     * @param ex domain exception raised by the service layer
     * @return 501 response with stable machine-readable code
     */
    @ExceptionHandler(PlatformAdminProvisioningUnavailableException.class)
    public ResponseEntity<PlatformAdminErrorResponse> handleProvisioningUnavailable(
            PlatformAdminProvisioningUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new PlatformAdminErrorResponse(
                        PlatformAdminProvisioningUnavailableException.CODE,
                        ex.getMessage()));
    }

    /**
     * Maps setup-link delivery failures to a stable fail-closed response.
     *
     * <p>The setup token remains server-side and the transaction rolls back; the
     * client only receives a machine-readable delivery failure code.</p>
     *
     * @param ex notification capability failure
     * @return 503 response with stable machine-readable code
     */
    @ExceptionHandler(SetupLinkDeliveryException.class)
    public ResponseEntity<PlatformAdminErrorResponse> handleSetupLinkDelivery(
            SetupLinkDeliveryException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new PlatformAdminErrorResponse(
                        SetupLinkDeliveryException.CODE,
                        SetupLinkDeliveryException.CODE));
    }

    /**
     * Maps fail-closed license/quota admission denials to a stable response.
     *
     * @param ex quota admission exception
     * @return 403 response with a stable reason code
     */
    @ExceptionHandler(QuotaAdmissionException.class)
    public ResponseEntity<PlatformAdminErrorResponse> handleQuotaAdmission(QuotaAdmissionException ex) {
        String reason = ex.reason();
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new PlatformAdminErrorResponse(reason, reason));
    }

    /**
     * Preserves explicit platform-admin HTTP failure semantics.
     *
     * <p>Service-layer bootstrap/setup guards intentionally raise
     * {@link ResponseStatusException} for states such as expired setup codes,
     * invalid bootstrap anchors, and unavailable bootstrap sessions. Mapping the
     * exception here prevents the generic platform fallback handler from turning
     * these expected domain failures into 500 responses.</p>
     *
     * @param ex response-status exception raised by platform-admin services
     * @return response with the original HTTP status and stable reason code
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<PlatformAdminErrorResponse> handleResponseStatusException(
            ResponseStatusException ex) {
        String exceptionReason = ex.getReason();
        String reason = exceptionReason == null || exceptionReason.isBlank()
                ? ex.getStatusCode().toString()
                : exceptionReason;
        return ResponseEntity.status(ex.getStatusCode())
                .body(new PlatformAdminErrorResponse(reason, reason));
    }
}
