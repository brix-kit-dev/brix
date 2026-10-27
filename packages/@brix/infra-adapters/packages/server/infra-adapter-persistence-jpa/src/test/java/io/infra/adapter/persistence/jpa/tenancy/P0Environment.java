/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.tenancy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.h2.jdbcx.JdbcDataSource;
import org.hibernate.Session;
import org.hibernate.cfg.AvailableSettings;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shared persistence unit for the P0 suite: H2 by default, the reference DCP (PostgreSQL) when
 * {@code BRIX_P0_POSTGRES_URL}, {@code BRIX_P0_POSTGRES_USER} and {@code BRIX_P0_POSTGRES_PASSWORD}
 * point at a throwaway database.
 */
final class P0Environment implements AutoCloseable {

    final String database;
    final EntityManagerFactory entityManagerFactory;
    final EntityManager sharedEntityManager;
    final TransactionTemplate transactions;
    final JpaRepositoryFactory repositories;

    P0Environment() {
        String postgresUrl = System.getenv("BRIX_P0_POSTGRES_URL");
        DataSource dataSource;
        if (postgresUrl != null && !postgresUrl.isBlank()) {
            PGSimpleDataSource postgres = new PGSimpleDataSource();
            postgres.setUrl(postgresUrl);
            postgres.setUser(System.getenv("BRIX_P0_POSTGRES_USER"));
            postgres.setPassword(System.getenv("BRIX_P0_POSTGRES_PASSWORD"));
            dataSource = postgres;
            database = "postgresql";
        } else {
            JdbcDataSource h2 = new JdbcDataSource();
            h2.setURL("jdbc:h2:mem:p0-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
            dataSource = h2;
            database = "h2";
        }
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setManagedTypes(PersistenceManagedTypes.of(
            List.of(P0Folder.class.getName(), P0Doc.class.getName(), P0Note.class.getName(),
                P0Invite.class.getName(), P0Global.class.getName()),
            List.of()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
            AvailableSettings.HBM2DDL_AUTO, "create-drop",
            AvailableSettings.STATEMENT_INSPECTOR, P0SqlCapture.INSTANCE,
            P0TenantFilterContributor.ENABLED, "true"));
        factory.afterPropertiesSet();
        entityManagerFactory = factory.getObject();
        sharedEntityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        transactions = new TransactionTemplate(new JpaTransactionManager(entityManagerFactory));
        repositories = new JpaRepositoryFactory(sharedEntityManager);
    }

    <R> R repository(Class<R> type) {
        return repositories.getRepository(type);
    }

    /** Runs without any filter, the way seeding and after-the-fact checks see the table. */
    <T> T unscoped(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    /** Runs with the tenant filter and the write guard bound to the given tenants. */
    <T> T inTenants(Set<Long> tenants, Supplier<T> work) {
        return transactions.execute(status -> {
            session().enableFilter(P0TenantFilterContributor.TENANT_FILTER)
                .setParameterList(P0TenantFilterContributor.TENANT_PARAMETER, tenants);
            P0Scope.enter(tenants);
            try {
                return work.get();
            } finally {
                P0Scope.exit();
            }
        });
    }

    /** Runs with one key filter and no tenant filter, the shape of a P-1 or P-3 policy. */
    <T> T keyed(String filter, String parameter, Object value, Supplier<T> work) {
        return transactions.execute(status -> {
            session().enableFilter(filter).setParameter(parameter, value);
            return work.get();
        });
    }

    Session session() {
        return sharedEntityManager.unwrap(Session.class);
    }

    @Override
    public void close() {
        entityManagerFactory.close();
    }
}
