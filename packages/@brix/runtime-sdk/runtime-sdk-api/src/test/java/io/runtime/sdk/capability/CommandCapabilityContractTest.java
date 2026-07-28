/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.runtime.sdk.command.CommandEnvelope;
import io.runtime.sdk.event.EventScope;

class CommandCapabilityContractTest {

    @Test
    void tenantScopedCommandRequiresTenantId() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new CommandEnvelope<>(
            "command-1",
            "TenantMemberCommand",
            "1.0.0",
            Instant.parse("2026-07-28T00:00:00Z"),
            "sender-plugin",
            EventScope.TENANT,
            "",
            "correlation-1",
            null,
            "00-00000000000000000000000000000001-0000000000000001-01",
            null,
            "tenant-100",
            "tenant-member",
            "idem-1",
            "payload"));

        assertEquals("tenantId must not be blank", error.getMessage());
    }

    @Test
    void platformScopedCommandRejectsTenantId() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new CommandEnvelope<>(
            "command-1",
            "PlatformCommand",
            "1.0.0",
            Instant.parse("2026-07-28T00:00:00Z"),
            "sender-plugin",
            EventScope.PLATFORM,
            "100",
            "correlation-1",
            null,
            "00-00000000000000000000000000000001-0000000000000001-01",
            null,
            "platform",
            "platform",
            "idem-1",
            "payload"));

        assertEquals("tenantId must be empty for PLATFORM scoped commands", error.getMessage());
    }

    @Test
    void receiptOnlyContainsDurableSenderFacts() {
        CommandReceipt receipt = new CommandReceipt(
            "command-1",
            "TenantMemberCommand",
            Instant.parse("2026-07-28T00:00:01Z"));

        assertEquals("command-1", receipt.commandId());
        assertEquals("TenantMemberCommand", receipt.commandType());
        assertEquals(Instant.parse("2026-07-28T00:00:01Z"), receipt.acceptedAt());
    }
}
