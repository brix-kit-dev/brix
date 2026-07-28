/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.runtime.sdk.capability;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import io.runtime.sdk.event.EventEnvelope;
import io.runtime.sdk.event.EventReliability;
import io.runtime.sdk.event.EventScope;

class EventBusCapabilityContractTest {

    @Test
    void eventReliabilityContractUsesThreeManifestLevels() {
        assertEquals(
            EnumSet.of(EventReliability.CRITICAL, EventReliability.STANDARD, EventReliability.BEST_EFFORT),
            EnumSet.allOf(EventReliability.class));
    }

    @Test
    void reliableEnvelopeRequiresPersistentDeliveryOnlyForCriticalAndStandard() {
        assertTrue(envelope(EventReliability.CRITICAL, EventScope.TENANT, "tenant-1")
            .requiresPersistentDelivery());
        assertTrue(envelope(EventReliability.STANDARD, EventScope.TENANT, "tenant-1")
            .requiresPersistentDelivery());
        assertFalse(envelope(EventReliability.BEST_EFFORT, EventScope.PLATFORM, null)
            .requiresPersistentDelivery());
    }

    @Test
    void tenantScopedEnvelopeRequiresTenantId() {
        assertThrows(IllegalArgumentException.class,
            () -> envelope(EventReliability.CRITICAL, EventScope.TENANT, null));
    }

    @Test
    void platformScopedEnvelopeRejectsTenantId() {
        assertThrows(IllegalArgumentException.class,
            () -> envelope(EventReliability.BEST_EFFORT, EventScope.PLATFORM, "tenant-1"));
    }

    @Test
    void platformScopedEnvelopeAllowsNoTenantId() {
        assertDoesNotThrow(() -> envelope(EventReliability.BEST_EFFORT, EventScope.PLATFORM, null));
    }

    private static EventEnvelope<String> envelope(
            EventReliability reliability,
            EventScope scope,
            String tenantId) {
        return new EventEnvelope<>(
            "event-1",
            "TenantFirstOwnerAccepted",
            "1.0.0",
            Instant.parse("2026-07-28T00:00:00Z"),
            "platform-tenant",
            scope,
            tenantId,
            "correlation-1",
            null,
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
            "tenant.1",
            "payload",
            reliability);
    }
}
