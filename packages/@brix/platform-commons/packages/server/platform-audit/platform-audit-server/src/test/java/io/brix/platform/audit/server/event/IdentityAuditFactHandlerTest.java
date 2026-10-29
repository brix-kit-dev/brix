package io.brix.platform.audit.server.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.brix.platform.audit.api.PlatformAuditActions;
import io.brix.platform.audit.api.PlatformAuditWriters;
import io.brix.platform.audit.core.entity.PlatformAuditLog;
import io.brix.platform.audit.core.repository.PlatformAuditLogRepository;
import io.brix.platform.audit.core.service.PlatformAuditService;
import io.brix.platform.audit.internal.PlatformAuditEntryView;
import io.brix.platform.audit.internal.PlatformAuditRejectedException;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.event.EventEnvelope;
import io.runtime.sdk.event.EventReliability;
import io.runtime.sdk.event.EventScope;
import io.runtime.sdk.plugin.CancellationSignal;
import io.runtime.sdk.plugin.EventInvocation;
import io.runtime.sdk.plugin.PublisherDecision;
import io.runtime.sdk.plugin.TraceContext;

/**
 * platform-identity's audit facts become platform audit records written by the owner (P4 design §4.2,
 * D14, D15).
 */
class IdentityAuditFactHandlerTest {

    private static final String TRACEPARENT = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-29T08:00:00Z");

    private final PlatformAuditLogRepository records = mock(PlatformAuditLogRepository.class);
    private final IdGeneratorCapability ids = mock(IdGeneratorCapability.class);
    private final IdentityAuditFactHandler handler = new IdentityAuditFactHandler(new PlatformAuditService(records, ids));

    @Test
    void aFactBecomesAnOwnerRecordAtTheTimeItOccurred() {
        when(ids.generate()).thenReturn("374829105938497541");
        when(records.save(any())).thenAnswer(call -> call.getArgument(0));

        handler.handle(invocation(new IdentityAuditFactPayload(PlatformAuditActions.IDENTITY_ACTIVATED, 7L,
            "platform-identity", "IDENTITY", "7", null, true, "tenant-first-owner-setup", null,
            "4bf92f3577b34da6a3ce929d0e0e4736", null))).toCompletableFuture().join();

        PlatformAuditEntryView view = saved().toView();
        assertThat(view.id()).isEqualTo(374829105938497541L);
        assertThat(view.action()).isEqualTo(PlatformAuditActions.IDENTITY_ACTIVATED);
        assertThat(view.actorIdentityId()).isEqualTo(7L);
        assertThat(view.executionIdentity()).isEqualTo("platform-identity");
        assertThat(view.targetType()).isEqualTo("IDENTITY");
        assertThat(view.targetId()).isEqualTo("7");
        assertThat(view.affectedTenants()).isEmpty();
        assertThat(view.success()).isTrue();
        assertThat(view.reasonCode()).isEqualTo("tenant-first-owner-setup");
        assertThat(view.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(view.writer()).isEqualTo(PlatformAuditWriters.OWNER);
        assertThat(view.createdAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void withoutATraceIdTheRecordTakesTheDeliveryTrace() {
        when(ids.generate()).thenReturn("374829105938497542");
        when(records.save(any())).thenAnswer(call -> call.getArgument(0));

        handler.handle(invocation(new IdentityAuditFactPayload(PlatformAuditActions.BOOTSTRAP_SESSION_OPENED, null,
            "platform-identity", "BOOTSTRAP", null, List.of(), true, "bootstrap", null, null, null)))
            .toCompletableFuture().join();

        PlatformAuditEntryView view = saved().toView();
        assertThat(view.actorIdentityId()).as("the bootstrap request is anonymous").isNull();
        assertThat(view.traceId()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
    }

    @Test
    void aFactTheOwnerRefusesFailsTheDelivery() {
        assertThatThrownBy(() -> handler.handle(invocation(new IdentityAuditFactPayload(PlatformAuditActions.TENANT_CREATED,
                7L, "platform-identity", "TENANT", "910001", List.of(910001L), true, "bootstrap", null, null, null))))
            .isInstanceOfSatisfying(PlatformAuditRejectedException.class,
                rejected -> assertThat(rejected.code()).isEqualTo(PlatformAuditRejectedException.ACTION_REFUSED));
        verify(records, never()).save(any());
    }

    @Test
    void thePayloadHasExactlyTheFieldsOfSchemaOnePointZero() {
        // brix://schemas/platform-identity/identity-audit-fact/1.0.0; platform-identity publishes the same names.
        assertThat(Arrays.stream(IdentityAuditFactPayload.class.getRecordComponents()).map(RecordComponent::getName))
            .containsExactly("action", "actorIdentityId", "executionIdentity", "targetType", "targetId",
                "affectedTenants", "success", "reasonCode", "errorCode", "traceId", "sourceIp");
    }

    @Test
    void aPayloadWithoutItsRequiredFieldsDoesNotDecode() {
        assertThatThrownBy(() -> new IdentityAuditFactPayload(PlatformAuditActions.IDENTITY_ACTIVATED, 7L,
                "platform-identity", "IDENTITY", "7", List.of(), null, "initial-setup", null, null, null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IdentityAuditFactPayload(null, 7L,
                "platform-identity", "IDENTITY", "7", List.of(), true, "initial-setup", null, null, null))
            .isInstanceOf(NullPointerException.class);
    }

    private PlatformAuditLog saved() {
        ArgumentCaptor<PlatformAuditLog> saved = ArgumentCaptor.forClass(PlatformAuditLog.class);
        verify(records).save(saved.capture());
        return saved.getValue();
    }

    private static EventInvocation<IdentityAuditFactPayload> invocation(IdentityAuditFactPayload payload) {
        EventEnvelope<IdentityAuditFactPayload> envelope = new EventEnvelope<>(
            "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
            "IdentityAuditFactRecorded",
            "1.0.0",
            OCCURRED_AT,
            "platform-identity",
            EventScope.PLATFORM,
            null,
            "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
            null,
            TRACEPARENT,
            "IDENTITY:7",
            payload,
            EventReliability.CRITICAL);
        return new EventInvocation<>(envelope, new PublisherDecision("platform-identity", null),
            new TraceContext(TRACEPARENT, null), OCCURRED_AT.plusSeconds(15), CancellationSignal.none(), 1);
    }
}
