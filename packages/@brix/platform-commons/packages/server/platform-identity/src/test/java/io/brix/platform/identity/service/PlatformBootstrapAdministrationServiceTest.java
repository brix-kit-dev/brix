package io.brix.platform.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import io.brix.platform.audit.api.PlatformAuditActions;
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

    private final RecordedAuditFacts audit = new RecordedAuditFacts();

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
        verifyNoInteractions(identityRepository, setupTokenRepository);
        assertEquals(List.of(), audit.facts());
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
        // Point 10b-7a (D99): the singleton keeps the session's jti, which the runtime names after verifying the token.
        ArgumentCaptor<JwtIssuerCapability.BootstrapSetupTokenRequest> issued =
                ArgumentCaptor.forClass(JwtIssuerCapability.BootstrapSetupTokenRequest.class);
        verify(issuer).issueBootstrapSetupToken(issued.capture());
        assertEquals(issued.getValue().jti(), saved.getBootstrapSessionJti());
        assertFalse(saved.isCompleted());
        verify(identityRepository, never()).save(any(Identity.class));
        verify(platformAdminRepository, never()).save(any(PlatformAdmin.class));
        verify(setupTokenRepository, never()).save(any(SetupToken.class));
        verify(issuer).issueBootstrapSetupToken(argThat(request ->
                request.permissions().equals(List.of(
                        "bootstrap:status",
                        "bootstrap:session",
                        "bootstrap:create-first-admin"))));
        assertEquals(List.of(PlatformAuditActions.BOOTSTRAP_SESSION_OPENED), audit.actions());
        assertEquals(null, audit.facts().get(0).actorIdentityId(), "the bootstrap request is anonymous");
        assertEquals(IdentityAuditFacts.REASON_BOOTSTRAP, audit.facts().get(0).reasonCode());
    }

    @Test
    void createFirstAdminFlushesIdentityAndAdminBeforeIssuingSetupToken() {
        BootstrapState state = new BootstrapState();
        state.activateSession("session-jti", OffsetDateTime.now().plusMinutes(5));

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
                audit.publisher());

        service.createFirstAdmin(new CreateFirstPlatformAdminCommand(
                "session-jti",
                "Owner",
                "owner@example.invalid",
                null));
        assertFalse(state.isBootstrapSessionUsable("session-jti", OffsetDateTime.now()),
                "the same transaction spends the session");

        InOrder order = inOrder(identityRepository, platformAdminRepository, setupTokenRepository);
        order.verify(identityRepository).save(any(Identity.class));
        order.verify(identityRepository).flush();
        order.verify(platformAdminRepository).save(any(PlatformAdmin.class));
        order.verify(platformAdminRepository).flush();
        order.verify(setupTokenRepository).save(any(SetupToken.class));
        verify(notification).send(any());
        assertEquals(List.of(PlatformAuditActions.BOOTSTRAP_ADMIN_CREATED, PlatformAuditActions.SETUP_TOKEN_ISSUED), audit.actions());
        List<String> targetTypes = audit.facts().stream().map(fact -> fact.targetType()).toList();
        assertEquals(List.of("PLATFORM_ADMIN", "SETUP_TOKEN"), targetTypes);
        assertEquals(true, audit.facts().stream()
                .allMatch(fact -> IdentityAuditFacts.REASON_BOOTSTRAP.equals(fact.reasonCode())));
    }

    @Test
    void createFirstAdminAcceptsOnlyTheUnusedUnexpiredSessionOpenedLast() {
        // Point 10b-7a (D96, D99): the runtime verified a token; the owner still decides whether its session is usable.
        PlatformBootstrapAdministrationService service = service(Optional.empty(), Optional.of(mock(NotificationCapability.class)));
        when(platformAdminRepository.countCompletedFormalSuperAdmins()).thenReturn(0L);

        BootstrapState replaced = new BootstrapState();
        replaced.activateSession("older-session", OffsetDateTime.now().plusMinutes(5));
        replaced.activateSession("newer-session", OffsetDateTime.now().plusMinutes(5));
        BootstrapState spent = new BootstrapState();
        spent.activateSession("spent-session", OffsetDateTime.now().plusMinutes(5));
        spent.consumeSession();
        BootstrapState expired = new BootstrapState();
        expired.activateSession("expired-session", OffsetDateTime.now().minusSeconds(1));
        when(bootstrapStateRepository.findByIdForUpdate(BootstrapState.SINGLETON_ID))
                .thenReturn(Optional.of(replaced), Optional.of(spent), Optional.of(expired));

        for (String session : List.of("older-session", "spent-session", "expired-session")) {
            IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                    () -> service.createFirstAdmin(new CreateFirstPlatformAdminCommand(
                            session, "Owner", "owner@example.invalid", null)), session);
            assertEquals("bootstrap session is invalid", rejected.getMessage(), session);
        }
        verify(identityRepository, never()).save(any(Identity.class));
        verify(platformAdminRepository, never()).save(any(PlatformAdmin.class));
        verify(setupTokenRepository, never()).save(any(SetupToken.class));
        verify(bootstrapStateRepository, never()).save(any(BootstrapState.class));
        assertEquals(List.of(), audit.facts());
    }

    @Test
    void rejectedSetupCodeWritesNoAuditRowThatTheRollbackWouldDiscard() {
        when(bootstrapStateRepository.findByIdForUpdate(BootstrapState.SINGLETON_ID))
                .thenReturn(Optional.empty());
        when(platformAdminRepository.countCompletedFormalSuperAdmins()).thenReturn(0L);

        PlatformBootstrapAdministrationService service = service(Optional.empty(), Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.openSession(new BootstrapSessionCommand("wrong-code")));
        assertEquals(List.of(), audit.facts());
        verify(bootstrapStateRepository, never()).save(any(BootstrapState.class));
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
                audit.publisher());
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
