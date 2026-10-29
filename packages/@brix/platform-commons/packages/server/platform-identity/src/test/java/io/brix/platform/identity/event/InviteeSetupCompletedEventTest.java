package io.brix.platform.identity.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import io.runtime.sdk.event.EventScope;
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;

/**
 * The setup-completed fact as the identity outbox records it (P4 design §4.3): the manifest's event type,
 * PLATFORM scope, and the payload platform-tenant decodes.
 */
class InviteeSetupCompletedEventTest {

    @Test
    void theFactIsAPlatformEventWithTheIdentityAsItsPayload() {
        InviteeSetupCompletedEvent event = new InviteeSetupCompletedEvent(77L);

        assertEquals("FirstOwnerInviteeSetupCompleted", event.eventTypeId());
        assertEquals(EventScope.PLATFORM, event.scope());
        assertNull(event.getTenantId(), "the runtime binds the invitee's tenants, the fact names none");
        assertEquals(new FirstOwnerInviteeSetupCompletedEvent(77L), event.payload());
        assertEquals("IDENTITY:77", event.getRoutingKey());
    }
}
