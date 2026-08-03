/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.dataowner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataOwnerPhase4Test {

    @Test
    void platformTenantCleanMigrationPassesOwnerSqlLint() throws IOException {
        Path repoRoot = locateRepoRoot();
        Path migration = repoRoot.resolve("packages/@brix/platform-commons/packages/server/platform-tenant/"
            + "src/main/resources/db/migration/platform-tenant/V001__platform_tenant_clean_schema.sql");
        SqlMigrationDocument document = new SqlMigrationDocument(
            "V001__platform_tenant_clean_schema.sql",
            "platform-tenant",
            Files.readString(migration));

        assertTrue(new SqlMigrationLinter(registry()).lint(document).isEmpty());
    }

    @Test
    void sqlLinterRejectsCrossOwnerForeignKeyJoinAndWrite() {
        SqlMigrationDocument document = new SqlMigrationDocument(
            "V999__cross_owner.sql",
            "platform-tenant",
            """
            CREATE TABLE sys_tenant_extension (
                id BIGINT PRIMARY KEY,
                booking_id BIGINT REFERENCES booking_order(id)
            );
            INSERT INTO booking_order(id) VALUES (1);
            SELECT t.id FROM sys_tenant t JOIN booking_order b ON b.tenant_id = t.id;
            """);

        List<SqlMigrationViolation> violations = new SqlMigrationLinter(registry()).lint(document);

        assertTrue(violations.stream().anyMatch(v -> v.code().equals(SqlMigrationLinter.CROSS_OWNER_REFERENCE)));
        assertTrue(violations.stream().anyMatch(v -> v.code().equals(SqlMigrationLinter.CROSS_OWNER_WRITE)));
    }

    @Test
    void migrationHashLedgerDetectsReleasedFileMutation(@TempDir Path tempDir) throws IOException {
        Path migration = tempDir.resolve("db/V001__schema.sql");
        Files.createDirectories(migration.getParent());
        Files.writeString(migration, "CREATE TABLE sys_tenant(id BIGINT PRIMARY KEY);");
        String ledger = "db/V001__schema.sql " + MigrationHashLedger.sha256(migration);

        assertTrue(MigrationHashLedger.parse(ledger).verify(tempDir).isEmpty());

        Files.writeString(migration, "CREATE TABLE sys_tenant(id BIGINT PRIMARY KEY, code VARCHAR(64));");

        List<SqlMigrationViolation> violations = MigrationHashLedger.parse(ledger).verify(tempDir);
        assertEquals(MigrationHashLedger.HASH_MISMATCH, violations.get(0).code());
    }

    @Test
    void databasePrivilegeContractRejectsCrossOwnerGrantAndUnapprovedPrivilege() {
        DatabasePrivilegeContract contract = new DatabasePrivilegeContract(
            "platform-tenant",
            "platform_tenant_writer",
            Set.of("SELECT", "INSERT", "UPDATE", "DELETE"));
        String grants = """
            GRANT SELECT, INSERT ON sys_tenant TO platform_tenant_writer;
            GRANT INSERT ON booking_order TO platform_tenant_writer;
            GRANT TRUNCATE ON sys_tenant TO platform_tenant_writer;
            """;

        List<SqlMigrationViolation> violations = new DatabasePrivilegeVerifier(registry()).verify(grants, contract);

        assertTrue(violations.stream().anyMatch(v -> v.code().equals(DatabasePrivilegeVerifier.CROSS_OWNER_GRANT)));
        assertTrue(violations.stream().anyMatch(v -> v.code().equals(DatabasePrivilegeVerifier.UNAPPROVED_PRIVILEGE)));
    }

    @Test
    void platformTenantFirstOwnerReliableMessageEvidenceIsPresent() {
        List<SqlMigrationViolation> violations =
            new ReliableMessageEvidenceVerifier().verifyPlatformTenantFirstOwner(locateRepoRoot());

        assertTrue(violations.isEmpty(), () -> "Missing reliable message evidence: " + violations);
    }

    private static DataOwnerRegistry registry() {
        return DataOwnerRegistry.of(List.of(
            new DataOwnerDefinition(
                "platform-tenant",
                "packages/@brix/platform-commons/packages/server/platform-tenant",
                Set.of(
                    "sys_tenant",
                    "sys_identity",
                    "sys_platform_admin",
                    "sys_tenant_member",
                    "sys_tenant_principal",
                    "sys_organization",
                    "biz_user_profile",
                    "sys_tenant_config",
                    "sys_tenant_invitation",
                    "sys_installation_quota",
                    "sys_setup_token",
                    "sys_bootstrap_state",
                    "biz_audit_log",
                    "biz_tenant_audit_log",
                    "sys_platform_audit_log",
                    "platform_tenant_outbox",
                    "platform_tenant_inbox",
                    "platform_tenant_first_owner_projection",
                    "platform_tenant_command_idempotency",
                    "sys_tenant_extension"),
                "db/migration/platform-tenant",
                "platform_tenant_outbox",
                "platform_tenant_inbox"),
            new DataOwnerDefinition(
                "app-booking",
                "packages/@brix/enterprise-solutions/app-booking",
                Set.of("booking_order"),
                "db/migration/app-booking",
                "",
                "")));
    }

    private static Path locateRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("packages/@brix/platform-devtools"))
                && Files.exists(current.resolve("packages/@brix/platform-commons"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root");
    }
}
