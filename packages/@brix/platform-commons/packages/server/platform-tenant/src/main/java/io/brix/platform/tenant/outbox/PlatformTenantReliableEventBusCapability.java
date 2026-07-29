/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.outbox;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.brix.platform.tenant.entity.PlatformTenantOutbox;
import io.brix.platform.tenant.event.TenantFirstOwnerAcceptedEvent;
import io.brix.platform.tenant.repository.PlatformTenantOutboxRepository;
import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.manifest.model.PluginManifest;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.EventPublishException;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.EventReliability;
import io.runtime.sdk.event.EventScope;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * Owner-scoped reliable EventBus policy for {@code platform-tenant} producers.
 *
 * <p>The service layer only sees the L2A {@link EventBusCapability}. This
 * component validates the active manifest declaration, requires an active local
 * transaction for durable events, builds the canonical producer envelope and
 * appends the owner outbox record through the owner repository.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public final class PlatformTenantReliableEventBusCapability implements EventBusCapability {

    private static final String STORAGE_ID = "platform_tenant";
    private static final String OUTBOX_TABLE = "platform_tenant_outbox";
    private static final String INBOX_TABLE = "platform_tenant_inbox";
    private static final String MESSAGE_KIND_EVENT = "EVENT";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_TRACEPARENT = "traceparent";
    private static final String MDC_TRACESTATE = "tracestate";
    private static final String W3C_FLAGS_SAMPLED = "01";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlatformTenantOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final PluginManifest manifest;

    /**
     * Creates the reliable producer policy with the active classpath manifest.
     *
     * @param outboxRepository owner outbox repository
     * @param objectMapper JSON mapper
     */
    public PlatformTenantReliableEventBusCapability(
            PlatformTenantOutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        this(outboxRepository, objectMapper, new PluginManifestLoader()
            .loadActiveFromClasspath(PlatformTenantReliableEventBusCapability.class.getClassLoader()));
    }

    /**
     * Creates the reliable producer policy with an explicit manifest.
     *
     * @param outboxRepository owner outbox repository
     * @param objectMapper JSON mapper
     * @param manifest active plugin manifest
     */
    public PlatformTenantReliableEventBusCapability(
            PlatformTenantOutboxRepository outboxRepository,
            ObjectMapper objectMapper,
            PluginManifest manifest) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.manifest = Objects.requireNonNull(manifest, "manifest must not be null");
        validateOwnerDataDeclaration(manifest);
    }

    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        throw new EventPublishException("platform-tenant reliable EventBus accepts integration events only");
    }

    @Override
    public void publishIntegration(IntegrationEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        EventPolicy policy = resolvePolicy(event);
        if (policy.reliability() == EventReliability.BEST_EFFORT) {
            throw new EventPublishException(event.getEventId(),
                "platform-tenant reliable EventBus does not provide BEST_EFFORT transport", null);
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new EventPublishException(event.getEventId(),
                "CRITICAL/STANDARD integration events require an active owner transaction", null);
        }
        outboxRepository.save(toOutbox(event, policy));
    }

    private PlatformTenantOutbox toOutbox(IntegrationEvent event, EventPolicy policy) {
        TenantFirstOwnerAcceptedEvent accepted = requireTenantFirstOwnerAccepted(event);
        String tenantId = requireText(event.getTenantId(), "tenantId");
        String correlationId = firstText(event.getCorrelationId(), MDC.get(MDC_CORRELATION_ID), event.getEventId());
        String traceparent = traceparent(event.getTraceId());
        PlatformTenantOutbox outbox = new PlatformTenantOutbox();
        outbox.setMessageId(event.getEventId());
        outbox.setEventId(event.getEventId());
        outbox.setMessageKind(MESSAGE_KIND_EVENT);
        outbox.setMessageType(policy.eventType());
        outbox.setEventType(policy.eventType());
        outbox.setSchemaVersion(policy.schemaVersion());
        outbox.setReliability(policy.reliability().name());
        outbox.setProducerPluginId(manifest.pluginId());
        outbox.setScope(EventScope.TENANT.name());
        outbox.setTenantId(Long.valueOf(tenantId));
        outbox.setPartitionKey(event.getRoutingKey());
        outbox.setOccurredAt(OffsetDateTime.ofInstant(event.getTimestamp(), ZoneOffset.UTC));
        outbox.setCorrelationId(correlationId);
        outbox.setCausationId(event.getCausationId());
        outbox.setTraceparent(traceparent);
        outbox.setTracestate(MDC.get(MDC_TRACESTATE));
        outbox.setPayload(writePayload(event.getEventId(), accepted));
        outbox.setStatus("PENDING");
        outbox.setAvailableAt(OffsetDateTime.now(ZoneOffset.UTC));
        outbox.setAttemptCount(0);
        outbox.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return outbox;
    }

    private String writePayload(String eventId, TenantFirstOwnerAcceptedEvent event) {
        try {
            return objectMapper.writeValueAsString(event.payload());
        } catch (JsonProcessingException ex) {
            throw new EventPublishException(eventId, "Failed to serialize integration event payload", ex);
        }
    }

    private EventPolicy resolvePolicy(IntegrationEvent event) {
        String eventType = eventTypeId(event);
        PluginManifest.EventPublish publish = publications().stream()
            .filter(candidate -> eventType.equals(candidate.getId()))
            .findFirst()
            .orElseThrow(() -> new EventPublishException(event.getEventId(),
                "Integration event is not declared in platform-tenant manifest: " + eventType, null));
        EventReliability reliability;
        try {
            reliability = EventReliability.valueOf(publish.getReliability());
        } catch (IllegalArgumentException ex) {
            throw new EventPublishException(event.getEventId(),
                "Invalid manifest reliability for integration event: " + eventType, ex);
        }
        return new EventPolicy(publish.getId(), publish.getVersion(), reliability);
    }

    private List<PluginManifest.EventPublish> publications() {
        if (manifest.getEvents() == null) {
            return List.of();
        }
        return manifest.getEvents().getPublishes();
    }

    private static String eventTypeId(IntegrationEvent event) {
        if (event instanceof TenantFirstOwnerAcceptedEvent accepted) {
            return accepted.eventTypeId();
        }
        String eventType = event.getEventType();
        int lastDot = eventType.lastIndexOf('.');
        return lastDot >= 0 ? eventType.substring(lastDot + 1) : eventType;
    }

    private static TenantFirstOwnerAcceptedEvent requireTenantFirstOwnerAccepted(IntegrationEvent event) {
        if (event instanceof TenantFirstOwnerAcceptedEvent accepted) {
            return accepted;
        }
        throw new EventPublishException(event.getEventId(),
            "platform-tenant producer does not own event type: " + event.getEventType(), null);
    }

    private static void validateOwnerDataDeclaration(PluginManifest manifest) {
        PluginManifest.DataSection data = manifest.getData();
        if (data == null
                || !STORAGE_ID.equals(data.getStorageId())
                || !OUTBOX_TABLE.equals(data.getOutbox())
                || !INBOX_TABLE.equals(data.getInbox())) {
            throw new EventPublishException(
                "platform-tenant manifest must declare platform_tenant storage, outbox, and inbox");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new EventPublishException(field + " must not be blank");
        }
        return value;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String traceparent(String traceId) {
        String mdcTraceparent = MDC.get(MDC_TRACEPARENT);
        if (isTraceparent(mdcTraceparent)) {
            return mdcTraceparent;
        }
        if (isTraceparent(traceId)) {
            return traceId;
        }
        if (isTraceId(traceId)) {
            return "00-" + traceId + "-" + randomHex(8) + "-" + W3C_FLAGS_SAMPLED;
        }
        return newTraceparent();
    }

    private static boolean isTraceparent(String value) {
        return value != null
            && value.matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$")
            && !value.substring(3, 35).equals("00000000000000000000000000000000")
            && !value.substring(36, 52).equals("0000000000000000");
    }

    private static boolean isTraceId(String value) {
        return value != null
            && value.matches("^[0-9a-f]{32}$")
            && !value.equals("00000000000000000000000000000000");
    }

    private static String newTraceparent() {
        return "00-" + randomHex(16) + "-" + randomHex(8) + "-" + W3C_FLAGS_SAMPLED;
    }

    private static String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

    private record EventPolicy(String eventType, String schemaVersion, EventReliability reliability) {
    }
}
