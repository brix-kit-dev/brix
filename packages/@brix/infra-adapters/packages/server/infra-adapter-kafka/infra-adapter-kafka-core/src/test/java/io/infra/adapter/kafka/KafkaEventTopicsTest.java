/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.event.ConsumedSubscription;

/**
 * The canonical bus's names live apart from the direct bus's {@code integration.*} topics (Kafka design D18),
 * and each subscription has its own group and parking topic (D19, D20).
 */
class KafkaEventTopicsTest {

    private final ConsumedSubscription subscription =
        new ConsumedSubscription("platform-audit", "identity-audit-fact-record.v1", "IdentityAuditFactRecorded");

    @Test
    void eachEventTypeHasItsOwnTopicUnderTheCanonicalNamespace() {
        KafkaEventTopics topics = new KafkaEventTopics("");

        assertThat(topics.eventTopic("IdentityAuditFactRecorded")).isEqualTo("brix.event.IdentityAuditFactRecorded");
        assertThat(topics.parkingTopic(subscription)).isEqualTo("brix.parked.platform-audit.identity-audit-fact-record.v1");
        assertThat(topics.consumerGroup(subscription))
            .isEqualTo("brix.subscription.platform-audit.identity-audit-fact-record.v1");
    }

    @Test
    void anEnvironmentPrefixComesFirst() {
        KafkaEventTopics topics = new KafkaEventTopics("staging-");

        assertThat(topics.eventTopic("TenantFirstOwnerAccepted")).isEqualTo("staging-brix.event.TenantFirstOwnerAccepted");
        assertThat(topics.consumerGroup(subscription)).startsWith("staging-brix.subscription.");
    }

    @Test
    void aNameKafkaWouldRefuseIsRefusedHere() {
        KafkaEventTopics topics = new KafkaEventTopics(null);

        assertThatThrownBy(() -> topics.eventTopic("Tenant Accepted")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> topics.eventTopic("x".repeat(250))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KafkaEventTopics("bad prefix/")).isInstanceOf(IllegalArgumentException.class);
    }
}
