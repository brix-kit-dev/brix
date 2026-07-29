/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.brix.platform.tenant.entity.PlatformTenantOutbox;
import io.brix.platform.tenant.event.TenantFirstOwnerAcceptedEvent;
import io.brix.platform.tenant.repository.PlatformTenantOutboxRepository;
import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.manifest.model.PluginManifest;
import io.runtime.sdk.capability.EventPublishException;
import io.runtime.sdk.event.IntegrationEvent;

@ExtendWith(MockitoExtension.class)
class PlatformTenantReliableEventBusCapabilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PlatformTenantOutboxRepository outboxRepository;

    private PlatformTenantReliableEventBusCapability capability;

    @BeforeEach
    void setUp() {
        capability = new PlatformTenantReliableEventBusCapability(outboxRepository, objectMapper);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void criticalEventWithoutActiveOwnerTransactionFailsFast() {
        TenantFirstOwnerAcceptedEvent event = new TenantFirstOwnerAcceptedEvent(100L, 200L, 300L, 400L);

        EventPublishException failure = assertThrows(
            EventPublishException.class,
            () -> capability.publishIntegration(event));

        assertEquals(event.getEventId(), failure.getEventId());
        verify(outboxRepository, never()).save(any(PlatformTenantOutbox.class));
    }

    @Test
    void criticalEventAppendsCanonicalOutboxInsideOwnerTransaction() throws Exception {
        beginOwnerTransaction();
        MDC.put("tracestate", "brix=tenant");
        TenantFirstOwnerAcceptedEvent event = new TenantFirstOwnerAcceptedEvent(100L, 200L, 300L, 400L);
        event.withCorrelationId("corr-1")
            .withCausationId("cause-1")
            .withTraceId("4bf92f3577b34da6a3ce929d0e0e4736");
        when(outboxRepository.save(any(PlatformTenantOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        capability.publishIntegration(event);

        ArgumentCaptor<PlatformTenantOutbox> outbox = ArgumentCaptor.forClass(PlatformTenantOutbox.class);
        verify(outboxRepository).save(outbox.capture());
        PlatformTenantOutbox record = outbox.getValue();
        assertEquals(event.getEventId(), record.getMessageId());
        assertEquals(event.getEventId(), record.getEventId());
        assertEquals("EVENT", record.getMessageKind());
        assertEquals(TenantFirstOwnerAcceptedEvent.EVENT_TYPE, record.getMessageType());
        assertEquals(TenantFirstOwnerAcceptedEvent.EVENT_TYPE, record.getEventType());
        assertEquals("1.0.0", record.getSchemaVersion());
        assertEquals("CRITICAL", record.getReliability());
        assertEquals("platform-tenant", record.getProducerPluginId());
        assertEquals("TENANT", record.getScope());
        assertEquals(100L, record.getTenantId());
        assertEquals("100", record.getPartitionKey());
        assertEquals("corr-1", record.getCorrelationId());
        assertEquals("cause-1", record.getCausationId());
        assertTrue(record.getTraceparent().matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$"));
        assertEquals("brix=tenant", record.getTracestate());
        assertEquals("PENDING", record.getStatus());
        assertEquals(0, record.getAttemptCount());
        assertNotNull(record.getAvailableAt());
        assertNotNull(record.getCreatedAt());
        Map<String, Object> payload = objectMapper.readValue(record.getPayload(), new TypeReference<>() {
        });
        assertEquals(100, payload.get("tenantId"));
        assertEquals(200, payload.get("memberId"));
        assertEquals(300, payload.get("profileId"));
        assertEquals(400, payload.get("invitationId"));
        assertFalse(payload.containsKey("eventId"));
    }

    @Test
    void undeclaredPlatformTenantEventIsRejectedBeforeAppend() {
        beginOwnerTransaction();
        IntegrationEvent event = new IntegrationEvent(TenantFirstOwnerAcceptedEvent.SOURCE_MODULE, "100") {
            private static final long serialVersionUID = 1L;

            @Override
            public String getRoutingKey() {
                return "100";
            }
        };

        assertThrows(EventPublishException.class, () -> capability.publishIntegration(event));

        verify(outboxRepository, never()).save(any(PlatformTenantOutbox.class));
    }

    @Test
    void manifestWithoutCanonicalInboxIsRejectedAtConstruction() {
        PluginManifest manifest = new PluginManifestLoader()
            .loadActiveFromClasspath(PlatformTenantReliableEventBusCapability.class.getClassLoader());
        manifest.getData().setInbox(null);

        EventPublishException failure = assertThrows(
            EventPublishException.class,
            () -> new PlatformTenantReliableEventBusCapability(outboxRepository, objectMapper, manifest));

        assertTrue(failure.getMessage().contains("storage, outbox, and inbox"));
    }

    private static void beginOwnerTransaction() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }
}
