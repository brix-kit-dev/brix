package io.brix.platform.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.brix.platform.identity.bootstrap.SuperAdminBootstrapProperties;
import io.brix.platform.identity.core.IdGenerator;
import io.brix.platform.identity.entity.BootstrapState;
import io.brix.platform.identity.entity.Identity;
import io.brix.platform.identity.entity.PlatformAdmin;
import io.brix.platform.identity.entity.SetupToken;
import io.brix.platform.identity.internal.BootstrapSessionCommand;
import io.brix.platform.identity.internal.BootstrapSessionView;
import io.brix.platform.identity.internal.BootstrapStatusView;
import io.brix.platform.identity.internal.CreateFirstPlatformAdminCommand;
import io.brix.platform.identity.repository.BootstrapStateRepository;
import io.brix.platform.identity.repository.IdentityRepository;
import io.brix.platform.identity.repository.PlatformAdminRepository;
import io.brix.platform.identity.repository.SetupTokenRepository;
import io.brix.platform.identity.security.SecretHashing;
import io.runtime.sdk.capability.JwtIssuerCapability;
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

    private SuperAdminBootstrapProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SuperAdminBootstrapProperties();
        properties.setEnabled(true);
        properties.setSetupCode("setup-code");
        properties.setSetupBaseUrl("https://platform.example.invalid/platform/setup");
    }

    @Test
    void statusCanOpenStageAWithoutWritingBootstrapState() {
        when(bootstrapStateRepository.findById(BootstrapState.SINGLETON_ID))
                .thenReturn(Optional.empty());
        when(platformAdminRepository.countCompletedFormalSuperAdmins()).thenReturn(0L);

        PlatformBootstrapAdministrationService service = service(Optional.empty(), Optional.empty());

        BootstrapStatusView status = service.status();

        assertEquals(true, status.open());
        verify(bootstrapStateRepository, never()).save(any(BootstrapState.class));
        verifyNoInteractions(identityRepository, setupTokenRepository, auditService);
    }

    @Test
    void openSessionInitializesSingletonWithoutCreatingBootstrapIdentityOrAdmin() {
        JwtIssuerCapability issuer = mock(JwtIssuerCapability.class);
        when(issuer.issueBootstrapSetupToken(any(JwtIssuerCapability.BootstrapSetupTokenRequest.class)))
                .thenReturn("bootstrap-jwt");
        when(bootstrapStateRepository.findByIdForUpdate(BootstrapState.SINGLETON_ID))
                .thenReturn(Optional.empty());
        when(platformAdminRepository.countCompletedFormalSuperAdmins()).thenReturn(0L);
        when(bootstrapStateRepository.save(any(BootstrapState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlatformBootstrapAdministrationService service = service(Optional.of(issuer), Optional.empty());

        BootstrapSessionView session = service.openSession(new BootstrapSessionCommand("setup-code"));

        assertEquals("BOOTSTRAP_SETUP", session.tokenType());
        assertEquals("bootstrap-jwt", session.accessToken());
        ArgumentCaptor<BootstrapState> stateCaptor = ArgumentCaptor.forClass(BootstrapState.class);
        verify(bootstrapStateRepository).save(stateCaptor.capture());
        BootstrapState saved = stateCaptor.getValue();
        assertEquals(BootstrapState.SINGLETON_ID, saved.getId());
        assertEquals(SecretHashing.sha256Base64Url("setup-code"), saved.getSetupCodeHash());
        assertEquals(SecretHashing.sha256Base64Url("bootstrap-jwt"), saved.getBootstrapSessionJti());
        assertFalse(saved.isCompleted());
        verify(identityRepository, never()).save(any(Identity.class));
        verify(platformAdminRepository, never()).save(any(PlatformAdmin.class));
        verify(setupTokenRepository, never()).save(any(SetupToken.class));
        verify(issuer).issueBootstrapSetupToken(argThat(request ->
                request.permissions().equals(List.of(
                        "bootstrap:status",
                        "bootstrap:session",
                        "bootstrap:create-first-admin"))));
    }

    @Test
    void createFirstAdminFlushesIdentityAndAdminBeforeIssuingSetupToken() {
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

    private PlatformBootstrapAdministrationService service(
            Optional<JwtIssuerCapability> jwtIssuerCapability,
            Optional<NotificationCapability> notificationCapability) {
        return new PlatformBootstrapAdministrationService(
                properties,
                bootstrapStateRepository,
                identityRepository,
                platformAdminRepository,
                setupTokenRepository,
                new FixedIdGenerator(),
                jwtIssuerCapability,
                notificationCapability,
                auditService);
    }

    private static final class FixedIdGenerator implements IdGenerator {

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
    }
}
