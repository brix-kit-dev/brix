/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.CreateTopicsOptions;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.runtime.orchestrator.event.ConsumedSubscription;

/**
 * The broker view probes reachability sparingly and creates the topics the manifests need (Kafka design §4.3, §4.4,
 * D18, D21).
 */
class KafkaEventBrokerTest {

    private final Admin admin = mock(Admin.class);
    private final MutableClock clock = new MutableClock();
    private final KafkaEventBroker broker = new KafkaEventBroker(admin, new KafkaEventTopics(""), KafkaEventSettings.defaults(),
        clock);

    @Test
    void aProbeIsTrustedForAFewSeconds() {
        clusterAnswers(true);

        assertThat(broker.reachable()).isTrue();
        assertThat(broker.reachable()).isTrue();
        verify(admin, times(1)).describeCluster(any(DescribeClusterOptions.class));

        clock.advance(KafkaEventSettings.defaults().readyCacheTtl());
        clusterAnswers(false);

        assertThat(broker.reachable()).isFalse();
        verify(admin, times(2)).describeCluster(any(DescribeClusterOptions.class));
    }

    @Test
    void onlyMissingTopicsAreCreatedWithTheConfiguredPartitionsAndRetention() {
        topicsExist("brix.event.TenantFirstOwnerAccepted");
        CreateTopicsResult created = created(Map.of("brix.event.IdentityAuditFactRecorded", KafkaFuture.completedFuture(null)));
        when(admin.createTopics(anyCollection(), any(CreateTopicsOptions.class))).thenReturn(created);

        broker.ensureEventTopics(Set.of("TenantFirstOwnerAccepted", "IdentityAuditFactRecorded"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<NewTopic>> requested = ArgumentCaptor.forClass(Collection.class);
        verify(admin).createTopics(requested.capture(), any(CreateTopicsOptions.class));
        assertThat(requested.getValue()).singleElement().satisfies(topic -> {
            assertThat(topic.name()).isEqualTo("brix.event.IdentityAuditFactRecorded");
            assertThat(topic.numPartitions()).isEqualTo(3);
            assertThat(topic.replicationFactor()).isEqualTo((short) 1);
            assertThat(topic.configs()).containsEntry("retention.ms", String.valueOf(Duration.ofDays(7).toMillis()));
        });
    }

    @Test
    void aTopicOnceMadeSureOfIsNotAskedForAgain() {
        topicsExist("brix.event.TenantFirstOwnerAccepted");

        broker.ensureEventTopics(Set.of("TenantFirstOwnerAccepted"));
        broker.ensureEventTopics(Set.of("TenantFirstOwnerAccepted"));

        verify(admin, times(1)).listTopics(any(ListTopicsOptions.class));
        verify(admin, never()).createTopics(anyCollection(), any(CreateTopicsOptions.class));
    }

    @Test
    void aParkingTopicIsCompactedSoAParkedEventStaysUntilReleased() {
        topicsExist();
        CreateTopicsResult created = created(Map.of(
            "brix.parked.platform-audit.identity-audit-fact-record.v1", KafkaFuture.completedFuture(null)));
        when(admin.createTopics(anyCollection(), any(CreateTopicsOptions.class))).thenReturn(created);

        broker.ensureParkingTopics(List.of(
            new ConsumedSubscription("platform-audit", "identity-audit-fact-record.v1", "IdentityAuditFactRecorded")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<NewTopic>> requested = ArgumentCaptor.forClass(Collection.class);
        verify(admin).createTopics(requested.capture(), any(CreateTopicsOptions.class));
        assertThat(requested.getValue()).singleElement().satisfies(topic -> {
            assertThat(topic.numPartitions()).isEqualTo(1);
            assertThat(topic.configs()).containsEntry("cleanup.policy", "compact");
        });
    }

    @Test
    void aTopicAnotherHostCreatedMeanwhileIsFine() {
        topicsExist();
        CreateTopicsResult created = created(Map.of("brix.event.IdentityAuditFactRecorded",
            failed(new TopicExistsException("created by another host"))));
        when(admin.createTopics(anyCollection(), any(CreateTopicsOptions.class))).thenReturn(created);

        broker.ensureEventTopics(Set.of("IdentityAuditFactRecorded"));
    }

    @Test
    void aTopicTheBrokerRefusesIsReportedAndAskedForAgainLater() {
        topicsExist();
        CreateTopicsResult created = created(Map.of("brix.event.IdentityAuditFactRecorded",
            failed(new TopicAuthorizationException("not allowed"))));
        when(admin.createTopics(anyCollection(), any(CreateTopicsOptions.class))).thenReturn(created);

        assertThatThrownBy(() -> broker.ensureEventTopics(Set.of("IdentityAuditFactRecorded")))
            .isInstanceOf(IllegalStateException.class)
            .hasCauseInstanceOf(TopicAuthorizationException.class);
        assertThatThrownBy(() -> broker.ensureEventTopics(Set.of("IdentityAuditFactRecorded")))
            .isInstanceOf(IllegalStateException.class);
        verify(admin, times(2)).createTopics(anyCollection(), any(CreateTopicsOptions.class));
    }

    @Test
    void aProductionHostRefusesAnExistingEventTopicWithTooFewPartitionsAndAsksAgainNextTime() {
        // Standard-link plan point 5: nothing goes to or comes from it until its partitions are raised.
        KafkaEventBroker production = new KafkaEventBroker(admin, new KafkaEventTopics(""), twelvePartitions(),
            KafkaClientSettings.PRODUCTION_PARTITIONS, clock);
        topicsExistWith(Map.of("brix.event.TenantFirstOwnerAccepted", 3));

        assertThatThrownBy(() -> production.ensureEventTopics(Set.of("TenantFirstOwnerAccepted")))
            .isInstanceOf(KafkaTopicPartitionsException.class)
            .hasMessageContaining("brix.event.TenantFirstOwnerAccepted (3)")
            .hasMessageContaining("at least 12");

        topicsExistWith(Map.of("brix.event.TenantFirstOwnerAccepted", 12));
        production.ensureEventTopics(Set.of("TenantFirstOwnerAccepted"));
        production.ensureEventTopics(Set.of("TenantFirstOwnerAccepted"));

        verify(admin, times(2)).describeTopics(anyCollection());
    }

    @Test
    void aProductionHostChecksCommandTopicsButNotParkingTopics() {
        KafkaEventBroker production = new KafkaEventBroker(admin, new KafkaEventTopics(""), twelvePartitions(),
            KafkaClientSettings.PRODUCTION_PARTITIONS, clock);
        topicsExistWith(Map.of("brix.command.orders.cancel-order", 3,
            "brix.parked.platform-audit.identity-audit-fact-record.v1", 1));

        assertThatThrownBy(() -> production.ensureCommandTopics(Set.of("orders.cancel-order")))
            .isInstanceOf(KafkaTopicPartitionsException.class);
        production.ensureParkingTopics(List.of(
            new ConsumedSubscription("platform-audit", "identity-audit-fact-record.v1", "IdentityAuditFactRecorded")));
    }

    @Test
    void elsewhereAnExistingTopicWithFewerPartitionsThanConfiguredIsOnlyReported() {
        KafkaEventBroker shared = new KafkaEventBroker(admin, new KafkaEventTopics(""), twelvePartitions(), clock);
        topicsExistWith(Map.of("brix.event.TenantFirstOwnerAccepted", 3));

        shared.ensureEventTopics(Set.of("TenantFirstOwnerAccepted"));
        shared.ensureEventTopics(Set.of("TenantFirstOwnerAccepted"));

        verify(admin, times(1)).describeTopics(anyCollection());
        verify(admin, never()).createTopics(anyCollection(), any(CreateTopicsOptions.class));
    }

    private static KafkaEventSettings twelvePartitions() {
        return new KafkaEventSettings(12, (short) 1, (short) 1, Duration.ofDays(7), 10, Duration.ofMillis(500),
            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(10));
    }

    private void clusterAnswers(boolean reachable) {
        DescribeClusterResult described = mock(DescribeClusterResult.class);
        KafkaFuture<Collection<Node>> nodes = reachable
            ? KafkaFuture.completedFuture(List.of(new Node(1, "kafka", 9092)))
            : failed(new org.apache.kafka.common.errors.TimeoutException("no broker"));
        when(described.nodes()).thenReturn(nodes);
        when(admin.describeCluster(any(DescribeClusterOptions.class))).thenReturn(described);
    }

    private void topicsExist(String... names) {
        Map<String, Integer> partitions = new LinkedHashMap<>();
        for (String name : names) {
            partitions.put(name, 3);
        }
        topicsExistWith(partitions);
    }

    private void topicsExistWith(Map<String, Integer> partitionsByTopic) {
        ListTopicsResult listed = mock(ListTopicsResult.class);
        when(listed.names()).thenReturn(KafkaFuture.completedFuture(Set.copyOf(partitionsByTopic.keySet())));
        when(admin.listTopics(any(ListTopicsOptions.class))).thenReturn(listed);
        Map<String, TopicDescription> described = new LinkedHashMap<>();
        Node node = new Node(1, "kafka", 9092);
        partitionsByTopic.forEach((name, count) -> described.put(name, new TopicDescription(name, false,
            java.util.stream.IntStream.range(0, count)
                .mapToObj(partition -> new TopicPartitionInfo(partition, node, List.of(node), List.of(node)))
                .toList())));
        DescribeTopicsResult result = mock(DescribeTopicsResult.class);
        when(result.allTopicNames()).thenReturn(KafkaFuture.completedFuture(described));
        when(admin.describeTopics(anyCollection())).thenReturn(result);
    }

    private static CreateTopicsResult created(Map<String, KafkaFuture<Void>> values) {
        CreateTopicsResult created = mock(CreateTopicsResult.class);
        when(created.values()).thenReturn(values);
        return created;
    }

    private static <T> KafkaFuture<T> failed(Throwable failure) {
        KafkaFutureImpl<T> future = new KafkaFutureImpl<>();
        future.completeExceptionally(failure);
        return future;
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-10-29T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
