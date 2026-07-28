/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.autoconfigure;

import java.time.Clock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.command.CommandOutboxSender;
import io.runtime.orchestrator.command.CommandPayloadCodec;
import io.runtime.orchestrator.command.CommandTransactionBoundary;
import io.runtime.orchestrator.command.JsonCommandPayloadCodec;
import io.runtime.orchestrator.command.SpringCommandTransactionBoundary;
import io.runtime.orchestrator.outbox.OutboxMessageStore;
import io.runtime.sdk.capability.CommandCapability;

/**
 * Auto-configuration for the runtime-native command capability substrate.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
@AutoConfiguration(before = CapabilityAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.transaction.support.TransactionSynchronizationManager")
@ConditionalOnBean(OutboxMessageStore.class)
public class CommandCapabilityAutoConfiguration {

    /**
     * Creates the Spring transaction callback boundary used by command receipts.
     *
     * @return transaction callback boundary
     */
    @Bean
    @ConditionalOnMissingBean
    public CommandTransactionBoundary commandTransactionBoundary() {
        return new SpringCommandTransactionBoundary();
    }

    /**
     * Creates the JSON codec used for canonical command outbox payloads.
     *
     * @param objectMapper host-managed object mapper
     * @return command payload codec
     */
    @Bean
    @ConditionalOnMissingBean
    public CommandPayloadCodec commandPayloadCodec(ObjectMapper objectMapper) {
        return new JsonCommandPayloadCodec(objectMapper);
    }

    /**
     * Creates the runtime command capability backed by the canonical outbox.
     *
     * @param outboxStore canonical outbox store
     * @param transactionBoundary transaction callback boundary
     * @param payloadCodec command payload codec
     * @return command capability implementation
     */
    @Bean
    @ConditionalOnMissingBean(CommandCapability.class)
    public CommandOutboxSender commandCapability(
            OutboxMessageStore outboxStore,
            CommandTransactionBoundary transactionBoundary,
            CommandPayloadCodec payloadCodec) {
        return new CommandOutboxSender(outboxStore, transactionBoundary, payloadCodec, Clock.systemUTC());
    }
}
