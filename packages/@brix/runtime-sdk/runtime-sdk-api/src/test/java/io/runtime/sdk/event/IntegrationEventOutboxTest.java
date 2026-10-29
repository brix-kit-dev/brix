/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.sdk.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * What an owner outbox reads from an integration event to build the envelope (root BRX-RS-MESSAGE-021).
 */
class IntegrationEventOutboxTest {

    static final class TenantFirstOwnerAcceptedEvent extends IntegrationEvent {
        TenantFirstOwnerAcceptedEvent(String tenantId) {
            super("platform-tenant", tenantId);
        }

        @Override
        public String getRoutingKey() {
            return "tenant";
        }
    }

    static final class Event extends IntegrationEvent {
        Event() {
            super("platform-tenant");
        }

        @Override
        public String getRoutingKey() {
            return "event";
        }
    }

    @Test
    void theManifestEventTypeIsTheClassNameWithoutItsEventSuffix() {
        assertEquals("TenantFirstOwnerAccepted", new TenantFirstOwnerAcceptedEvent("1").eventTypeId());
        assertEquals("Event", new Event().eventTypeId(), "a class named only Event keeps its name");
    }

    @Test
    void anEventWithATenantIsTenantScopedOtherwisePlatformScoped() {
        assertEquals(EventScope.TENANT, new TenantFirstOwnerAcceptedEvent("910001").scope());
        assertEquals(EventScope.PLATFORM, new TenantFirstOwnerAcceptedEvent(null).scope());
        assertEquals(EventScope.PLATFORM, new TenantFirstOwnerAcceptedEvent(" ").scope());
    }

    @Test
    void anEventMustDefineThePayloadAnOutboxRecords() {
        assertThrows(UnsupportedOperationException.class, () -> new Event().payload());
    }
}
