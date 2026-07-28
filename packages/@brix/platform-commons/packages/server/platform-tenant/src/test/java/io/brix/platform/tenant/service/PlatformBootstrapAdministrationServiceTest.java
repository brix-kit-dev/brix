package io.brix.platform.tenant.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.brix.platform.tenant.bootstrap.SuperAdminBootstrapProperties;
import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.entity.BootstrapState;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.entity.SetupToken;
import io.brix.platform.tenant.internal.CreateFirstPlatformAdminCommand;
import io.brix.platform.tenant.repository.BootstrapStateRepository;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.brix.platform.tenant.repository.SetupTokenRepository;
import io.brix.platform.tenant.security.SecretHashing;
import io.runtime.sdk.capability.NotificationCapability;

@ExtendWith(MockitoExtension.class)
class PlatformBootstrapAdministrationServiceTest {

    @Mock
    private BootstrapStateRepository bootstrapStateRepository;

    @Mock
    private IdentityRepository identityRepository;

    @Mock
    private PlatformAdminRepository platformAdminRepository;

    @Mock
    private SetupTokenRepository setupTokenRepository;

    @Mock
    private AuditService auditService;

    @Test
    void createFirstAdminFlushesIdentityAndAdminBeforeIssuingSetupToken() {
        SuperAdminBootstrapProperties properties = new SuperAdminBootstrapProperties();
        properties.setSetupBaseUrl("https://platform.example.invalid/platform/setup");
        BootstrapState state = new BootstrapState();
        state.activateSession(SecretHashing.sha256Base64Url("bootstrap-session"),
                OffsetDateTime.now().plusMinutes(5));

        NotificationCapability notification = mock(NotificationCapability.class);
        IdGenerator idGenerator = new IdGenerator() {
            private long next = 1000L;

            @Override
            public long nextId() {
                return next++;
            }

            @Override
            public long parseTimestamp(long id) {
                return id;
            }

            @Override
            public long parseWorkerId(long id) {
                return 0L;
            }
        };

        when(bootstrapStateRepository.findByIdForUpdate(BootstrapState.SINGLETON_ID))
                .thenReturn(Optional.of(state));
        when(platformAdminRepository.countCompletedFormalSuperAdmins()).thenReturn(0L);
        when(identityRepository.existsByEmail("owner@example.invalid")).thenReturn(false);
        when(identityRepository.save(any(Identity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(platformAdminRepository.save(any(PlatformAdmin.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(setupTokenRepository.save(any(SetupToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlatformBootstrapAdministrationService service = new PlatformBootstrapAdministrationService(
                properties,
                bootstrapStateRepository,
                identityRepository,
                platformAdminRepository,
                setupTokenRepository,
                idGenerator,
                Optional.empty(),
                Optional.of(notification),
                auditService);

        service.createFirstAdmin(new CreateFirstPlatformAdminCommand(
                "bootstrap-session",
                "Owner",
                "owner@example.invalid",
                null));

        InOrder order = inOrder(identityRepository, platformAdminRepository, setupTokenRepository);
        order.verify(identityRepository).save(any(Identity.class));
        order.verify(identityRepository).flush();
        order.verify(platformAdminRepository).save(any(PlatformAdmin.class));
        order.verify(platformAdminRepository).flush();
        order.verify(setupTokenRepository).save(any(SetupToken.class));
        verify(notification).send(any());
    }
}
