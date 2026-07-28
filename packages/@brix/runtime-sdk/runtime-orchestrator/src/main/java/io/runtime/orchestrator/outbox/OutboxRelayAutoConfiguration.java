/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.time.Clock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
@ConditionalOnBean({OutboxMessageStore.class, OutboxTransport.class})
@EnableConfigurationProperties(OutboxRelayProperties.class)
public class OutboxRelayAutoConfiguration {

    /**
     * Creates the relay policy.
     *
     * @param properties relay properties
     * @return relay policy
     */
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
     * Creates the relay instance.
     *
     * @param store outbox message store
     * @param transport broker transport
     * @param policy relay policy
     * @param properties relay properties
     * @return relay
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboxRelay outboxRelay(
            OutboxMessageStore store,
            OutboxTransport transport,
            OutboxRelayPolicy policy,
            OutboxRelayProperties properties) {
        String owner = properties.getOwner();
        if (owner == null || owner.isBlank()) {
            throw new IllegalStateException("brix.outbox.relay.owner must be configured when relay is enabled");
        }
        return new OutboxRelay(owner, store, transport, policy, Clock.systemUTC());
    }

    /**
     * Creates the managed relay resource.
     *
     * @param relay relay
     * @param properties relay properties
     * @return managed resource
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboxRelayManagedResource outboxRelayManagedResource(
            OutboxRelay relay,
            OutboxRelayProperties properties) {
        return new OutboxRelayManagedResource(relay, properties.getPollDelay());
    }
}
