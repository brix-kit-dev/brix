/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.event;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.brix.platform.tenant.PlatformTenantPlugin;
import io.runtime.orchestrator.event.conformance.EventSchemaContract;

/**
 * The event the tenant owner publishes has its schema file, and the schema matches the payload the owner writes
 * (Kafka design D24).
 */
class TenantEventSchemasTest {

    @Test
    void everyPublishedEventMatchesItsSchema() {
        EventSchemaContract.verify(PlatformTenantPlugin.class, Map.of(
            TenantFirstOwnerAcceptedEvent.EVENT_TYPE, new TenantFirstOwnerAcceptedPayload(910001L, 31L, 41L, 51L)));
    }

    @Test
    void aPayloadTheSchemaDoesNotDescribeFailsTheBuild() {
        record Renamed(Long tenantId, Long memberId, Long profileId, Long invitation) {
        }

        assertThrows(AssertionError.class, () -> EventSchemaContract.verify(PlatformTenantPlugin.class,
            Map.of(TenantFirstOwnerAcceptedEvent.EVENT_TYPE, new Renamed(910001L, 31L, 41L, 51L))));
    }
}
