/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.event;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.orchestrator.plugin.PluginRuntimeManager.BoundEventHandler;

/**
 * Decides the scope of each delivery (multi-tenant §6.4; P4 design §7.3).
 */
@FunctionalInterface
public interface EventDeliveryScopes {

    /** TENANT events in their envelope's tenant, PLATFORM events without a tenant. */
    EventDeliveryScopes ENVELOPE = (bound, message, payload) -> envelope(message);

    /**
     * The scope a subscriber handles a message in.
     *
     * @param bound the subscriber's handler and manifest subscription
     * @param message the delivered message
     * @param payload the decoded payload
     * @return the scope
     */
    EventDeliveryScope scopeOf(BoundEventHandler bound, CanonicalOutboxMessage message, Object payload);

    /**
     * The scope the envelope alone gives: its tenant for a TENANT event, none for a PLATFORM event.
     *
     * @param message the delivered message
     * @return the scope
     */
    static EventDeliveryScope envelope(CanonicalOutboxMessage message) {
        TenantScope scope = "TENANT".equals(message.scope())
            ? TenantScope.tenant(message.tenantId())
            : TenantScope.none();
        return () -> scope;
    }
}
