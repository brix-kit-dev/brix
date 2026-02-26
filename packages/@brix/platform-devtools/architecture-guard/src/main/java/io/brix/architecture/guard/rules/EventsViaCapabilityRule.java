/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import io.brix.architecture.guard.ArchitectureLayers;

/**
 * Events Must Be Published Via EventBusCapability Rule.
 *
 * <p>Direct use of Spring ApplicationEventPublisher is forbidden.
 * All event publishing/subscribing must go through EventBusCapability
 * to ensure events can propagate across processes (e.g., via Kafka).</p>
 *
 * <h2>Checks</h2>
 * <ul>
 *   <li>No dependency on ApplicationEventPublisher (publishing)</li>
 *   <li>No @EventListener annotation (subscribing)</li>
 *   <li>No extending ApplicationEvent (custom events)</li>
 * </ul>
 *
 * @since 3.1.0
 */
public final class EventsViaCapabilityRule {

    private EventsViaCapabilityRule() {}

    /** Forbid direct use of Spring ApplicationEventPublisher. */
    public static ArchRule rule() {
        return noClasses()
                .should().dependOnClassesThat()
                .haveFullyQualifiedName(ArchitectureLayers.APPLICATION_EVENT_PUBLISHER)
                .because("Direct Spring ApplicationEventPublisher usage is forbidden. " +
                        "Use EventBusCapability for cross-process event propagation");
    }

    /**
     * Forbid @EventListener annotation in business modules.
     *
     * <p>Event subscription must go through EventBusCapability.subscribe(),
     * not Spring's @EventListener annotation.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noEventListenerAnnotation() {
        return noMethods()
                .should().beAnnotatedWith(ArchitectureLayers.EVENT_LISTENER)
                .because("@EventListener annotation is forbidden in business modules. " +
                        "Use EventBusCapability.subscribe() for event subscription");
    }

    /**
     * Forbid extending Spring ApplicationEvent.
     *
     * <p>Custom events should use runtime-sdk-api event types,
     * not Spring's ApplicationEvent hierarchy.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noApplicationEventSubclass() {
        return noClasses()
                .should().beAssignableTo(ArchitectureLayers.APPLICATION_EVENT)
                .because("Extending ApplicationEvent is forbidden. " +
                        "Use DomainEvent or IntegrationEvent from runtime-sdk-api");
    }
}
