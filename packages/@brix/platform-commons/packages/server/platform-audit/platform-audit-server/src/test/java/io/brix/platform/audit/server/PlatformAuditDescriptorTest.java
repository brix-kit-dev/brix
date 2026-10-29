/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.audit.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;

import io.brix.platform.audit.internal.PlatformAuditAdministration;
import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.manifest.model.PluginManifest;

/**
 * The platform audit owner's descriptor (super-admin §1.3a, root BRX-RS-MANIFEST-005, HOST-021).
 */
class PlatformAuditDescriptorTest {

    private final PluginManifest manifest = new PluginManifestLoader().loadActiveFromClasspath(PlatformAuditPlugin.class);

    @Test
    void theDescriptorListsTheOwnersThreeArtifacts() {
        assertThat(manifest.pluginId()).isEqualTo("platform-audit");
        assertThat(manifest.getModules())
            .extracting(PluginManifest.Module::getArtifactId, PluginManifest.Module::getModuleKind)
            .containsExactly(
                tuple("platform-audit-api", "plugin-api"),
                tuple("platform-audit-core", "plugin-core"),
                tuple("platform-audit-server", "plugin-server"));
    }

    @Test
    void theDescriptorProvidesTheAuditContractAtItsExactVersion() {
        assertThat(manifest.getInternalContracts().getProvides()).singleElement().satisfies(contract -> {
            assertThat(contract.getContractId()).isEqualTo(PlatformAuditAdministration.CONTRACT_ID);
            assertThat(contract.getContractType()).isEqualTo(PlatformAuditAdministration.class.getName());
            assertThat(contract.getContractVersion()).isEqualTo(PlatformAuditAdministration.CONTRACT_VERSION);
            assertThat(contract.getOwner()).isEqualTo("platform-audit");
        });
    }

    @Test
    void theOwnersMigrationsAreWhereTheDescriptorSays() {
        assertThat(manifest.getData().getStorageId()).isEqualTo("platform_audit");
        assertThat(manifest.getData().getMigrationLocation()).isEqualTo("db/migration/platform-audit");
        ClassLoader classes = PlatformAuditPlugin.class.getClassLoader();
        assertThat(classes.getResource("db/migration/platform-audit/V001__platform_audit_adopt_table.sql")).isNotNull();
        assertThat(classes.getResource("db/migration/platform-audit/V002__platform_audit_policy_use_fields.sql")).isNotNull();
        assertThat(classes.getResource("db/migration/platform-audit/V003__platform_audit_inbox.sql")).isNotNull();
    }

    @Test
    void theOwnerReceivesIdentityAuditFactsThroughItsInbox() {
        // P4 design §4.2: CRITICAL facts need the persistent inbox (root BRX-RS-MANIFEST-025, MESSAGE-025).
        assertThat(manifest.getData().getInbox()).isEqualTo("platform_audit_inbox");
        assertThat(manifest.getData().getOutbox()).as("the owner publishes nothing").isNull();
        assertThat(manifest.getEvents().getPublishes()).isEmpty();
        assertThat(manifest.getEvents().getSubscribes()).singleElement().satisfies(subscription -> {
            assertThat(subscription.getEventType()).isEqualTo("IdentityAuditFactRecorded");
            assertThat(subscription.getSchemaRange()).isEqualTo(">=1.0.0 <2.0.0");
            assertThat(subscription.getHandlerId()).isEqualTo(PlatformAuditPlugin.HANDLER_IDENTITY_AUDIT_FACT);
            assertThat(subscription.getPublisherPolicyRef()).isEqualTo("platform-identity-owner");
            assertThat(subscription.getIdempotencyPolicyRef()).isEqualTo("persistent-inbox");
        });
    }
}
