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

import io.runtime.orchestrator.event.EventDelivery;
import io.runtime.orchestrator.event.EventDeliveryProvider;
import io.runtime.orchestrator.event.EventDeliveryScopes;
import io.runtime.orchestrator.event.EventInboxStore;
import io.runtime.orchestrator.event.EventSubscriptionGroup;
import io.runtime.orchestrator.event.EventSubscriptionMetricsFactory;
import io.runtime.orchestrator.event.EventSubscriptionTransport;
import io.runtime.orchestrator.event.EventsEndpoint;
import io.runtime.orchestrator.event.BestEffortEventSender;
import io.runtime.orchestrator.event.InProcessEventTransport;
import io.runtime.orchestrator.event.TransportBestEffortSender;
import io.runtime.orchestrator.event.MicrometerEventSubscriptionMetrics;
import io.runtime.orchestrator.event.PluginEventSubscribers;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.orchestrator.tracing.MessageTracing;


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
            properties.getRetryJitterMax(),
            properties.getRetryBackoffMax());
    }

    /**
     * The delivery core the in-process transport, the subscription group and the events endpoint share
     * (Kafka design §4.1). It is built when one of them first needs it, after every bean exists.
     */
    @Bean
    @ConditionalOnMissingBean
    public EventDeliveryProvider eventDeliveryProvider(
            OutboxRelayProperties properties,
            ObjectProvider<PluginRuntimeManager> plugins,
            ObjectProvider<OwnerPersistenceProvider> owners,
            ObjectProvider<EventInboxStore> inbox,
            ObjectProvider<ObjectMapper> json,
            ObjectProvider<EventDeliveryScopes> scopes,
            ObjectProvider<MessageTracing> tracing) {
        return new EventDeliveryProvider(() -> delivery(properties, plugins, owners, inbox, json, scopes, tracing));
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
            EventDeliveryProvider deliveries,
            ObjectProvider<OutboxRelayMetricsFactory> metrics,
            ObjectProvider<MessageTracing> tracing) {
        Supplier<OutboxTransport> transport = IN_PROCESS.equals(properties.getTransport())
            ? () -> inProcess(deliveries.get())
            : brokerTransport::getIfAvailable;
        return new OutboxRelayGroup(properties, policy, stores::getIfAvailable, transport,
            outbox -> metrics.getIfAvailable(() -> OutboxRelayMetricsFactory.NOOP).forOutbox(outbox),
            tracing.getIfAvailable(() -> MessageTracing.NOOP));
    }

    /**
     * BEST_EFFORT events skip the outbox but go out through the relay's transport, so the same subscriptions
     * receive them on either transport (Kafka design §14 K21).
     */
    @Bean
    @ConditionalOnMissingBean
    public BestEffortEventSender bestEffortEventSender(
            OutboxRelayProperties properties,
            EventDeliveryProvider deliveries,
            ObjectProvider<OutboxTransport> brokerTransport) {
        return new TransportBestEffortSender(IN_PROCESS.equals(properties.getTransport())
            ? () -> inProcess(deliveries.get())
            : brokerTransport::getIfAvailable);
    }

    /**
     * Consumes every declared subscription from the broker the relay sends to (Kafka design §4.2); with the
     * {@code in-process} transport the relay delivers and the group does nothing.
     */
    @Bean
    @ConditionalOnMissingBean
    public EventSubscriptionGroup eventSubscriptionGroup(
            OutboxRelayProperties properties,
            OutboxRelayPolicy policy,
            EventDeliveryProvider deliveries,
            ObjectProvider<EventSubscriptionTransport> broker,
            ObjectProvider<EventSubscriptionMetricsFactory> metrics) {
        return new EventSubscriptionGroup(properties, policy, deliveries, broker::getIfAvailable,
            subscription -> metrics.getIfAvailable(() -> EventSubscriptionMetricsFactory.NOOP).forSubscription(subscription));
    }

    private static OutboxTransport inProcess(EventDelivery delivery) {
        return delivery == null ? null : new InProcessEventTransport(delivery);
    }

    private static EventDelivery delivery(
            OutboxRelayProperties properties,
            ObjectProvider<PluginRuntimeManager> plugins,
            ObjectProvider<OwnerPersistenceProvider> owners,
            ObjectProvider<EventInboxStore> inbox,
            ObjectProvider<ObjectMapper> json,
            ObjectProvider<EventDeliveryScopes> scopes,
            ObjectProvider<MessageTracing> tracing) {
        PluginRuntimeManager runtime = plugins.getIfAvailable();
        OwnerPersistenceProvider persistence = owners.getIfAvailable();
        EventInboxStore inboxes = inbox.getIfAvailable();
        if (runtime == null || persistence == null || inboxes == null) {
            return null;
        }
        return new EventDelivery(new PluginEventSubscribers(runtime), persistence, inboxes,
            json.getIfAvailable(ObjectMapper::new), Clock.systemUTC(), properties.getLeaseDuration().dividedBy(2),
            scopes.getIfAvailable(() -> EventDeliveryScopes.ENVELOPE), tracing.getIfAvailable(() -> MessageTracing.NOOP));
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

        @Bean
        @ConditionalOnMissingBean
        EventSubscriptionMetricsFactory eventSubscriptionMetricsFactory(ObjectProvider<MeterRegistry> registry) {
            return MicrometerEventSubscriptionMetrics.factory(registry::getIfAvailable);
        }
    }

    /** The management endpoints for parked messages and parked events, when the host has actuator. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(WebEndpoint.class)
    static class OutboxManagement {

        @Bean
        @ConditionalOnMissingBean
        OutboxEndpoint outboxEndpoint(ObjectProvider<OutboxStoreRegistry> stores) {
            return new OutboxEndpoint(stores::getIfAvailable, Clock.systemUTC());
        }

        @Bean
        @ConditionalOnMissingBean
        EventsEndpoint eventsEndpoint(ObjectProvider<EventSubscriptionTransport> broker, EventDeliveryProvider deliveries) {
            return new EventsEndpoint(broker::getIfAvailable, deliveries);
        }
    }
}
