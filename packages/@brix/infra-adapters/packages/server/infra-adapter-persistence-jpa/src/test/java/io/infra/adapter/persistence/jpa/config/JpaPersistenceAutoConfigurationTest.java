/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.infra.adapter.persistence.jpa.config.tenant.TenantRecord;
import io.infra.adapter.persistence.jpa.config.tenant.TenantRecordRepository;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.orchestrator.persistence.TenantScopes;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.PersistenceCapabilityException;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;

class JpaPersistenceAutoConfigurationTest {

    private final ApplicationContextRunner jpaRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            DataSourceAutoConfiguration.class,
            TenantStatementVerifierAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaPersistenceAutoConfiguration.class))
        .withPropertyValues(
            "spring.datasource.url=jdbc:h2:mem:auto-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop");

    private final ApplicationContextRunner contextRunner =
        jpaRunner.withUserConfiguration(OwnerModuleConfiguration.class);

    @Test
    void injectedCapabilityBelongsToTheInjectingModulesOwner() {
        contextRunner.run(context -> {
            assertTrue(context.getStartupFailure() == null, () -> String.valueOf(context.getStartupFailure()));
            OwnerPersistenceProvider provider = context.getBean(OwnerPersistenceProvider.class);
            PersistenceCapability injected = context.getBean(OwnerModuleConfiguration.InjectedPersistence.class)
                .persistence();

            assertSame(provider.forOwner("test-owner").orElseThrow(), injected);
            assertTrue(injected.toString().contains("test-owner"));
        });
    }

    @Test
    void ownerRepositoriesWorkInsideTheirTenant() {
        contextRunner.run(context -> {
            PersistenceCapability persistence = context.getBean(OwnerModuleConfiguration.InjectedPersistence.class)
                .persistence();
            TenantRecordRepository records = persistence.repository(TenantRecordRepository.class);

            TenantScopes.within(TenantScope.tenant(7L), () -> persistence.unitOfWork()
                .required(() -> records.save(new TenantRecord(1L, 7L, "stored"))));

            assertEquals("stored", TenantScopes.within(TenantScope.tenant(7L), () -> persistence.unitOfWork()
                .readOnly(() -> records.findByLabel("stored").orElseThrow().getLabel())));
            assertFalse(TenantScopes.within(TenantScope.tenant(8L), () -> persistence.unitOfWork()
                .readOnly(() -> records.findByLabel("stored").isPresent())));
        });
    }

    @Test
    void tenantRowsNeedATenantScope() {
        contextRunner.run(context -> {
            PersistenceCapability persistence = context.getBean(OwnerModuleConfiguration.InjectedPersistence.class)
                .persistence();
            TenantRecordRepository records = persistence.repository(TenantRecordRepository.class);

            PersistenceCapabilityException failure = assertThrows(PersistenceCapabilityException.class,
                () -> persistence.unitOfWork().readOnly(() -> records.findByLabel("stored")));
            assertEquals(PersistenceCapabilityException.Code.TENANT_SCOPE_REQUIRED, failure.code());
        });
    }

    @Test
    void theOwnersOutboxBusIsInjectedByQualifierAndHidesNoHostBus() {
        jpaRunner
            .withConfiguration(AutoConfigurations.of(HostEventBusAutoConfiguration.class))
            .withUserConfiguration(OwnerModuleConfiguration.class, OwnerEventsConfiguration.class)
            .run(context -> {
                assertTrue(context.getStartupFailure() == null, () -> String.valueOf(context.getStartupFailure()));
                OwnerEventsConfiguration.InjectedEvents injected = context.getBean(OwnerEventsConfiguration.InjectedEvents.class);

                // The Host's bus is created after the adapter's beans exist, as the broker adapters create theirs.
                assertSame(context.getBean("hostEventBus"), injected.plain());
                assertSame(context.getBean(OwnerPersistenceProvider.class).eventBusFor("test-owner").orElseThrow(),
                    injected.owner());
            });
    }

    @Test
    void unknownOwnersHaveNoPersistence() {
        contextRunner.run(context ->
            assertFalse(context.getBean(OwnerPersistenceProvider.class).forOwner("not-a-plugin").isPresent()));
    }

    @Test
    void adapterCanBeDisabled() {
        jpaRunner
            .withPropertyValues("brix.persistence.enabled=false")
            .run(context -> {
                assertTrue(context.getStartupFailure() == null, () -> String.valueOf(context.getStartupFailure()));
                assertFalse(context.containsBean("ownerPersistenceUnits"));
                assertTrue(context.getBeansOfType(OwnerPersistenceProvider.class).isEmpty());
            });
    }

    /** A module of the test owner that publishes through its outbox and also sees the Host's bus. */
    @Configuration(proxyBeanMethods = false)
    static class OwnerEventsConfiguration {

        @Bean
        InjectedEvents injectedEvents(
                @Qualifier("ownerOutboxEventBus") EventBusCapability owner, EventBusCapability plain) {
            return new InjectedEvents(owner, plain);
        }

        record InjectedEvents(EventBusCapability owner, EventBusCapability plain) {
        }
    }

    /** Creates its bus only while there is none, as the Kafka, in-memory and webhook adapters do. */
    @AutoConfiguration(after = JpaPersistenceAutoConfiguration.class)
    static class HostEventBusAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean(EventBusCapability.class)
        EventBusCapability hostEventBus() {
            return new EventBusCapability() {
                @Override
                public void publish(DomainEvent event) {
                }

                @Override
                public void publishIntegration(IntegrationEvent event) {
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = TenantRecord.class)
    static class OwnerModuleConfiguration {

        @Bean
        InjectedPersistence injectedPersistence(PersistenceCapability persistence) {
            return new InjectedPersistence(persistence);
        }

        record InjectedPersistence(PersistenceCapability persistence) {
        }
    }
}
