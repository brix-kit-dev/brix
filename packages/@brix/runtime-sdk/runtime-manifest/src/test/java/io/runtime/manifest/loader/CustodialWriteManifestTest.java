/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.manifest.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.runtime.manifest.model.PluginManifest;
import io.runtime.manifest.validation.ManifestValidationException;

/**
 * Manifest {@code data.custodialWrites} (root BRX-RS-DATA-045, MANIFEST-031).
 */
class CustodialWriteManifestTest {

    private final PluginManifestLoader loader = new PluginManifestLoader();

    @Test
    void aCustodialWriteRegistrationLoads() {
        PluginManifest manifest = loader.loadFromString(manifest("platform_audit", registration(
            "sys_platform_audit_log", "runtime.tenant-policy-audit", "[INSERT]", "[action, trace_id, writer]",
            "brix://schemas/platform-audit/policy-use-record/1.0.0")));

        assertThat(manifest.getData().getCustodialWrites()).singleElement().satisfies(write -> {
            assertThat(write.getTable()).isEqualTo("sys_platform_audit_log");
            assertThat(write.getCustodian()).isEqualTo("runtime.tenant-policy-audit");
            assertThat(write.getOperations()).containsExactly("INSERT");
            assertThat(write.getColumns()).containsExactly("action", "trace_id", "writer");
            assertThat(write.getActions()).containsExactly("LOGIN_TENANT_DISCOVERY");
        });
    }

    @Test
    void onlyARegisteredRuntimeCustodianMayWrite() {
        assertRejected(registration("sys_platform_audit_log", "platform-tenant", "[INSERT]", "[action]",
            "brix://schemas/platform-audit/policy-use-record/1.0.0"), "custodian");
    }

    @Test
    void aCustodianMayOnlyInsert() {
        assertRejected(registration("sys_platform_audit_log", "runtime.tenant-policy-audit", "[INSERT, UPDATE]", "[action]",
            "brix://schemas/platform-audit/policy-use-record/1.0.0"), "operations");
    }

    @Test
    void theColumnsAreListedOnceEach() {
        assertRejected(registration("sys_platform_audit_log", "runtime.tenant-policy-audit", "[INSERT]", "[]",
            "brix://schemas/platform-audit/policy-use-record/1.0.0"), "columns");
        assertRejected(registration("sys_platform_audit_log", "runtime.tenant-policy-audit", "[INSERT]", "[action, action]",
            "brix://schemas/platform-audit/policy-use-record/1.0.0"), "columns");
    }

    @Test
    void theRecordSchemaIsAVersionedReference() {
        assertRejected(registration("sys_platform_audit_log", "runtime.tenant-policy-audit", "[INSERT]", "[action]",
            "policy-use-record"), "recordSchemaRef");
    }

    @Test
    void aTableAndCustodianAreRegisteredOnce() {
        String twice = registration("sys_platform_audit_log", "runtime.tenant-policy-audit", "[INSERT]", "[action]",
            "brix://schemas/platform-audit/policy-use-record/1.0.0")
            + item("sys_platform_audit_log", "runtime.tenant-policy-audit", "[INSERT]", "[action]",
            "brix://schemas/platform-audit/policy-use-record/1.0.0");
        assertRejected(twice, "registered more than once");
    }

    @Test
    void onlyADataOwnerRegistersCustodialWrites() {
        String yaml = manifest(null, registration("sys_platform_audit_log", "runtime.tenant-policy-audit", "[INSERT]",
            "[action]", "brix://schemas/platform-audit/policy-use-record/1.0.0"));
        assertThatThrownBy(() -> loader.loadFromString(yaml))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("Data Owner");
    }

    private void assertRejected(String registration, String message) {
        assertThatThrownBy(() -> loader.loadFromString(manifest("platform_audit", registration)))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining(message);
    }

    private static String registration(String table, String custodian, String operations, String columns, String schemaRef) {
        return "  custodialWrites:\n" + item(table, custodian, operations, columns, schemaRef);
    }

    private static String item(String table, String custodian, String operations, String columns, String schemaRef) {
        return "    - table: " + table + "\n"
            + "      custodian: " + custodian + "\n"
            + "      operations: " + operations + "\n"
            + "      columns: " + columns + "\n"
            + "      actions: [LOGIN_TENANT_DISCOVERY]\n"
            + "      recordSchemaRef: \"" + schemaRef + "\"\n";
    }

    private static String manifest(String storageId, String registration) {
        return """
            apiVersion: brix.io/v1
            kind: Plugin
            metadata:
              pluginId: platform-audit
              name: platform-audit
              version: 3.2.0
              vendor: Brix
              license: Apache-2.0
            runtime:
              compiledAgainst: 3.2.0
              supportedRange: ">=3.0.10 <4.0.0"
            modules:
              - groupId: io.brix.platform
                artifactId: platform-audit-server
                version: 3.2.0
                moduleKind: plugin-server
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
            data:
            """ + (storageId == null ? "" : "  storageId: " + storageId + "\n") + registration;
    }
}
