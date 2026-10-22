/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.h2.jdbcx.JdbcDataSource;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

import io.infra.adapter.persistence.jpa.fixture.ForeignNote;
import io.infra.adapter.persistence.jpa.fixture.OwnedNote;

/**
 * A shared JPA persistence unit on an in-memory database, set up the way the host sets it up.
 */
final class JpaTestEnvironment implements AutoCloseable {

    static final Duration TRANSACTION_TIMEOUT = Duration.ofSeconds(5);

    final DataSource dataSource;
    final EntityManagerFactory entityManagerFactory;
    final JpaTransactionManager transactionManager;
    final EntityManager sharedEntityManager;
    final PersistenceFailureTranslator failureTranslator;

    JpaTestEnvironment() {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:owner-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        this.dataSource = h2;
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setManagedTypes(PersistenceManagedTypes.of(
            List.of(OwnedNote.class.getName(), ForeignNote.class.getName()),
            List.of()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(AvailableSettings.HBM2DDL_AUTO, "create-drop"));
        factory.afterPropertiesSet();
        this.entityManagerFactory = factory.getObject();
        this.transactionManager = new JpaTransactionManager(entityManagerFactory);
        this.sharedEntityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        this.failureTranslator = new PersistenceFailureTranslator(new HibernateJpaDialect());
    }

    OwnerUnitOfWork unitOfWork(String ownerId) {
        return new OwnerUnitOfWork(
            ownerId,
            entityManagerFactory,
            sharedEntityManager,
            transactionManager,
            TRANSACTION_TIMEOUT,
            failureTranslator);
    }

    JpaOwnerPersistence persistence(String ownerId, Set<Class<?>> ownerTypes) {
        OwnerPersistenceValidator validator = OwnerPersistenceValidator.create(
            ownerId, ownerTypes, entityManagerFactory, dataSource, true);
        return new JpaOwnerPersistence(
            ownerId,
            type -> ownerId,
            sharedEntityManager,
            validator,
            unitOfWork(ownerId),
            failureTranslator);
    }

    long count(Class<?> entity) {
        return new TransactionTemplate(transactionManager).execute(status -> sharedEntityManager
            .createQuery("select count(e) from " + entity.getSimpleName() + " e", Long.class)
            .getSingleResult());
    }

    @Override
    public void close() {
        entityManagerFactory.close();
    }
}
