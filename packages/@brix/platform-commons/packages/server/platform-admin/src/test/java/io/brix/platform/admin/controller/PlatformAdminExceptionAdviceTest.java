package io.brix.platform.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import io.brix.platform.admin.dto.PlatformAdminErrorResponse;
import io.brix.platform.admin.service.SetupLinkDeliveryException;

class PlatformAdminExceptionAdviceTest {

    private final PlatformAdminExceptionAdvice advice = new PlatformAdminExceptionAdvice();

    @Test
    void preservesResponseStatusExceptionStatusAndReason() {
        ResponseEntity<PlatformAdminErrorResponse> response = advice.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "BOOTSTRAP_SETUP_CODE_INVALID"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BOOTSTRAP_SETUP_CODE_INVALID", response.getBody().code());
        assertEquals("BOOTSTRAP_SETUP_CODE_INVALID", response.getBody().message());
    }

    @Test
    void mapsSetupLinkDeliveryFailureToServiceUnavailable() {
        ResponseEntity<PlatformAdminErrorResponse> response = advice.handleSetupLinkDelivery(
                new SetupLinkDeliveryException(new IllegalStateException("smtp unavailable")));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(SetupLinkDeliveryException.CODE, response.getBody().code());
        assertEquals(SetupLinkDeliveryException.CODE, response.getBody().message());
    }
}
