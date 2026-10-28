/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.audit.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.brix.platform.audit.api.PlatformAuditActions;
import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.manifest.model.PluginManifest;

/**
 * The owner's migrations on the reference DCP, with the custodial INSERT the runtime policy audit writer
 * makes (root BRX-RS-DATA-046): the INSERT names exactly the columns the descriptor registers.
 *
 * <p>V001 and V002 use PostgreSQL DDL, so this runs only when {@code BRIX_P0_POSTGRES_URL},
 * {@code BRIX_P0_POSTGRES_USER} and {@code BRIX_P0_POSTGRES_PASSWORD} name a throwaway PostgreSQL
 * database, the same switch as the persistence adapter's P0 suite. Each test migrates a schema of its
 * own and drops it.</p>
 */
class PlatformAuditMigrationTest {

    private static final String URL = System.getenv("BRIX_P0_POSTGRES_URL");
    private static final String CUSTODIAN = "runtime.tenant-policy-audit";

    private final PluginManifest.CustodialWrite registration = new PluginManifestLoader()
        .loadActiveFromClasspath(PlatformAuditPlugin.class).getData().getCustodialWrites().get(0);
    private final AtomicLong ids = new AtomicLong(374829105938497537L);
    private String schema;
    private Connection connection;

    @BeforeEach
    void migrate() throws SQLException {
        assumeTrue(URL != null && !URL.isBlank(), "BRIX_P0_POSTGRES_URL names no throwaway PostgreSQL database");
        schema = "platform_audit_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Flyway.configure()
            .dataSource(URL, System.getenv("BRIX_P0_POSTGRES_USER"), System.getenv("BRIX_P0_POSTGRES_PASSWORD"))
            .schemas(schema)
            .locations("classpath:db/migration/platform-audit")
            .table("flyway_schema_history_platform_audit")
            .load()
            .migrate();
        connection = DriverManager.getConnection(URL, System.getenv("BRIX_P0_POSTGRES_USER"),
            System.getenv("BRIX_P0_POSTGRES_PASSWORD"));
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
        }
    }

    @AfterEach
    void dropSchema() throws SQLException {
        if (connection != null) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
            connection.close();
        }
    }

    @Test
    void theCustodianWritesEveryRegisteredActionWithTheRegisteredColumns() throws SQLException {
        assertThat(registration.getTable()).isEqualTo("sys_platform_audit_log");
        assertThat(registration.getCustodian()).isEqualTo(CUSTODIAN);

        for (String action : registration.getActions()) {
            custodialInsert(action, CUSTODIAN, "trace-" + action.toLowerCase().replace('_', '-'));
        }

        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                    "SELECT action, writer, created_at FROM sys_platform_audit_log ORDER BY action")) {
            int count = 0;
            while (rows.next()) {
                count++;
                assertThat(rows.getString("writer")).isEqualTo(CUSTODIAN);
                assertThat(rows.getTimestamp("created_at")).as("the table fills the creation time").isNotNull();
            }
            assertThat(count).isEqualTo(registration.getActions().size());
        }
        assertThat(registration.getActions()).containsExactlyInAnyOrderElementsOf(PlatformAuditActions.POLICY_USES);
    }

    @Test
    void theDatabaseBacksUpTheRegistration() {
        assertThatThrownBy(() -> custodialInsert(PlatformAuditActions.TENANT_CREATED, CUSTODIAN, "trace-0002-abcdef"))
            .as("the custodian writes only the registered actions")
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> custodialInsert(PlatformAuditActions.LOGIN_TENANT_DISCOVERY, "platform-identity",
                "trace-0003-abcdef"))
            .as("nobody else writes a policy action")
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> custodialInsert(PlatformAuditActions.LOGIN_TENANT_DISCOVERY, CUSTODIAN, null))
            .as("a policy row carries a trace id")
            .isInstanceOf(SQLException.class);
    }

    @Test
    void platformIdentityKeepsWritingItsOwnRowsDuringTheSplit() throws SQLException {
        // EX-2026-003: platform-identity's entity maps neither writer nor the new columns.
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO sys_platform_audit_log"
                + " (id, operator_identity_id, action, resource_type, resource_id, description, success)"
                + " VALUES (?, 7, 'IDENTITY_ACTIVATED', 'IDENTITY', '7', 'activated', TRUE)")) {
            insert.setLong(1, 1035351323601539001L);
            insert.executeUpdate();
        }

        try (Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery("SELECT writer FROM sys_platform_audit_log")) {
            assertThat(row.next()).isTrue();
            assertThat(row.getString("writer")).isEqualTo("platform-identity");
        }
    }

    /** The INSERT the runtime policy audit writer makes, over exactly the registered columns. */
    private void custodialInsert(String action, String writer, String traceId) throws SQLException {
        Map<String, Object> values = new HashMap<>();
        values.put("id", ids.getAndIncrement());
        values.put("operator_identity_id", 5L);
        values.put("action", action);
        values.put("resource_type", "SELF");
        values.put("resource_id", null);
        values.put("affected_tenants", "[910001,910002]");
        values.put("context", "{\"entry\":\"login\",\"matched\":\"2\"}");
        values.put("success", true);
        values.put("error_code", null);
        values.put("execution_identity", "runtime:policy:login-tenant-discovery");
        values.put("reason_code", "login-tenant-discovery");
        values.put("trace_id", traceId);
        values.put("source_ip", "203.0.113.0/24");
        values.put("user_agent", null);
        values.put("writer", writer);
        List<String> columns = registration.getColumns();
        assertThat(values.keySet()).containsExactlyInAnyOrderElementsOf(columns);
        String sql = "INSERT INTO " + registration.getTable() + " (" + String.join(", ", columns) + ") VALUES ("
            + columns.stream().map(column -> column.equals("affected_tenants") || column.equals("context")
                ? "CAST(? AS JSONB)" : "?").collect(Collectors.joining(", ")) + ")";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (int i = 0; i < columns.size(); i++) {
                Object value = values.get(columns.get(i));
                if (value == null) {
                    insert.setNull(i + 1, Types.VARCHAR);
                } else {
                    insert.setObject(i + 1, value);
                }
            }
            insert.executeUpdate();
        }
    }
}
