/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.outbox.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.infra.adapter.outbox.JdbcOutboxMessageStore;
import io.runtime.orchestrator.outbox.OutboxMessageStore;

/**
 * Auto-configuration for broker-neutral Outbox relay persistence.
 *
 * @author Brix Platform Authors
 * @since 3.0.10
 */
@AutoConfiguration
@ConditionalOnBean({DataSource.class, PlatformTransactionManager.class})
@ConditionalOnProperty(prefix = "brix.outbox.relay.persistence", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OutboxRelayPersistenceProperties.class)
public class OutboxRelayPersistenceAutoConfiguration {

    /**
     * Creates the canonical outbox message store.
     *
     * @param dataSource owner datasource selected by Host composition
     * @param transactionManager transaction manager
     * @param properties persistence properties
     * @return outbox message store
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboxMessageStore outboxMessageStore(
            DataSource dataSource,
            PlatformTransactionManager transactionManager,
            OutboxRelayPersistenceProperties properties) {
        return new JdbcOutboxMessageStore(
            new JdbcTemplate(dataSource),
            new TransactionTemplate(transactionManager),
            properties.getTableName());
    }
}
