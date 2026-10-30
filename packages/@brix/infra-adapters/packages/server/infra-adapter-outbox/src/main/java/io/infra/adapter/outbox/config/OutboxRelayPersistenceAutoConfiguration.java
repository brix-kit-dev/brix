/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.outbox.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ClassUtils;

import io.infra.adapter.outbox.ManifestOutboxStoreRegistry;
import io.runtime.orchestrator.outbox.OutboxStoreRegistry;

/**
 * Auto-configuration for broker-neutral Outbox relay persistence: the store of each owner outbox the plugin manifests
 * declare. No outbox is named in configuration; each owner's comes from its manifest's {@code data.outbox}.
 *
 * @author Brix Platform Authors
 * @since 3.0.10
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "brix.outbox.relay.persistence", name = "enabled", havingValue = "true")
public class OutboxRelayPersistenceAutoConfiguration {

    /**
     * One store for each owner outbox the plugin manifests declare (P4 design D12). Its dependencies are
     * plain bean references, so it does not depend on the order in which auto-configurations run.
     */
    @Bean
    @ConditionalOnMissingBean(OutboxStoreRegistry.class)
    public OutboxStoreRegistry outboxStoreRegistry(DataSource dataSource, PlatformTransactionManager transactionManager) {
        return new ManifestOutboxStoreRegistry(ClassUtils.getDefaultClassLoader(), new JdbcTemplate(dataSource),
            new TransactionTemplate(transactionManager));
    }
}
