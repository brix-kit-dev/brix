package io.brix.platform.identity.service;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.brix.platform.auth.AuditAction;
import io.brix.platform.identity.dto.AuditEvent;
import io.brix.platform.identity.entity.BootstrapState;
import io.brix.platform.identity.entity.Identity;
import io.brix.platform.identity.entity.PlatformAdmin;
import io.brix.platform.identity.enums.IdentityStatus;
import io.brix.platform.identity.enums.PlatformAdminRole;
import io.brix.platform.identity.repository.BootstrapStateRepository;
import io.brix.platform.identity.repository.IdentityRepository;
import io.brix.platform.identity.repository.PlatformAdminRepository;

class BootstrapCompletionListenerTest {

    @Test
    void activeFormalSuperAdminWithMfaClosesBootstrapAndDisablesAnchor() {
        BootstrapStateRepository stateRepository = mock(BootstrapStateRepository.class);
        IdentityRepository identityRepository = mock(IdentityRepository.class);
        PlatformAdminRepository adminRepository = mock(PlatformAdminRepository.class);
        AuditService auditService = mock(AuditService.class);

        BootstrapState state = new BootstrapState();
        state.setId(BootstrapState.SINGLETON_ID);
        state.setBootstrapIdentityId(1L);

        Identity formalIdentity = new Identity("admin@example.invalid", "Admin");
        formalIdentity.setId(2L);
        formalIdentity.setStatus(IdentityStatus.ACTIVE);
        formalIdentity.setMfaEnabled(true);

        PlatformAdmin formalAdmin = new PlatformAdmin(2L, PlatformAdminRole.PLATFORM_SUPER_ADMIN);
        formalAdmin.setId(20L);

        Identity bootstrapIdentity = new Identity("bootstrap@example.invalid", "Bootstrap");
        bootstrapIdentity.setId(1L);
        bootstrapIdentity.setStatus(IdentityStatus.PENDING_SETUP);
        bootstrapIdentity.setMfaEnabled(true);
        bootstrapIdentity.setMfaSecretEncrypted("encrypted-secret");
        bootstrapIdentity.setTokenVersion(7L);

        Identity secondBootstrapIdentity = new Identity("bootstrap-2@example.invalid", "Bootstrap 2");
        secondBootstrapIdentity.setId(3L);
        secondBootstrapIdentity.setStatus(IdentityStatus.PENDING_SETUP);
        secondBootstrapIdentity.setTokenVersion(4L);

        PlatformAdmin bootstrapAdmin = new PlatformAdmin(1L, PlatformAdminRole.BOOTSTRAP);
        bootstrapAdmin.setId(10L);

        PlatformAdmin secondBootstrapAdmin = new PlatformAdmin(3L, PlatformAdminRole.BOOTSTRAP);
        secondBootstrapAdmin.setId(11L);

        when(stateRepository.findByIdForUpdate(BootstrapState.SINGLETON_ID)).thenReturn(Optional.of(state));
        when(identityRepository.findById(2L)).thenReturn(Optional.of(formalIdentity));
        when(adminRepository.findByIdentityId(2L)).thenReturn(Optional.of(formalAdmin));
        when(identityRepository.findById(1L)).thenReturn(Optional.of(bootstrapIdentity));
        when(identityRepository.findById(3L)).thenReturn(Optional.of(secondBootstrapIdentity));
        when(adminRepository.findByRole(PlatformAdminRole.BOOTSTRAP))
            .thenReturn(List.of(bootstrapAdmin, secondBootstrapAdmin));

        BootstrapCompletionListener listener = new BootstrapCompletionListener(
                stateRepository,
                identityRepository,
            adminRepository,
            auditService);

        assertTrue(listener.completeIfEligible(2L));

        assertNotNull(state.getCompletedAt());
        assertEquals(2L, state.getCompletedByIdentityId());
        assertEquals(IdentityStatus.DISABLED, bootstrapIdentity.getStatus());
        assertNull(bootstrapIdentity.getPasswordHash());
        assertFalse(bootstrapIdentity.isMfaEnabled());
        assertNull(bootstrapIdentity.getMfaSecretEncrypted());
        assertEquals(8L, bootstrapIdentity.getTokenVersion());
        assertEquals(IdentityStatus.DISABLED, secondBootstrapIdentity.getStatus());
        assertEquals(5L, secondBootstrapIdentity.getTokenVersion());
        verify(adminRepository).save(formalAdmin);
        verify(stateRepository).save(state);
        verify(identityRepository).save(bootstrapIdentity);
        verify(identityRepository).save(secondBootstrapIdentity);
        verify(identityRepository).findById(1L);
        verify(identityRepository).findById(2L);
        verify(identityRepository).findById(3L);

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(4)).log(auditCaptor.capture());
        assertEquals(4, auditCaptor.getAllValues().size());
        assertEquals(2, auditCaptor.getAllValues().stream()
            .filter(auditEvent -> AuditAction.BOOTSTRAP_ADMIN_DEACTIVATED.equals(auditEvent.getAction()))
            .count());
        assertEquals(2, auditCaptor.getAllValues().stream()
            .filter(auditEvent -> AuditAction.IDENTITY_DISABLED.equals(auditEvent.getAction()))
            .count());
        assertTrue(auditCaptor.getAllValues().stream()
            .filter(auditEvent -> AuditAction.BOOTSTRAP_ADMIN_DEACTIVATED.equals(auditEvent.getAction()))
            .allMatch(auditEvent -> "BOOTSTRAP".equals(auditEvent.getResourceType())));
        assertTrue(auditCaptor.getAllValues().stream()
            .map(AuditEvent::getResourceId)
            .toList()
            .containsAll(List.of("1", "3")));
        }

        @Test
        void pendingFormalSuperAdminDoesNotCloseBootstrapEarly() {
        BootstrapStateRepository stateRepository = mock(BootstrapStateRepository.class);
        IdentityRepository identityRepository = mock(IdentityRepository.class);
        PlatformAdminRepository adminRepository = mock(PlatformAdminRepository.class);
        AuditService auditService = mock(AuditService.class);

        BootstrapState state = new BootstrapState();
        state.setId(BootstrapState.SINGLETON_ID);
        state.setBootstrapIdentityId(1L);

        Identity formalIdentity = new Identity("admin@example.invalid", "Admin");
        formalIdentity.setId(2L);
        formalIdentity.setStatus(IdentityStatus.PENDING_SETUP);
        formalIdentity.setMfaEnabled(false);

        PlatformAdmin formalAdmin = new PlatformAdmin(2L, PlatformAdminRole.PLATFORM_SUPER_ADMIN);
        formalAdmin.setId(20L);

        when(stateRepository.findByIdForUpdate(BootstrapState.SINGLETON_ID)).thenReturn(Optional.of(state));
        when(identityRepository.findById(2L)).thenReturn(Optional.of(formalIdentity));
        when(adminRepository.findByIdentityId(2L)).thenReturn(Optional.of(formalAdmin));

        BootstrapCompletionListener listener = new BootstrapCompletionListener(
            stateRepository,
            identityRepository,
            adminRepository,
            auditService);

        assertFalse(listener.completeIfEligible(2L));

        assertNull(state.getCompletedAt());
        verify(stateRepository, never()).save(state);
        verify(identityRepository, never()).save(formalIdentity);
        verify(adminRepository, never()).save(formalAdmin);
        verify(auditService, never()).log(any(AuditEvent.class));
    }
}