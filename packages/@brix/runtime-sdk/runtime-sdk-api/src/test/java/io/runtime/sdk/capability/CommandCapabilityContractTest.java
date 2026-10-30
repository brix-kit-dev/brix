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
import io.runtime.sdk.command.CommandRequest;
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
        // Root BRX-RS-MESSAGE-013: the receipt says the command is queued, nothing about its outcome.
        CommandReceipt receipt = new CommandReceipt(
            "command-1",
            "TenantMemberCommand",
            Instant.parse("2026-07-28T00:00:01Z"),
            CommandReceipt.Status.QUEUED);

        assertEquals("command-1", receipt.commandId());
        assertEquals("TenantMemberCommand", receipt.commandType());
        assertEquals(Instant.parse("2026-07-28T00:00:01Z"), receipt.persistedAt());
        assertEquals(CommandReceipt.Status.QUEUED, receipt.status());
    }

    @Test
    void aRequestNamesItsTypePayloadAndIdempotencyKey() {
        CommandRequest<String> request = CommandRequest.of("ReserveStock", "sku-1", "order", "order-7")
            .forTenant("910001");

        assertEquals("910001", request.tenantId());
        assertEquals(null, request.partitionKey());
        assertThrows(IllegalArgumentException.class, () -> CommandRequest.of("ReserveStock", "sku-1", "order", " "));
    }

}
