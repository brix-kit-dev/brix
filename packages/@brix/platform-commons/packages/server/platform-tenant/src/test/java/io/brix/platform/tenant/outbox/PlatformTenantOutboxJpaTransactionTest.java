/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.brix.platform.tenant.entity.PlatformTenantOutbox;
import io.brix.platform.tenant.event.TenantFirstOwnerAcceptedEvent;
import io.brix.platform.tenant.repository.PlatformTenantOutboxRepository;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:platform_tenant_outbox;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PlatformTenantOutboxJpaTransactionTest.JpaConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlatformTenantOutboxJpaTransactionTest {

    @Autowired
    private PlatformTenantOutboxRepository outboxRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void producerOutboxAppendCommitsWithOwnerTransaction() {
        PlatformTenantReliableEventBusCapability capability =
            new PlatformTenantReliableEventBusCapability(outboxRepository, objectMapper);
        TenantFirstOwnerAcceptedEvent event = new TenantFirstOwnerAcceptedEvent(100L, 200L, 300L, 400L);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> capability.publishIntegration(event));

        PlatformTenantOutbox record = outboxRepository.findById(event.getEventId()).orElseThrow();
        assertEquals(event.getEventId(), record.getMessageId());
        assertEquals(TenantFirstOwnerAcceptedEvent.EVENT_TYPE, record.getMessageType());
        assertEquals("platform-tenant", record.getProducerPluginId());
        assertEquals("TENANT", record.getScope());
        assertEquals(100L, record.getTenantId());
        assertEquals("PENDING", record.getStatus());
    }

    @Test
    void producerOutboxAppendRollsBackWithOwnerTransaction() {
        PlatformTenantReliableEventBusCapability capability =
            new PlatformTenantReliableEventBusCapability(outboxRepository, objectMapper);
        TenantFirstOwnerAcceptedEvent event = new TenantFirstOwnerAcceptedEvent(101L, 201L, 301L, 401L);

        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager)
            .executeWithoutResult(status -> {
                capability.publishIntegration(event);
                throw new IllegalStateException("rollback-owner-transaction");
            }));

        assertFalse(outboxRepository.findById(event.getEventId()).isPresent());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackageClasses = PlatformTenantOutboxRepository.class)
    @EntityScan(basePackageClasses = PlatformTenantOutbox.class)
    static class JpaConfig {
    }
}
