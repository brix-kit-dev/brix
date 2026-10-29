package io.brix.platform.identity.event;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.brix.platform.identity.PlatformIdentityPlugin;
import io.runtime.orchestrator.event.conformance.EventSchemaContract;
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;

/**
 * Each event the identity owner publishes has its schema file, and the schema matches the payload the owner
 * writes (Kafka design D24).
 */
class IdentityEventSchemasTest {

    @Test
    void everyPublishedEventMatchesItsSchema() {
        EventSchemaContract.verify(PlatformIdentityPlugin.class, Map.of(
            IdentityAuditFactRecordedEvent.EVENT_TYPE, new IdentityAuditFact("SUPER_ADMIN_LOGIN_SUCCESS", 7L,
                "platform-identity", "IDENTITY", "7", List.of(910001L), true, "login", null, "4bf92f3577b34da6a3ce929d0e0e4736",
                "10.0.0.x"),
            InviteeSetupCompletedEvent.EVENT_TYPE, new FirstOwnerInviteeSetupCompletedEvent(7L)));
    }

    @Test
    void aFactWithoutActorOrTenantsStillMatches() {
        // A failed login names no actor and reaches no tenant.
        EventSchemaContract.verify(PlatformIdentityPlugin.class, Map.of(
            IdentityAuditFactRecordedEvent.EVENT_TYPE, new IdentityAuditFact("SUPER_ADMIN_LOGIN_FAILED", null,
                "platform-identity", "IDENTITY", null, null, false, "login-failures-exceeded", "AUTH_FAILED", null, null),
            InviteeSetupCompletedEvent.EVENT_TYPE, new FirstOwnerInviteeSetupCompletedEvent(7L)));
    }
}
