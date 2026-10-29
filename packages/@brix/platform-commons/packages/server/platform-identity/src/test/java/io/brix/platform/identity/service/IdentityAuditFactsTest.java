package io.brix.platform.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import io.brix.platform.auth.AuditAction;
import io.brix.platform.identity.event.IdentityAuditFact;
import io.brix.platform.identity.event.IdentityAuditFactRecordedEvent;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.event.EventScope;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * The identity owner's audit facts (P4 design §4.2, D14): PLATFORM events with typed fields only.
 */
class IdentityAuditFactsTest {

    @AfterEach
    void clearLogContext() {
        MDC.clear();
    }

    @Test
    void aFactIsAPlatformEventTheManifestDeclares() {
        EventBusCapability outbox = mock(EventBusCapability.class);

        new IdentityAuditFacts(outbox).record(AuditAction.IDENTITY_ACTIVATED, 7L, "IDENTITY", "7", "initial-setup");

        ArgumentCaptor<IntegrationEvent> published = ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(outbox).publishIntegration(published.capture());
        IntegrationEvent event = published.getValue();
        assertEquals(IdentityAuditFactRecordedEvent.EVENT_TYPE, event.eventTypeId());
        assertEquals(EventScope.PLATFORM, event.scope());
        assertNull(event.getTenantId(), "an identity audit fact belongs to no tenant");
        assertEquals("IDENTITY:7", event.getRoutingKey());
        assertEquals(new IdentityAuditFact(AuditAction.IDENTITY_ACTIVATED, 7L, IdentityAuditFacts.EXECUTION_IDENTITY,
                "IDENTITY", "7", List.of(), true, "initial-setup", null, null, null), event.payload());
    }

    @Test
    void thePayloadHasExactlyTheFieldsOfSchemaOnePointZero() {
        // brix://schemas/platform-identity/identity-audit-fact/1.0.0; platform-audit decodes the same names.
        assertEquals(List.of("action", "actorIdentityId", "executionIdentity", "targetType", "targetId",
                "affectedTenants", "success", "reasonCode", "errorCode", "traceId", "sourceIp"),
            Arrays.stream(IdentityAuditFact.class.getRecordComponents()).map(RecordComponent::getName).toList());
    }

    @Test
    void theClientAddressLeavesAsItsNetworkPrefix() {
        RecordedAuditFacts audit = new RecordedAuditFacts();

        audit.publisher().record(AuditAction.IDENTITY_LOCKED, 7L, "IDENTITY", "7",
            IdentityAuditFacts.REASON_LOGIN_FAILURES, "203.0.113.45");
        audit.publisher().record(AuditAction.IDENTITY_LOCKED, 8L, "IDENTITY", "8",
            IdentityAuditFacts.REASON_LOGIN_FAILURES, "not an address");

        assertEquals("203.0.113.0/24", audit.facts().get(0).sourceIp());
        assertNull(audit.facts().get(1).sourceIp());
    }

    @Test
    void theTraceIdIsTheInvocationsWhenTheLogContextHasOne() {
        EventBusCapability outbox = mock(EventBusCapability.class);
        IdentityAuditFacts facts = new IdentityAuditFacts(outbox);

        MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        facts.record(AuditAction.TOTP_BOUND, 7L, "IDENTITY", null, "initial-setup");
        MDC.put("traceId", "not a trace id!");
        facts.record(AuditAction.TOTP_BOUND, 7L, "IDENTITY", null, "initial-setup");
        MDC.clear();
        facts.record(AuditAction.TOTP_BOUND, 7L, "IDENTITY", null, "initial-setup");

        ArgumentCaptor<IntegrationEvent> published = ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(outbox, org.mockito.Mockito.times(3)).publishIntegration(published.capture());
        List<IntegrationEvent> events = published.getAllValues();
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", ((IdentityAuditFact) events.get(0).payload()).traceId());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", events.get(0).getTraceId(),
            "the outbox envelope continues the same trace");
        assertTrue(events.subList(1, 3).stream().allMatch(event -> ((IdentityAuditFact) event.payload()).traceId() == null),
            "without a usable trace id the platform audit owner takes the delivery's trace");
    }

    @Test
    void aFactNeedsAStableReason() {
        IdentityAuditFacts facts = new IdentityAuditFacts(mock(EventBusCapability.class));

        assertThrows(NullPointerException.class,
            () -> facts.record(AuditAction.IDENTITY_ACTIVATED, 7L, "IDENTITY", "7", null));
    }

    @Test
    void theSetupReasonIsTheTokenPurpose() {
        assertEquals("initial-setup", IdentityAuditFacts.setupReason(SetupTokenPurposes.INITIAL_SETUP));
        assertEquals("tenant-first-owner-setup", IdentityAuditFacts.setupReason(SetupTokenPurposes.TENANT_FIRST_OWNER_SETUP));
    }
}
