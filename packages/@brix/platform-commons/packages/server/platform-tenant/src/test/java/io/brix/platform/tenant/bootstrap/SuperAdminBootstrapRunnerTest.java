package io.brix.platform.tenant.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.entity.BootstrapState;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.enums.IdentityStatus;
import io.brix.platform.tenant.enums.PlatformAdminRole;
import io.brix.platform.tenant.enums.PlatformAdminStatus;
import io.brix.platform.tenant.repository.BootstrapStateRepository;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;

class SuperAdminBootstrapRunnerTest {

    @Test
    void createsPasswordlessPendingSetupBootstrapAnchor() throws Exception {
        SuperAdminBootstrapProperties properties = new SuperAdminBootstrapProperties();
        properties.setEnabled(true);
        properties.setEmail("bootstrap@example.invalid");
        properties.setUsername("Bootstrap Setup");
        properties.setSetupCode("setup-code");

        IdentityRepository identityRepository = mock(IdentityRepository.class);
        PlatformAdminRepository platformAdminRepository = mock(PlatformAdminRepository.class);
        BootstrapStateRepository bootstrapStateRepository = mock(BootstrapStateRepository.class);
        IdGenerator idGenerator = mock(IdGenerator.class);

        when(bootstrapStateRepository.findByIdForUpdate(BootstrapState.SINGLETON_ID))
                .thenReturn(Optional.empty());
        when(identityRepository.findByEmail("bootstrap@example.invalid"))
                .thenReturn(Optional.empty());
        when(platformAdminRepository.findByIdentityId(100L))
                .thenReturn(Optional.empty());
        when(identityRepository.save(any(Identity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(platformAdminRepository.save(any(PlatformAdmin.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bootstrapStateRepository.save(any(BootstrapState.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(idGenerator.nextId()).thenReturn(100L, 101L);

        SuperAdminBootstrapRunner runner = new SuperAdminBootstrapRunner(
                properties,
                identityRepository,
                platformAdminRepository,
                bootstrapStateRepository,
                idGenerator);

        runner.run(null);

        ArgumentCaptor<Identity> identityCaptor = ArgumentCaptor.forClass(Identity.class);
        ArgumentCaptor<PlatformAdmin> adminCaptor = ArgumentCaptor.forClass(PlatformAdmin.class);
        ArgumentCaptor<BootstrapState> stateCaptor = ArgumentCaptor.forClass(BootstrapState.class);
        verify(identityRepository).save(identityCaptor.capture());
        verify(platformAdminRepository).save(adminCaptor.capture());
        verify(bootstrapStateRepository).save(stateCaptor.capture());

        Identity identity = identityCaptor.getValue();
        assertEquals(IdentityStatus.PENDING_SETUP, identity.getStatus());
        assertNull(identity.getPasswordHash());
        assertFalse(identity.isMfaEnabled());

        PlatformAdmin admin = adminCaptor.getValue();
        assertEquals(PlatformAdminRole.BOOTSTRAP, admin.getRole());
        assertEquals(PlatformAdminStatus.ACTIVE, admin.getStatus());
        assertFalse(admin.isMfaEnabled());

        BootstrapState state = stateCaptor.getValue();
        assertEquals(100L, state.getBootstrapIdentityId());
        assertNotNull(state.getSetupCodeHash());
        assertNotNull(state.getSetupCodeExpiresAt());
        assertNull(state.getCompletedAt());
    }

        @Test
        void restartAfterBootstrapClosureDoesNotRecreateAnchor() throws Exception {
                SuperAdminBootstrapProperties properties = new SuperAdminBootstrapProperties();
                properties.setEnabled(true);
                properties.setEmail("bootstrap@example.invalid");
                properties.setSetupCode("setup-code");

                IdentityRepository identityRepository = mock(IdentityRepository.class);
                PlatformAdminRepository platformAdminRepository = mock(PlatformAdminRepository.class);
                BootstrapStateRepository bootstrapStateRepository = mock(BootstrapStateRepository.class);
                IdGenerator idGenerator = mock(IdGenerator.class);
                BootstrapState closedState = new BootstrapState();
                closedState.setId(BootstrapState.SINGLETON_ID);
                closedState.complete(2L);

                when(bootstrapStateRepository.findByIdForUpdate(BootstrapState.SINGLETON_ID))
                                .thenReturn(Optional.of(closedState));

                SuperAdminBootstrapRunner runner = new SuperAdminBootstrapRunner(
                                properties,
                                identityRepository,
                                platformAdminRepository,
                                bootstrapStateRepository,
                                idGenerator);

                runner.run(null);

                verify(identityRepository, never()).save(any(Identity.class));
                verify(platformAdminRepository, never()).save(any(PlatformAdmin.class));
                verify(bootstrapStateRepository, never()).save(any(BootstrapState.class));
        }
}