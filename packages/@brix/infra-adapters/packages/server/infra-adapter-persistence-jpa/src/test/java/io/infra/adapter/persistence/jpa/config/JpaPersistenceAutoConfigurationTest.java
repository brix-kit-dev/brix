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
import org.springframework.boot.autoconfigure.AutoConfigurations;
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
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.PersistenceCapabilityException;

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
