/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Root BRX-RS-DATA-031: an Owner's manifest names its migrations, so a plugin the Host does not list still
 * gets its tables (Kafka design §14 K19).
 */
class FlywayModuleMigrationRunnerTest {

    @TempDir
    Path roots;

    @Test
    void everyManifestThatDeclaresMigrationsIsMigratedInAHistoryTableOfItsStorage() throws IOException {
        try (URLClassLoader manifests = new URLClassLoader(new URL[] {
                manifest("app-orders", "app_orders", "db/migration/app-orders"),
                manifest("app-billing", "app_billing", "classpath:db/migration/app-billing/"),
                manifest("app-reports", "app_reports", null)}, null)) {

            assertEquals(List.of(
                    new FlywayModuleMigrationRunner.Migration("app-billing", "classpath:db/migration/app-billing",
                        "flyway_schema_history_app_billing"),
                    new FlywayModuleMigrationRunner.Migration("app-orders", "classpath:db/migration/app-orders",
                        "flyway_schema_history_app_orders")),
                FlywayModuleMigrationRunner.declaredMigrations(manifests, "flyway_schema_history_"));
        }
    }

    @Test
    void anOwnerGetsTheMessagingTablesItsManifestNeeds() throws IOException {
        try (URLClassLoader manifests = new URLClassLoader(new URL[] {
                messagingManifest("shop", "shop", true),
                messagingManifest("ledger", "ledger", false),
                manifest("app-reports", "app_reports", null)}, null)) {

            assertEquals(List.of(
                    "ledger_outbox brix_msg_history_ledger_outbox classpath:META-INF/brix/messaging/postgresql/outbox",
                    "ledger_inbox brix_msg_history_ledger_inbox classpath:META-INF/brix/messaging/postgresql/inbox",
                    "shop_outbox brix_msg_history_shop_outbox classpath:META-INF/brix/messaging/postgresql/outbox",
                    "shop_inbox brix_msg_history_shop_inbox classpath:META-INF/brix/messaging/postgresql/inbox",
                    "shop_command_idempotency brix_msg_history_shop_idempotency"
                        + " classpath:META-INF/brix/messaging/postgresql/command-idempotency",
                    "shop_command_outcome brix_msg_history_shop_command_outcome"
                        + " classpath:META-INF/brix/messaging/postgresql/command-outcome"),
                FlywayModuleMigrationRunner.messagingMigrations(manifests).stream()
                    .map(migration -> migration.table() + " " + migration.history() + " " + migration.location())
                    .toList());
        }
    }

    @Test
    void anOwnerOfReplayableWritesGetsTheEndpointKeyTable() throws IOException {
        // Idempotency design D79: an operational endpoint's record goes to the owner of its write.
        URL owner = replayingManifest("tickets", "tickets", "not-applicable");
        Path manifest = roots.resolve("tickets-replaying/META-INF/brix/plugin-manifest.yaml");
        Files.writeString(manifest, Files.readString(manifest) + """
            internalContracts:
              provides:
                - contractId: tickets.administration
                  contractType: io.example.TicketAdministration
                  contractVersion: 1.0.0
                  providerId: tickets.internal
                  owner: tickets
                  replayableWrites: [close]
            """);
        try (URLClassLoader manifests = new URLClassLoader(new URL[] {owner}, null)) {

            assertEquals(List.of("tickets_endpoint_idempotency"),
                FlywayModuleMigrationRunner.messagingMigrations(manifests).stream()
                    .map(migration -> migration.table())
                    .toList());
        }
    }

    @Test
    void anOwnerWhoseEndpointHasItsResultsReplayedGetsTheEndpointKeyTable() throws IOException {
        // Standard-link plan point 10a: no other owner has this table.
        try (URLClassLoader manifests = new URLClassLoader(new URL[] {
                replayingManifest("tickets", "tickets", "required"),
                replayingManifest("reports", "reports", "not-applicable")}, null)) {

            assertEquals(List.of("tickets_endpoint_idempotency brix_msg_history_tickets_endpoint_keys"
                    + " classpath:META-INF/brix/messaging/postgresql/endpoint-idempotency"),
                FlywayModuleMigrationRunner.messagingMigrations(manifests).stream()
                    .map(migration -> migration.table() + " " + migration.history() + " " + migration.location())
                    .toList());
        }
    }

