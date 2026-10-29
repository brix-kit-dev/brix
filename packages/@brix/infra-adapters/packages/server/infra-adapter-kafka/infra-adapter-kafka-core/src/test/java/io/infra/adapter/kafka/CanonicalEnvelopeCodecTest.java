/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * The consumers read back exactly the envelope the relay's transport wrote (root BRX-RS-MESSAGE-021), including
 * the W3C trace context (OTEL-002).
 */
class CanonicalEnvelopeCodecTest {

    private final CanonicalEnvelopeCodec envelopes = new CanonicalEnvelopeCodec(JsonMapper.builder()
        .configureForJackson2()
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build());

    @Test
    void aWrittenEnvelopeReadsBackAsTheSameMessage() throws Exception {
        CanonicalOutboxMessage written = new CanonicalOutboxMessage("msg-1", "evt-1", "EVENT", "TenantFirstOwnerAccepted",
            "1.0.0", "CRITICAL", "platform-tenant", "TENANT", 910001L, "910001",
            OffsetDateTime.parse("2026-10-29T08:59:00.123Z"), "corr-1", "cause-1",
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", "brix=tenant", "{\"tenantId\":910001,\"memberId\":7}", 4);

        CanonicalOutboxMessage read = envelopes.read(envelopes.write(written));

        assertThat(read.messageId()).isEqualTo("msg-1");
        assertThat(read.eventId()).isEqualTo("evt-1");
        assertThat(read.messageKind()).isEqualTo("EVENT");
        assertThat(read.messageType()).isEqualTo("TenantFirstOwnerAccepted");
        assertThat(read.schemaVersion()).isEqualTo("1.0.0");
        assertThat(read.reliability()).isEqualTo("CRITICAL");
        assertThat(read.producerPluginId()).isEqualTo("platform-tenant");
        assertThat(read.scope()).isEqualTo("TENANT");
        assertThat(read.tenantId()).isEqualTo(910001L);
        assertThat(read.partitionKey()).isEqualTo("910001");
        assertThat(read.occurredAt()).isEqualTo(OffsetDateTime.parse("2026-10-29T08:59:00.123Z"));
        assertThat(read.correlationId()).isEqualTo("corr-1");
        assertThat(read.causationId()).isEqualTo("cause-1");
        assertThat(read.traceparent()).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(read.tracestate()).isEqualTo("brix=tenant");
        assertThat(read.payload()).isEqualTo("{\"tenantId\":910001,\"memberId\":7}");
        assertThat(read.attemptCount()).as("a consumer counts its own attempts").isEqualTo(1);
    }

    @Test
    void aPlatformEnvelopeHasNoTenant() throws Exception {
        CanonicalOutboxMessage written = new CanonicalOutboxMessage("msg-2", "evt-2", "EVENT", "IdentityAuditFactRecorded",
            "1.0.0", "CRITICAL", "platform-identity", "PLATFORM", null, "IDENTITY:7",
            OffsetDateTime.parse("2026-10-29T09:00:00Z"), "corr-2", null, null, null, "{\"action\":\"IDENTITY_ACTIVATED\"}", 1);

        CanonicalOutboxMessage read = envelopes.read(envelopes.write(written));

        assertThat(read.tenantId()).isNull();
        assertThat(read.causationId()).isNull();
        assertThat(read.traceparent()).isNull();
    }

    @Test
    void aRecordThatIsNoEnvelopeIsRefused() {
        assertThatThrownBy(() -> envelopes.read(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> envelopes.read("not json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> envelopes.read("[1,2]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> envelopes.read("{\"eventId\":\"e\"}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> envelopes.read("{\"messageId\":\"m\",\"messageKind\":\"EVENT\",\"eventType\":\"T\","
            + "\"schemaVersion\":\"1.0.0\",\"reliability\":\"CRITICAL\",\"producerPluginId\":\"p\",\"scope\":\"TENANT\","
            + "\"partitionKey\":\"k\",\"occurredAt\":\"2026-10-29T09:00:00Z\",\"correlationId\":\"c\",\"payload\":{}}"))
            .as("a TENANT envelope needs its tenant")
            .isInstanceOf(IllegalArgumentException.class);
    }
}
