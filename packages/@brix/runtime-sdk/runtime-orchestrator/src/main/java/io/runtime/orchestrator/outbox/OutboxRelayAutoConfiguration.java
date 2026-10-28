/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.time.Clock;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.event.EventInboxStore;
import io.runtime.orchestrator.event.InProcessEventTransport;
import io.runtime.orchestrator.event.PluginEventSubscribers;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;


import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the L2B outbox relay managed resource.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "brix.outbox.relay", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OutboxRelayProperties.class)
public class OutboxRelayAutoConfiguration {

    /** Transport that hands events to the subscribers' persistent inboxes in this process (P4 design D11). */
    public static final String IN_PROCESS = "in-process";

    @Bean
    @ConditionalOnMissingBean
    public OutboxRelayPolicy outboxRelayPolicy(OutboxRelayProperties properties) {
        return new OutboxRelayPolicy(
            properties.getBatchSize(),
            properties.getMaxAttempts(),
            properties.getLeaseDuration(),
            properties.getRetryBackoffBase(),
            properties.getRetryJitterMax());
    }

    /**
     * One relay per owner outbox. With the {@code in-process} transport the group delivers events to the
     * subscribers' inboxes; otherwise it uses the transport bean the host provides, such as Kafka.
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboxRelayGroup outboxRelayGroup(
            OutboxRelayProperties properties,
            OutboxRelayPolicy policy,
            ObjectProvider<OutboxStoreRegistry> stores,
            ObjectProvider<OutboxTransport> brokerTransport,
            ObjectProvider<PluginRuntimeManager> plugins,
            ObjectProvider<OwnerPersistenceProvider> owners,
            ObjectProvider<EventInboxStore> inbox,
            ObjectProvider<ObjectMapper> json,
            ObjectProvider<OutboxRelayMetricsFactory> metrics) {
        Supplier<OutboxTransport> transport = IN_PROCESS.equals(properties.getTransport())
            ? () -> inProcess(properties, plugins, owners, inbox, json)
            : brokerTransport::getIfAvailable;
        return new OutboxRelayGroup(properties, policy, stores::getIfAvailable, transport,
            outbox -> metrics.getIfAvailable(() -> OutboxRelayMetricsFactory.NOOP).forOutbox(outbox));
    }

    private static OutboxTransport inProcess(
            OutboxRelayProperties properties,
            ObjectProvider<PluginRuntimeManager> plugins,
            ObjectProvider<OwnerPersistenceProvider> owners,
            ObjectProvider<EventInboxStore> inbox,
            ObjectProvider<ObjectMapper> json) {
        PluginRuntimeManager runtime = plugins.getIfAvailable();
        OwnerPersistenceProvider persistence = owners.getIfAvailable();
        EventInboxStore inboxes = inbox.getIfAvailable();
        if (runtime == null || persistence == null || inboxes == null) {
            return null;
        }
        return new InProcessEventTransport(new PluginEventSubscribers(runtime), persistence, inboxes,
            json.getIfAvailable(ObjectMapper::new), Clock.systemUTC(), properties.getLeaseDuration().dividedBy(2));
    }

    /** Relay meters per owner outbox, when the host has Micrometer. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class OutboxRelayMeters {

        @Bean
        @ConditionalOnMissingBean
        OutboxRelayMetricsFactory outboxRelayMetricsFactory(ObjectProvider<MeterRegistry> registry) {
            return MicrometerOutboxRelayMetrics.factory(registry::getIfAvailable);
        }
    }

    /** The management endpoint for parked messages, when the host has actuator. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(WebEndpoint.class)
    static class OutboxManagement {

        @Bean
        @ConditionalOnMissingBean
        OutboxEndpoint outboxEndpoint(ObjectProvider<OutboxStoreRegistry> stores) {
            return new OutboxEndpoint(stores::getIfAvailable, Clock.systemUTC());
        }
    }
}
