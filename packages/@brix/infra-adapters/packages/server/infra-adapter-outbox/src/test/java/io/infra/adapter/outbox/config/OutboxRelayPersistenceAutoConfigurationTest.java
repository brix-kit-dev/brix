/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.outbox.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;

import io.runtime.orchestrator.outbox.OutboxMessageStore;
import io.runtime.orchestrator.outbox.OutboxStoreRegistry;

/**
 * Host assembly of the relay's outbox stores: each owner's outbox comes from its manifest, through the registry
 * (P4 design D12; Kafka design §14 K9). No outbox table is named in configuration.
 *
 * @author Brix Platform Authors
 * @since 3.0.10
 */
class OutboxRelayPersistenceAutoConfigurationTest {

    @Test
    void enabledPersistenceReachesEachOwnersOutboxThroughTheRegistryAlone() {
        try (AnnotationConfigApplicationContext context = context(true)) {
            assertEquals(1, context.getBeansOfType(OutboxStoreRegistry.class).size());
            assertTrue(context.getBeansOfType(OutboxMessageStore.class).isEmpty(),
                "no store of a table named in configuration, which the relay would never read");
        }
    }

    @Test
    void persistenceIsOffUnlessTheHostTurnsItOn() {
        try (AnnotationConfigApplicationContext context = context(false)) {
            assertTrue(context.getBeansOfType(OutboxStoreRegistry.class).isEmpty());
        }
    }

    private static AnnotationConfigApplicationContext context(boolean enabled) {
        MockEnvironment environment = new MockEnvironment();
        if (enabled) {
            environment.setProperty("brix.outbox.relay.persistence.enabled", "true");
        }
        DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:outbox_autoconfig_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", "");
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setEnvironment(environment);
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(dataSource));
        context.register(OutboxRelayPersistenceAutoConfiguration.class);
        context.refresh();
        return context;
    }
}
