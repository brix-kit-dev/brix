package io.brix.platform.identity.service;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.brix.platform.auth.AuditAction;
import io.brix.platform.identity.entity.BootstrapState;
import io.brix.platform.identity.entity.Identity;
import io.brix.platform.identity.entity.PlatformAdmin;
import io.brix.platform.identity.enums.IdentityStatus;
import io.brix.platform.identity.enums.PlatformAdminRole;
import io.brix.platform.identity.event.IdentityAuditFact;
import io.brix.platform.identity.repository.BootstrapStateRepository;
import io.brix.platform.identity.repository.IdentityRepository;
import io.brix.platform.identity.repository.PlatformAdminRepository;

class BootstrapCompletionListenerTest {

    @Test
    void activeFormalSuperAdminWithMfaClosesBootstrapAndDisablesAnchor() {
        BootstrapStateRepository stateRepository = mock(BootstrapStateRepository.class);
        IdentityRepository identityRepository = mock(IdentityRepository.class);
        PlatformAdminRepository adminRepository = mock(PlatformAdminRepository.class);
        RecordedAuditFacts audit = new RecordedAuditFacts();

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
            audit.publisher());

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

        assertEquals(List.of(AuditAction.BOOTSTRAP_ADMIN_DEACTIVATED, AuditAction.IDENTITY_DISABLED,
                AuditAction.BOOTSTRAP_ADMIN_DEACTIVATED, AuditAction.IDENTITY_DISABLED), audit.actions());
        assertTrue(audit.facts().stream()
            .filter(fact -> AuditAction.BOOTSTRAP_ADMIN_DEACTIVATED.equals(fact.action()))
            .allMatch(fact -> "BOOTSTRAP".equals(fact.targetType())));
        assertEquals(List.of("1", "1", "3", "3"), audit.facts().stream().map(IdentityAuditFact::targetId).toList());
        assertTrue(audit.facts().stream().allMatch(fact -> fact.actorIdentityId() == 2L
            && IdentityAuditFacts.REASON_BOOTSTRAP_COMPLETED.equals(fact.reasonCode())
            && IdentityAuditFacts.EXECUTION_IDENTITY.equals(fact.executionIdentity())
            && fact.success()));
        }

        @Test
        void pendingFormalSuperAdminDoesNotCloseBootstrapEarly() {
        BootstrapStateRepository stateRepository = mock(BootstrapStateRepository.class);
        IdentityRepository identityRepository = mock(IdentityRepository.class);
        PlatformAdminRepository adminRepository = mock(PlatformAdminRepository.class);
        RecordedAuditFacts audit = new RecordedAuditFacts();

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
            audit.publisher());

        assertFalse(listener.completeIfEligible(2L));

        assertNull(state.getCompletedAt());
        verify(stateRepository, never()).save(state);
        verify(identityRepository, never()).save(formalIdentity);
        verify(adminRepository, never()).save(formalAdmin);
        assertEquals(List.of(), audit.facts());
    }
}