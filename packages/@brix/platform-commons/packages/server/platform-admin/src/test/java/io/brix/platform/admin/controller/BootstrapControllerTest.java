package io.brix.platform.admin.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import io.brix.platform.admin.dto.BootstrapStatusResponse;
import io.brix.platform.admin.service.BootstrapSetupService;
import io.brix.platform.admin.service.BootstrapTokenService;
import io.brix.platform.tenant.entity.BootstrapState;
import org.springframework.http.ResponseEntity;

class BootstrapControllerTest {

    private final BootstrapTokenService tokenService = mock(BootstrapTokenService.class);
    private final BootstrapSetupService setupService = mock(BootstrapSetupService.class);
    private final BootstrapController controller = new BootstrapController(tokenService, setupService);

    @Test
    void statusReportsClosedWhenSetupCodeExpired() {
        BootstrapState state = new BootstrapState();
        state.setId(BootstrapState.SINGLETON_ID);
        state.openSetupCode("hash", OffsetDateTime.now().minusSeconds(1));
        when(tokenService.readState()).thenReturn(state);

        ResponseEntity<BootstrapStatusResponse> response = controller.status();

        assertNotNull(response.getBody());
        assertFalse(response.getBody().open());
        assertNull(response.getBody().setupCodeExpiresAt());
    }

    @Test
    void statusReportsOpenWhenSetupCodeUsable() {
        BootstrapState state = new BootstrapState();
        state.setId(BootstrapState.SINGLETON_ID);
        state.openSetupCode("hash", OffsetDateTime.now().plusMinutes(5));
        when(tokenService.readState()).thenReturn(state);

        ResponseEntity<BootstrapStatusResponse> response = controller.status();

        assertNotNull(response.getBody());
        assertTrue(response.getBody().open());
        assertNotNull(response.getBody().setupCodeExpiresAt());
    }
}
