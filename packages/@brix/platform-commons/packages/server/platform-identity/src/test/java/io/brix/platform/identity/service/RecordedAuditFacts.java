package io.brix.platform.identity.service;

import java.util.ArrayList;
import java.util.List;

import io.brix.platform.identity.event.IdentityAuditFact;
import io.brix.platform.identity.event.IdentityAuditFactRecordedEvent;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * Stands in for the identity owner's outbox: keeps the events a test publishes, in order.
 */
final class RecordedAuditFacts implements EventBusCapability {

    private final List<IntegrationEvent> published = new ArrayList<>();
    private final IdentityAuditFacts publisher = new IdentityAuditFacts(this);

    /** The publisher to hand to the code under test. */
    IdentityAuditFacts publisher() {
        return publisher;
    }

    /** The published audit facts, in order. */
    List<IdentityAuditFact> facts() {
        return published.stream()
            .filter(IdentityAuditFactRecordedEvent.class::isInstance)
            .map(event -> ((IdentityAuditFactRecordedEvent) event).payload())
            .toList();
    }

    /** Every published event, in order. */
    List<IntegrationEvent> published() {
        return List.copyOf(published);
    }

    /** The published facts' actions, in order. */
    List<String> actions() {
        return facts().stream().map(IdentityAuditFact::action).toList();
    }

    @Override
    public void publish(DomainEvent event) {
        throw new AssertionError("the identity outbox records integration events only");
    }

    @Override
    public void publishIntegration(IntegrationEvent event) {
        published.add(event);
    }
}
