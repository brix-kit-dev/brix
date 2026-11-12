/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.h2.jdbcx.JdbcDataSource;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

import io.infra.adapter.persistence.jpa.tenancy.P0Doc;
import io.infra.adapter.persistence.jpa.tenancy.P0Folder;
import io.infra.adapter.persistence.jpa.tenancy.P0Global;
import io.infra.adapter.persistence.jpa.tenancy.P0Invite;
import io.infra.adapter.persistence.jpa.tenancy.P0Member;
import io.infra.adapter.persistence.jpa.tenancy.P0Note;
import io.infra.adapter.persistence.jpa.tenancy.P0Projection;
import io.infra.adapter.persistence.jpa.tenancy.P0Tenant;
import io.runtime.orchestrator.persistence.TenantAccessPolicy;
import io.runtime.orchestrator.persistence.TenantIsolationRegister;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.orchestrator.persistence.TenantScopes;

/**
 * Persistence unit for the tenant isolation conformance suite, wired like the host: the
 * adapter's filters and write guard come from the module's Hibernate services, and the
 * statement verifier inspects every statement. H2 by default; the reference DCP (PostgreSQL)
 * when {@code BRIX_P0_POSTGRES_URL}, {@code BRIX_P0_POSTGRES_USER} and
 * {@code BRIX_P0_POSTGRES_PASSWORD} point at a throwaway database.
 */
final class TenantTestEnvironment implements AutoCloseable {

    static final String OWNER = TenantAccessPolicy.OWNER;

    final String database;
    /** The database the owner's unit of work runs on, for the adapter's own JDBC access such as the outbox's. */
    final DataSource dataSource;
    final EntityManagerFactory entityManagerFactory;
    final EntityManager sharedEntityManager;
    final TransactionTemplate transactions;
    final JpaOwnerPersistence persistence;
    final CapturingVerifier statements = new CapturingVerifier();
    private final OwnerPersistenceValidator validator;

    TenantTestEnvironment() {
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
            h2.setURL("jdbc:h2:mem:tenancy-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
            dataSource = h2;
            database = "h2";
        }
        this.dataSource = dataSource;
        List<Class<?>> types = List.of(P0Folder.class, P0Doc.class, P0Note.class, P0Invite.class, P0Member.class,
            P0Tenant.class, P0Global.class, P0Projection.class);
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setManagedTypes(PersistenceManagedTypes.of(types.stream().map(Class::getName).toList(), List.of()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
            AvailableSettings.HBM2DDL_AUTO, "create-drop",
            AvailableSettings.STATEMENT_INSPECTOR, statements));
        factory.afterPropertiesSet();
        entityManagerFactory = factory.getObject();
        sharedEntityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        JpaTransactionManager transactionManager = new JpaTransactionManager(entityManagerFactory);
        transactions = new TransactionTemplate(transactionManager);
        PersistenceFailureTranslator failureTranslator = new PersistenceFailureTranslator(new HibernateJpaDialect());
        Set<String> globalTables = Stream.concat(TenantIsolationRegister.GLOBAL_TABLES.stream(), Stream.of("p0_global"))
            .collect(Collectors.toUnmodifiableSet());
        validator = OwnerPersistenceValidator.create(
            OWNER, Set.copyOf(types), entityManagerFactory, dataSource, true, globalTables, Set.of());
        OwnerUnitOfWork unitOfWork = new OwnerUnitOfWork(OWNER, entityManagerFactory, sharedEntityManager,
            transactionManager, JpaTestEnvironment.TRANSACTION_TIMEOUT, failureTranslator, validator.tenantTables());
        persistence = new JpaOwnerPersistence(
            OWNER, type -> OWNER, sharedEntityManager, validator, unitOfWork, failureTranslator);
    }

    <R> R repository(Class<R> type) {
        return persistence.repository(type);
    }

    /** Reads in the given tenants, the way an endpoint with a verified {@code tid} reads. */
    <T> T read(TenantScope scope, Supplier<T> work) {
        return TenantScopes.within(scope, () -> persistence.unitOfWork().readOnly(work));
    }

    /** Writes in the given scope, the way a write entry does. */
    <T> T write(TenantScope scope, Supplier<T> work) {
        return TenantScopes.within(scope, () -> persistence.unitOfWork().required(work));
    }

    static TenantScope tenants(Long... tenantIds) {
        return new TenantScope.Tenants(Set.of(tenantIds));
    }

    @Override
    public void close() {
        validator.close();
        entityManagerFactory.close();
    }

    /** The production verifier, recording what it let through. */
    static final class CapturingVerifier implements StatementInspector {

        @Serial
        private static final long serialVersionUID = 1L;

        private final TenantStatementVerifier verifier = new TenantStatementVerifier();
        private final List<String> passed = new ArrayList<>();

        @Override
        public String inspect(String sql) {
            String inspected = verifier.inspect(sql);
            synchronized (passed) {
                passed.add(sql);
            }
            return inspected;
        }

        void clear() {
            synchronized (passed) {
                passed.clear();
            }
        }

        List<String> passed() {
            synchronized (passed) {
                return List.copyOf(passed);
            }
        }
    }
}
