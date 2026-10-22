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

import io.infra.adapter.persistence.jpa.fixture.OwnedNote;
import io.infra.adapter.persistence.jpa.fixture.OwnedNoteRepository;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.sdk.capability.PersistenceCapability;

class JpaPersistenceAutoConfigurationTest {

    private final ApplicationContextRunner jpaRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            DataSourceAutoConfiguration.class,
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
    void ownerRepositoriesWorkThroughTheInjectedCapability() {
        contextRunner.run(context -> {
            PersistenceCapability persistence = context.getBean(OwnerModuleConfiguration.InjectedPersistence.class)
                .persistence();
            OwnedNoteRepository notes = persistence.repository(OwnedNoteRepository.class);

            persistence.unitOfWork().required(() -> notes.save(new OwnedNote(1L, "n-1", "stored")));

            assertEquals("stored", persistence.unitOfWork()
                .readOnly(() -> notes.findByCode("n-1").orElseThrow().getText()));
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
    @EntityScan(basePackageClasses = OwnedNote.class)
    static class OwnerModuleConfiguration {

        @Bean
        InjectedPersistence injectedPersistence(PersistenceCapability persistence) {
            return new InjectedPersistence(persistence);
        }

        record InjectedPersistence(PersistenceCapability persistence) {
        }
    }
}
