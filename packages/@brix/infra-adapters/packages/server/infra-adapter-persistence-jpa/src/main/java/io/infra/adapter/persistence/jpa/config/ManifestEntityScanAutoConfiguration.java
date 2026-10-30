/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ClassUtils;

import jakarta.persistence.EntityManagerFactory;

/**
 * Registers the plugin Owners' entities before the shared persistence unit is built (Kafka design §14 K18).
 *
 * @author Brix Platform Authors
 * @since 3.3.0
 */
@AutoConfiguration(before = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(EntityManagerFactory.class)
@ConditionalOnProperty(prefix = "brix.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ManifestEntityScanAutoConfiguration {

    /**
     * Adds the entity packages of every Owner whose manifest declares PersistenceCapability.
     *
     * @return the registrar
     */
    @Bean
    public static ManifestEntityPackages manifestEntityPackages() {
        return new ManifestEntityPackages(ClassUtils.getDefaultClassLoader());
    }
}