    @Test
    void onPostgresqlThePlatformCreatesTheMessagingTablesOnceAndAdoptsTheOwnersOwn() throws Exception {
        String url = System.getenv("BRIX_P0_POSTGRES_URL");
        assumeTrue(url != null && !url.isBlank(), "needs a throwaway PostgreSQL");
        DataSource dataSource = new DriverManagerDataSource(url, System.getenv("BRIX_P0_POSTGRES_USER"),
            System.getenv("BRIX_P0_POSTGRES_PASSWORD"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String fresh = "k19n" + Long.toHexString(System.nanoTime() & 0xffffffL);
        String owned = "k19o" + Long.toHexString(System.nanoTime() & 0xffffffL);
        jdbc.execute("CREATE TABLE " + owned + "_outbox (message_id VARCHAR(64) PRIMARY KEY, note VARCHAR(20))");
        jdbc.update("INSERT INTO " + owned + "_outbox VALUES ('kept', 'owner row')");
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader manifests = new URLClassLoader(new URL[] {
                messagingManifest("fresh-owner", fresh, true), messagingManifest("owned-owner", owned, false)},
                getClass().getClassLoader())) {
            Thread.currentThread().setContextClassLoader(manifests);
            FlywayModuleMigrationProperties properties = new FlywayModuleMigrationProperties();
            properties.setFailOnError(true);

            List<FlywayModuleMigrationRunner.ModuleMigrationResult> first =
                new FlywayModuleMigrationRunner(dataSource, properties).getResults();
            List<FlywayModuleMigrationRunner.ModuleMigrationResult> second =
                new FlywayModuleMigrationRunner(dataSource, properties).getResults();

            // The command key table has two migrations: V2 adds the request fingerprint (standard-link plan point 8).
            assertEquals(List.of("fresh-owner:outbox 1", "fresh-owner:inbox 1", "fresh-owner:command-idempotency 2",
                    "fresh-owner:command-outcome 1", "owned-owner:outbox 0", "owned-owner:inbox 1"),
                first.stream()
                    .filter(result -> result.getModule().contains("-owner:"))
                    .map(result -> result.getModule() + " " + result.getMigrationsApplied())
                    .toList());
            assertTrue(second.stream().allMatch(result -> result.isSuccess() && result.getMigrationsApplied() == 0));
            assertEquals(List.of("message_id", "seq"), jdbc.queryForList("SELECT column_name FROM"
                + " information_schema.columns WHERE table_name = ? AND column_name IN ('message_id', 'seq')"
                + " ORDER BY column_name", String.class, fresh + "_outbox"));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + fresh + "_command_idempotency",
                Integer.class));
            assertEquals(List.of("request_fingerprint"), jdbc.queryForList("SELECT column_name FROM"
                + " information_schema.columns WHERE table_name = ? AND column_name = 'request_fingerprint'",
                String.class, fresh + "_command_idempotency"));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + fresh + "_command_outcome",
                Integer.class), "standard-link plan point 7: a provider of commands gets its outcome table");
            assertEquals("owner row", jdbc.queryForObject("SELECT note FROM " + owned + "_outbox", String.class),
                "an Owner's own table is adopted, not recreated");
            assertEquals("BASELINE", jdbc.queryForObject("SELECT type FROM brix_msg_history_" + owned + "_outbox"
                + " WHERE version = '1'", String.class));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            for (String storage : List.of(fresh, owned)) {
                for (String table : List.of("_outbox", "_inbox", "_command_idempotency", "_command_outcome")) {
                    jdbc.execute("DROP TABLE IF EXISTS " + storage + table);
                }
                for (String history : List.of("outbox", "inbox", "idempotency", "command_outcome")) {
                    jdbc.execute("DROP TABLE IF EXISTS brix_msg_history_" + storage + "_" + history);
                }
            }
        }
    }

    @Test
    void onAnotherDatabaseTheMessagingTablesAreLeftToTheOwner() throws Exception {
        DataSource h2 = new DriverManagerDataSource("jdbc:h2:mem:messaging-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1",
            "sa", "");
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader manifests = new URLClassLoader(new URL[] {messagingManifest("shop", "shop", true)},
                getClass().getClassLoader())) {
            Thread.currentThread().setContextClassLoader(manifests);
            FlywayModuleMigrationProperties properties = new FlywayModuleMigrationProperties();
            properties.setFailOnError(true);

            assertTrue(new FlywayModuleMigrationRunner(h2, properties).getResults().stream()
                .noneMatch(result -> result.getModule().startsWith("shop:")));
            assertEquals(0, new JdbcTemplate(h2).queryForObject("SELECT COUNT(*) FROM information_schema.tables"
                + " WHERE LOWER(table_name) = 'shop_outbox'", Integer.class));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    /** A manifest whose Owner publishes and subscribes, and with {@code provides} also handles a command. */
    private URL replayingManifest(String pluginId, String storageId, String idempotency) throws IOException {
        Path root = roots.resolve(pluginId + "-replaying");
        Path manifest = root.resolve("META-INF/brix/plugin-manifest.yaml");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, """
            apiVersion: brix.io/v1
            kind: Plugin
            metadata:
              pluginId: %1$s
              name: %1$s
              version: 3.3.0
              vendor: Brix
              license: Apache-2.0
            runtime:
              compiledAgainst: 3.3.0
              supportedRange: ">=3.3.0 <4.0.0"
            modules:
              - groupId: io.brix
                artifactId: %1$s-server
                version: 3.3.0
                moduleKind: plugin-server
            endpoints:
              provides:
                - endpointId: %1$s.create.v1
                  transport: http
                  method: POST
                  path: /api/%1$s/things
                  request:
                    schemaRef: brix://schemas/%1$s/create-request/1.0.0
                  response:
                    schemaRef: brix://schemas/%1$s/created/1.0.0
                  handlerId: %1$s.create.v1
                  authentication:
                    mode: required
                  authorization:
                    permissions: [%1$s:thing:create]
                  tenantContext:
                    mode: required
                  timeout:
                    policyRef: endpoint-default
                  idempotency:
                    mode: %3$s
            capabilities:
              required:
                - id: io.runtime.sdk.capability.PersistenceCapability
                  version: ">=3.2.0 <4.0.0"
              optional: []
            data:
              storageId: %2$s
            """.formatted(pluginId, storageId, idempotency));
        return root.toUri().toURL();
    }

    private URL messagingManifest(String pluginId, String storageId, boolean provides) throws IOException {
        Path root = roots.resolve(pluginId + "-messaging");
        Path manifest = root.resolve("META-INF/brix/plugin-manifest.yaml");
        Files.createDirectories(manifest.getParent());
        String commands = provides ? """
              provides:
                - commandType: ReserveStock
                  request:
                    schemaRef: brix://schemas/%s/reserve-stock/1.0.0
                  handlerId: reserve-stock.v1
                  scopePolicyRef: platform
                  accessPolicyRef: platform
                  timeoutPolicyRef: command-standard
                  concurrencyPolicyRef: partition-serial
                  retryPolicyRef: command-transient-retry
                  idempotencyPolicyRef: command-key
            """.formatted(pluginId) : "  provides: []\n";
        Files.writeString(manifest, """
            apiVersion: brix.io/v1
            kind: Plugin
            metadata:
              pluginId: %1$s
              name: %1$s
              version: 3.3.0
              vendor: Brix
              license: Apache-2.0
            runtime:
              compiledAgainst: 3.3.0
              supportedRange: ">=3.3.0 <4.0.0"
            modules:
              - groupId: io.brix
                artifactId: %1$s-server
                version: 3.3.0
                moduleKind: plugin-server
            capabilities:
              required:
                - id: io.runtime.sdk.capability.PersistenceCapability
                  version: ">=3.2.0 <4.0.0"
              optional: []
            queries:
              provides: []
              consumes: []
            commands:
            %3$s  consumes: []
            events:
              publishes: []
              subscribes:
                - subscriptionId: member-projection
                  eventType: MemberAccepted
                  schemaRange: ">=1.0.0 <2.0.0"
                  handlerId: member-projection.v1
                  retryPolicyRef: event-standard
                  idempotencyPolicyRef: persistent-inbox
            data:
              storageId: %2$s
              outbox: %2$s_outbox
              inbox: %2$s_inbox
            """.formatted(pluginId, storageId, commands));
        return root.toUri().toURL();
    }

    private URL manifest(String pluginId, String storageId, String migrationLocation) throws IOException {
        Path root = roots.resolve(pluginId);
        Path manifest = root.resolve("META-INF/brix/plugin-manifest.yaml");
        Files.createDirectories(manifest.getParent());
        String data = migrationLocation == null
            ? "  storageId: " + storageId
            : "  storageId: " + storageId + "\n  schema: " + pluginId + "\n  migrationLocation: " + migrationLocation;
        Files.writeString(manifest, """
            apiVersion: brix.io/v1
            kind: Plugin
            metadata:
              pluginId: %s
              name: %s
              version: 3.2.0
              displayName: Migration test owner
              vendor: Brix Platform
              license: Apache-2.0
            runtime:
              compiledAgainst: 3.2.0
              supportedRange: ">=3.0.10 <4.0.0"
            modules:
              - groupId: io.brix
                artifactId: infra-adapter-database
                version: 3.2.0
                moduleKind: plugin-server
            endpoints:
              provides: []
            capabilities:
              required: []
              optional: []
            queries:
              provides: []
              consumes: []
            commands:
              provides: []
              consumes: []
            events:
              publishes: []
              subscribes: []
            tasks:
              provides: []
            internalContracts:
              provides: []
            data:
            %s
            permissions: []
            config: []
            ui:
              web:
                enabled: false
            """.formatted(pluginId, pluginId, data));
        return root.toUri().toURL();
    }
}
