/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.dataowner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that a Data Owner has auditable reliable-message fixture evidence.
 */
public final class ReliableMessageEvidenceVerifier {

    public static final String MISSING_RELIABLE_MESSAGE_EVIDENCE = "RELIABLE_MESSAGE_EVIDENCE_MISSING";

    /**
     * Verifies platform-tenant FIRST_OWNER reliable-message fixture coverage.
     *
     * @param repoRoot repository root
     * @return violations
     */
    public List<SqlMigrationViolation> verifyPlatformTenantFirstOwner(Path repoRoot) {
        List<RequiredSourceEvidence> required = List.of(
            new RequiredSourceEvidence(
                "packages/@brix/platform-commons/packages/server/platform-tenant/src/test/java/"
                    + "io/brix/platform/tenant/outbox/PlatformTenantOutboxJpaTransactionTest.java",
                List.of(
                    "producerOutboxAppendCommitsWithOwnerTransaction",
                    "producerOutboxAppendRollsBackWithOwnerTransaction")),
            new RequiredSourceEvidence(
                "packages/@brix/platform-commons/packages/server/platform-tenant/src/test/java/"
                    + "io/brix/platform/tenant/outbox/PlatformTenantPersistentInboxE2ETest.java",
                List.of(
                    "duplicateDeliveryAfterAckLossDoesNotRepeatSideEffect",
                    "handlerFailureRollsBackInboxAndProjectionSoRetryCanConsume",
                    "firstOwnerAcceptanceOutboxCommitCanBeConsumedIntoPersistentSideEffect")),
            new RequiredSourceEvidence(
                "packages/@brix/platform-commons/packages/server/platform-tenant/src/main/resources/"
                    + "META-INF/brix/plugin-manifest.yaml",
                List.of(
                    "eventType: TenantFirstOwnerAccepted",
                    "reliability: CRITICAL",
                    "idempotencyPolicyRef: persistent-inbox",
                    "outbox: platform_tenant_outbox",
                    "inbox: platform_tenant_inbox"))
        );

        List<SqlMigrationViolation> violations = new ArrayList<>();
        for (RequiredSourceEvidence evidence : required) {
            Path source = repoRoot.resolve(evidence.relativePath()).normalize();
            if (!Files.isRegularFile(source)) {
                violations.add(violation(evidence.relativePath(), "required evidence file is missing"));
                continue;
            }
            String text = read(source);
            for (String needle : evidence.requiredText()) {
                if (!text.contains(needle)) {
                    violations.add(violation(evidence.relativePath(), "required evidence is missing: " + needle));
                }
            }
        }
        return List.copyOf(violations);
    }

    private static SqlMigrationViolation violation(String path, String message) {
        return new SqlMigrationViolation(
            MISSING_RELIABLE_MESSAGE_EVIDENCE,
            path,
            "platform-tenant",
            "TenantFirstOwnerAccepted",
            message);
    }

    private static String read(Path source) {
        try {
            return Files.readString(source);
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to read " + source, ex);
        }
    }

    private record RequiredSourceEvidence(String relativePath, List<String> requiredText) {
    }
}
