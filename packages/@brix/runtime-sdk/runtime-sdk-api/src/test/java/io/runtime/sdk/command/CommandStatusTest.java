/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.runtime.sdk.capability.CommandSubmission;

/**
 * A command's status as its sender reads it (root BRX-RS-MESSAGE-013; standard-link plan point 7).
 */
class CommandStatusTest {

    @Test
    void theStatesAreTheOnesTheDesignNames() {
        assertEquals(List.of("QUEUED", "SUCCEEDED", "REJECTED", "DUPLICATE", "CONFLICT", "PARKED", "DISCARDED",
            "UNKNOWN"), Arrays.stream(CommandStatus.State.values()).map(Enum::name).toList());
    }

    @Test
    void anUnknownCommandTellsNothingElse() {
        CommandStatus status = CommandStatus.unknown("command-1");

        assertEquals("command-1", status.commandId());
        assertEquals(CommandStatus.State.UNKNOWN, status.state());
        assertNull(status.errorCode());
        assertNull(status.originalCommandId());
        assertNull(status.at());
    }

    @Test
    void aStatusNamesItsCommandAndState() {
        assertThrows(IllegalArgumentException.class, () -> new CommandStatus(" ", CommandStatus.State.QUEUED, null,
            null, null));
        assertThrows(IllegalArgumentException.class, () -> new CommandStatus("command-1", null, null, null, null));
    }

    @Test
    void aClientThatIsNotTheRuntimesCannotReadAStatus() {
        // CAP-019: the method came as a default, so clients written before it still compile.
        CommandClient<String> client = (command, idempotencyKey, partitionKey) ->
            new CommandSubmission("command-1", CompletableFuture.completedFuture(null));

        assertThrows(UnsupportedOperationException.class, () -> client.status("command-1"));
    }
}
