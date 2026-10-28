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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

import io.infra.adapter.persistence.jpa.config.TenantIsolationMeterBinder;
import io.infra.adapter.persistence.jpa.custodial.PolicyAuditRow;
import io.infra.adapter.persistence.jpa.tenancy.P0Global;
import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.orchestrator.persistence.PolicyUseRecord;
import io.runtime.orchestrator.persistence.TenantAccessPolicy;
import io.runtime.sdk.error.PolicyAuditException;

/**
 * The runtime policy audit writer inserts only what the owner registers, in a transaction of its
 * own, and fails a use it cannot record (root BRX-RS-DATA-045, DATA-047).
 */
class JdbcPolicyAuditWriterTest {

    private static final String TABLE = """
        CREATE SEQUENCE test_policy_audit_id_seq;
        CREATE TABLE test_policy_audit (
            id                   BIGINT DEFAULT NEXT VALUE FOR test_policy_audit_id_seq PRIMARY KEY,
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
            writer               VARCHAR(64) DEFAULT 'platform-identity' NOT NULL,
            CONSTRAINT ck_test_policy_writer
                CHECK ((action IN ('LOGIN_TENANT_DISCOVERY', 'FIRST_OWNER_INVITEE_SETUP_FANOUT'))
                       = (writer = 'runtime.tenant-policy-audit')),
            CONSTRAINT ck_test_trace CHECK (trace_id <> 'refused-trace-0001')
        )
        """;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final TenantIsolationMeterBinder binder = new TenantIsolationMeterBinder();
    private JdbcDataSource dataSource;
    private Connection schema;
    private JdbcTemplate jdbc;
    private EntityManagerFactory entityManagerFactory;
    private JpaTransactionManager transactionManager;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:custodial-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        // H2 2.4 evaluates a check with a condition list in the session that created it, so that session stays open.
        schema = dataSource.getConnection();
        try (Statement statement = schema.createStatement()) {
            for (String sql : TABLE.split(";")) {
                statement.execute(sql);
            }
        }
        entityManagerFactory = entityManagerFactory(PolicyAuditRow.class);
        transactionManager = new JpaTransactionManager(entityManagerFactory);
        binder.bindTo(meters);
    }

    @AfterEach
    void tearDown() throws SQLException {
        binder.destroy();
        entityManagerFactory.close();
        schema.close();
    }

    @Test
    void aPolicyUseIsInsertedWithTheRegisteredColumnsAndTheTableDefaults() {
        writer().record(discovery("0af7651916cd43dd8448eb211c80319c"));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM test_policy_audit");
        assertThat(row.get("ID")).isNotNull();
        assertThat(row.get("CREATED_AT")).isNotNull();
        assertThat(row.get("ACTION")).isEqualTo("LOGIN_TENANT_DISCOVERY");
        assertThat(row.get("OPERATOR_IDENTITY_ID")).isEqualTo(5L);
        assertThat(row.get("RESOURCE_TYPE")).isEqualTo("SELF");
        assertThat(jdbc.queryForObject("SELECT CAST(affected_tenants AS VARCHAR) FROM test_policy_audit", String.class))
            .isEqualTo("[910001,910002]");
        assertThat(jdbc.queryForObject("SELECT CAST(context AS VARCHAR) FROM test_policy_audit", String.class))
            .contains("\"entry\":\"login\"").contains("\"matched\":\"2\"");
        assertThat(row.get("EXECUTION_IDENTITY")).isEqualTo("runtime:policy:login-tenant-discovery");
        assertThat(row.get("REASON_CODE")).isEqualTo("login-tenant-discovery");
        assertThat(row.get("TRACE_ID")).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(row.get("SOURCE_IP")).isEqualTo("203.0.113.0/24");
        assertThat(row.get("WRITER")).isEqualTo("runtime.tenant-policy-audit");
    }

    @Test
    void theRecordStaysWhenTheCallersTransactionRollsBack() {
        JdbcPolicyAuditWriter writer = writer();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            writer.record(discovery("0af7651916cd43dd8448eb211c80319c"));
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_policy_audit", Integer.class)).isEqualTo(1);
    }

    @Test
    void aRefusedRecordFailsTheUseWithAStableCodeAndIsCounted() {
        assertThatThrownBy(() -> writer().record(discovery("refused-trace-0001")))
            .isInstanceOfSatisfying(PolicyAuditException.class,
                failure -> assertThat(failure.code().wireCode()).isEqualTo("audit.policy_use_write_failed"));

        assertThat(meters.get(TenantIsolationMetrics.POLICY_AUDIT_FAILURES)
            .tags("policy", "login-tenant-discovery", "reason", "integrity").counter().count()).isEqualTo(1.0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_policy_audit", Integer.class)).isZero();
    }

    @Test
    void anActionTheRegistrationDoesNotCoverIsRefused() {
        JdbcPolicyAuditWriter writer = writer();
        PolicyUseRecord resend = new PolicyUseRecord(TenantAccessPolicy.FIRST_OWNER_INVITEE_SETUP_FANOUT, 5L, "INVITATION",
            List.of(910001L), Map.of("subscription", "first-owner-invitee-setup"), true, null, "trace-0002-abcdef", null, null);

        assertThat(writer.covers("LOGIN_TENANT_DISCOVERY")).isTrue();
        assertThat(writer.covers("FIRST_OWNER_INVITEE_SETUP_FANOUT")).isFalse();
        assertThatThrownBy(() -> writer.record(resend)).isInstanceOf(PolicyAuditException.class);
        assertThat(meters.get(TenantIsolationMetrics.POLICY_AUDIT_FAILURES)
            .tags("policy", "first-owner-invitee-setup-fanout", "reason", "not_registered").counter().count()).isEqualTo(1.0);
    }

    @Test
    void withoutARegistrationTheWriterCoversNothing() throws Exception {
        try (URLClassLoader empty = new URLClassLoader(new URL[0], null)) {
            JdbcPolicyAuditWriter writer = new JdbcPolicyAuditWriter(new OwnerDirectory(empty, new PluginManifestLoader()),
                entityManagerFactory, transactionManager, dataSource, Duration.ofSeconds(5));

            assertThat(writer.covers("LOGIN_TENANT_DISCOVERY")).isFalse();
            assertThatThrownBy(() -> writer.record(discovery("0af7651916cd43dd8448eb211c80319c")))
                .isInstanceOf(PolicyAuditException.class);
        }
    }

    @Test
    void aRegisteredTableNoEntityOfTheOwnerMapsStopsTheWriter() {
        EntityManagerFactory unrelated = entityManagerFactory(P0Global.class);
        try {
            JdbcPolicyAuditWriter writer = new JdbcPolicyAuditWriter(owners(), unrelated,
                new JpaTransactionManager(unrelated), dataSource, Duration.ofSeconds(5));

            assertThatThrownBy(() -> writer.covers("LOGIN_TENANT_DISCOVERY"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("none of its entities maps");
        } finally {
            unrelated.close();
        }
    }

    @Test
    void aRecordSchemaReferenceNamesAResource() {
        assertThat(JdbcPolicyAuditWriter.schemaResource("brix://schemas/platform-audit/policy-use-record/1.0.0"))
            .isEqualTo("META-INF/brix/schemas/platform-audit/policy-use-record/1.0.0.json");
        assertThat(JdbcPolicyAuditWriter.schemaResource("policy-use-record")).isEmpty();
    }

    private JdbcPolicyAuditWriter writer() {
        return new JdbcPolicyAuditWriter(owners(), entityManagerFactory, transactionManager, dataSource, Duration.ofSeconds(5));
    }

    private OwnerDirectory owners() {
        return new OwnerDirectory(getClass().getClassLoader(), new PluginManifestLoader());
    }

    private static PolicyUseRecord discovery(String traceId) {
        return new PolicyUseRecord(TenantAccessPolicy.LOGIN_TENANT_DISCOVERY, 5L, "SELF", List.of(910001L, 910002L),
            Map.of("entry", "login", "matched", "2"), true, null, traceId, "203.0.113.0/24", null);
    }

    private EntityManagerFactory entityManagerFactory(Class<?> entity) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setManagedTypes(PersistenceManagedTypes.of(List.of(entity.getName()), List.of()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.afterPropertiesSet();
        return factory.getObject();
    }
}
