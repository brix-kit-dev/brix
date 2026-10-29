/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.slf4j.MDC;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import io.runtime.manifest.model.PluginManifest;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.EventPublishException;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.EventReliability;
import io.runtime.sdk.event.EventScope;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * Publishes one owner's integration events into its own outbox, in its own transaction (root
 * BRX-RS-MESSAGE-017, MESSAGE-018, MESSAGE-021, MESSAGE-022; P4 design D13).
 *
 * <p>Only the CRITICAL and STANDARD events the owner's manifest declares under {@code events.publishes}
 * are accepted. The envelope is written to the owner's {@code data.outbox} on the connection of the
 * owner's current unit of work, so it commits or rolls back with the business change. Outside that unit
 * of work, including inside another owner's (root BRX-RS-DATA-008) or a read-only one, the publish fails
 * at once. A TENANT event may only name a tenant the transaction already reaches: subscribers run in that
 * tenant's scope, so the producer must not choose it freely. The relay delivers the committed rows.</p>
 */
final class OwnerOutboxEventBus implements EventBusCapability {

    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_TRACEPARENT = "traceparent";
    private static final String MDC_TRACESTATE = "tracestate";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String ownerId;
    private final String outbox;
    private final Map<String, PluginManifest.EventPublish> publications;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private volatile String payloadPlaceholder;

    OwnerOutboxEventBus(
            String ownerId,
            String outbox,
            List<PluginManifest.EventPublish> publications,
            DataSource dataSource,
            ObjectMapper json,
            Clock clock) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.outbox = OwnerTenantTables.normalize(Objects.requireNonNull(outbox, "outbox must not be null"));
        if (!this.outbox.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException(ownerId + " declares an invalid data.outbox: " + outbox);
        }
        this.publications = publications.stream()
            .collect(Collectors.toUnmodifiableMap(PluginManifest.EventPublish::getEventType, Function.identity()));
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.json = Objects.requireNonNull(json, "json must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        throw new EventPublishException("The outbox of " + ownerId + " records integration events only");
    }

    @Override
    public void publishIntegration(IntegrationEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        String eventType = event.eventTypeId();
        PluginManifest.EventPublish publication = publications.get(eventType);
        if (publication == null) {
            throw rejected(event, eventType + " is not declared under events.publishes of " + ownerId);
        }
        EventReliability reliability = reliability(event, publication);
        if (reliability == EventReliability.BEST_EFFORT) {
            throw rejected(event, eventType + " is BEST_EFFORT, which is not written to an outbox");
        }
        OwnerTransactionScope transaction = OwnerTransactionScope.joinable(ownerId);
        if (transaction == null) {
            throw rejected(event, eventType + " is published only inside a unit of work of " + ownerId);
        }
        if (transaction.readOnly()) {
            throw rejected(event, eventType + " cannot be published from a read-only unit of work");
        }
        EventScope scope = event.scope();
        Long tenantId = tenantOf(event, scope, transaction);
        Instant now = clock.instant();
        jdbc.update("INSERT INTO " + outbox + " (message_id, event_id, message_kind, message_type, event_type,"
                + " schema_version, reliability, producer_plugin_id, scope, tenant_id, partition_key, occurred_at,"
                + " correlation_id, causation_id, traceparent, tracestate, payload, status, available_at,"
                + " attempt_count, created_at)"
                + " VALUES (?, ?, 'EVENT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " + payloadPlaceholder()
                + ", 'PENDING', ?, 0, ?)",
            event.getEventId(),
            event.getEventId(),
            eventType,
            eventType,
            publication.getVersion(),
            reliability.name(),
            ownerId,
            scope.name(),
            tenantId,
            firstText(event.getRoutingKey(), event.getEventId()),
            Timestamp.from(event.getTimestamp()),
            firstText(event.getCorrelationId(), MDC.get(MDC_CORRELATION_ID), event.getEventId()),
            event.getCausationId(),
            traceparent(event.getTraceId()),
            MDC.get(MDC_TRACESTATE),
            payload(event),
            Timestamp.from(now),
            Timestamp.from(now));
    }

    private Long tenantOf(IntegrationEvent event, EventScope scope, OwnerTransactionScope transaction) {
        String declared = event.getTenantId();
        if (scope == EventScope.PLATFORM) {
            if (declared != null && !declared.isBlank()) {
                throw rejected(event, "a PLATFORM event must not carry a tenant");
            }
            return null;
        }
        if (scope != EventScope.TENANT) {
            throw rejected(event, "an outbox event is TENANT or PLATFORM, not " + scope);
        }
        long tenantId;
        try {
            tenantId = Long.parseLong(declared);
        } catch (NumberFormatException invalid) {
            throw rejected(event, "a TENANT event needs a numeric tenant");
        }
        if (!transaction.tenancy().tenants().contains(tenantId)) {
            throw rejected(event, "a TENANT event may only name a tenant this unit of work reaches");
        }
        return tenantId;
    }

    private EventReliability reliability(IntegrationEvent event, PluginManifest.EventPublish publication) {
        try {
            return EventReliability.valueOf(publication.getReliability().toUpperCase(Locale.ROOT));
        } catch (RuntimeException invalid) {
            throw rejected(event, ownerId + " declares " + publication.getEventType() + " without a valid reliability");
        }
    }

    private String payload(IntegrationEvent event) {
        try {
            return json.writeValueAsString(event.payload());
        } catch (JacksonException | UnsupportedOperationException invalid) {
            throw new EventPublishException(event.getEventId(), event.eventTypeId() + " has no payload to record", invalid);
        }
    }

    /** PostgreSQL stores the payload as JSONB and needs the bound text cast; H2 and others take it as is. */
    private String payloadPlaceholder() {
        String placeholder = payloadPlaceholder;
        if (placeholder == null) {
            String product = jdbc.execute((ConnectionCallback<String>) connection ->
                connection.getMetaData().getDatabaseProductName());
            placeholder = product != null && product.toLowerCase(Locale.ROOT).contains("postgresql")
                ? "CAST(? AS JSONB)"
                : "?";
            payloadPlaceholder = placeholder;
        }
        return placeholder;
    }

    private EventPublishException rejected(IntegrationEvent event, String reason) {
        return new EventPublishException(event.getEventId(), reason, null);
    }

    private static String traceparent(String traceId) {
        String current = MDC.get(MDC_TRACEPARENT);
        if (isTraceparent(current)) {
            return current;
        }
        if (isTraceparent(traceId)) {
            return traceId;
        }
        String trace = traceId != null && traceId.matches("[0-9a-f]{32}") && !traceId.matches("0{32}")
            ? traceId
            : randomHex(16);
        return "00-" + trace + "-" + randomHex(8) + "-01";
    }

    private static boolean isTraceparent(String value) {
        return value != null
            && value.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")
            && !value.substring(3, 35).matches("0{32}")
            && !value.substring(36, 52).matches("0{16}");
    }

    private static String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
