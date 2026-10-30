/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import jakarta.persistence.EntityManagerFactory;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

import io.infra.adapter.persistence.jpa.custodial.PolicyAuditRow;
import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.orchestrator.outbox.MessageOperationAudit;
import io.runtime.orchestrator.outbox.MessageOperationAudit.MessageOperation;
import io.runtime.sdk.capability.IdGeneratorCapability;

/**
 * The runtime custodian that records operators' actions on messages in the platform audit (Kafka design D33).
 */
class JdbcMessageOperationAuditTest {

    private final AtomicLong nextId = new AtomicLong(374829105938497537L);
    private JdbcDataSource dataSource;
    private JdbcTemplate jdbc;
    private EntityManagerFactory entityManagerFactory;
    private JpaTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:message-audit-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
            CREATE TABLE test_policy_audit (
                id                   BIGINT PRIMARY KEY,
                operator_identity_id BIGINT,
                action               VARCHAR(64) NOT NULL,
                resource_type        VARCHAR(64) NOT NULL,
                resource_id          VARCHAR(128),
                affected_tenants     JSON NOT NULL,
                description          VARCHAR(1000),
                context              JSON,
                success              BOOLEAN NOT NULL,
                error_code           VARCHAR(64),
                created_at           TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                execution_identity   VARCHAR(128),
                reason_code          VARCHAR(64),
                trace_id             VARCHAR(64),
                source_ip            VARCHAR(64),
                user_agent           VARCHAR(256),
                writer               VARCHAR(64) DEFAULT 'platform-identity' NOT NULL
            )
            """);
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setManagedTypes(PersistenceManagedTypes.of(List.of(PolicyAuditRow.class.getName()), List.of()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.afterPropertiesSet();
        entityManagerFactory = factory.getObject();
        transactionManager = new JpaTransactionManager(entityManagerFactory);
    }

    @AfterEach
    void tearDown() {
        entityManagerFactory.close();
    }

    @Test
    void anOperatorsActionIsInsertedWithTheRegisteredColumnsAndTheTableDefaults() {
        audit().record(MessageOperation.of(MessageOperationAudit.REPLAYED, "event-1", "platform-audit.identity-locked",
            "ops-alice", "svc-operator", "projection fixed"));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM test_policy_audit");
        assertThat(row.get("ID")).isEqualTo(nextId.get() - 1);
        assertThat(row.get("CREATED_AT")).as("when it happened").isNotNull();
        assertThat(row.get("ACTION")).isEqualTo("MESSAGE_REPLAYED");
        assertThat(row.get("OPERATOR_IDENTITY_ID")).isNull();
        assertThat(row.get("RESOURCE_TYPE")).isEqualTo("message");
        assertThat(row.get("RESOURCE_ID")).isEqualTo("event-1");
        assertThat(jdbc.queryForObject("SELECT CAST(context AS VARCHAR) FROM test_policy_audit", String.class))
            .contains("\"place\":\"platform-audit.identity-locked\"")
            .contains("\"operator\":\"ops-alice\"")
            .contains("\"principal\":\"svc-operator\"")
            .contains("\"reason\":\"projection fixed\"");
        assertThat(row.get("SUCCESS")).isEqualTo(true);
        assertThat(row.get("ERROR_CODE")).isNull();
        assertThat(row.get("EXECUTION_IDENTITY")).isEqualTo(MessageOperationAudit.CUSTODIAN);
        assertThat(row.get("REASON_CODE")).isEqualTo("operator-request");
        assertThat(row.get("TRACE_ID")).isNotNull();
        assertThat(row.get("WRITER")).isEqualTo(MessageOperationAudit.CUSTODIAN);
    }

    @Test
    void anActionThatFailedAfterItsRecordIsRecordedAsAFailure() {
        MessageOperation discard = MessageOperation.of(MessageOperationAudit.DISCARDED, "event-2", "platform_tenant_outbox",
            "ops-alice", null, "rejected by every subscriber");

        audit().record(discard.failed("OUTBOX_MESSAGE_CHANGED"));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM test_policy_audit");
        assertThat(row.get("SUCCESS")).isEqualTo(false);
        assertThat(row.get("ERROR_CODE")).isEqualTo("OUTBOX_MESSAGE_CHANGED");
        assertThat(jdbc.queryForObject("SELECT CAST(context AS VARCHAR) FROM test_policy_audit", String.class))
            .doesNotContain("principal");
    }

    @Test
    void theRecordStaysWhenTheCallersTransactionRollsBack() {
        JdbcMessageOperationAudit audit = audit();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            audit.record(MessageOperation.of(MessageOperationAudit.REPUBLISHED, "event-3", "platform_tenant_outbox",
                "ops-alice", null, "subscription started later"));
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_policy_audit", Integer.class)).isEqualTo(1);
    }

    @Test
    void withoutARegistrationNothingIsRecorded() throws Exception {
        try (URLClassLoader empty = new URLClassLoader(new URL[0], null)) {
            JdbcMessageOperationAudit audit = new JdbcMessageOperationAudit(new OwnerDirectory(empty,
                new PluginManifestLoader()), entityManagerFactory, transactionManager, dataSource, Duration.ofSeconds(5),
                () -> numericIds());

            assertThatThrownBy(() -> audit.record(MessageOperation.of(MessageOperationAudit.REPLAYED, "event-4", "outbox",
                "ops-alice", null, "why")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(MessageOperationAudit.CUSTODIAN);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_policy_audit", Integer.class)).isZero();
    }

    @Test
    void anActionOutsideTheMessageOperationsIsRefused() {
        assertThatThrownBy(() -> audit().record(MessageOperation.of("LOGIN_TENANT_DISCOVERY", "event-5", "outbox",
            "ops-alice", null, "why")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anOperatorAndAReasonAreRequired() {
        assertThatThrownBy(() -> MessageOperation.of(MessageOperationAudit.REPLAYED, "event-6", "outbox", " ", null,
            "why")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MessageOperation.of(MessageOperationAudit.REPLAYED, "event-6", "outbox", "ops-alice",
            null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private JdbcMessageOperationAudit audit() {
        return new JdbcMessageOperationAudit(new OwnerDirectory(getClass().getClassLoader(), new PluginManifestLoader()),
            entityManagerFactory, transactionManager, dataSource, Duration.ofSeconds(5), this::numericIds);
    }

    private IdGeneratorCapability numericIds() {
        return new IdGeneratorCapability() {
            @Override
            public String generate() {
                return Long.toString(nextId.getAndIncrement());
            }

            @Override
            public String generate(String prefix) {
                return prefix + generate();
            }

            @Override
            public List<String> generateBatch(int count) {
                return IntStream.range(0, count).mapToObj(i -> generate()).toList();
            }

            @Override
            public List<String> generateBatch(String prefix, int count) {
                return IntStream.range(0, count).mapToObj(i -> generate(prefix)).toList();
            }
        };
    }
}
