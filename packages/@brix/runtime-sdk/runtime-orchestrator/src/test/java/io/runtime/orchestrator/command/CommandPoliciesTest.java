/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The platform's command policies (root BRX-RS-MANIFEST-025; standard-link plan L4.3, D54).
 */
class CommandPoliciesTest {

    @Test
    void accessIsDeclaredConsumersOrOnBehalfOfAPermission() {
        assertEquals(CommandPolicies.Access.Kind.DECLARED_CONSUMERS,
            CommandPolicies.access("declared-consumers").kind());
        CommandPolicies.Access onBehalf = CommandPolicies.access("on-behalf-of:orders:cancel");
        assertEquals(CommandPolicies.Access.Kind.ON_BEHALF_OF, onBehalf.kind());
        assertEquals("orders:cancel", onBehalf.permission());
    }

    @Test
    void anUnknownOrEmptyAccessPolicyIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> CommandPolicies.access("anyone"));
        assertThrows(IllegalArgumentException.class, () -> CommandPolicies.access("on-behalf-of:"));
        assertThrows(IllegalArgumentException.class, () -> CommandPolicies.access(""));
    }

    @Test
    void theOwnersKeyScopeSeparatesSendersAndTenants() {
        assertEquals("app-orders|42|order", CommandPolicies.ownerScope("app-orders", 42L, "order"));
        assertEquals("app-orders|platform|order", CommandPolicies.ownerScope("app-orders", null, "order"));
        assertNotEquals(CommandPolicies.ownerScope("app-orders", 42L, "order"),
            CommandPolicies.ownerScope("app-billing", 42L, "order"));
    }

    @Test
    void aScopeTooLongForItsColumnIsHashed() {
        String scope = CommandPolicies.ownerScope("app-orders", 42L, "x".repeat(200));

        assertTrue(scope.startsWith("sha256:"), scope);
        assertTrue(scope.length() <= 128, scope);
        assertEquals(scope, CommandPolicies.ownerScope("app-orders", 42L, "x".repeat(200)));
        assertNotEquals(scope, CommandPolicies.ownerScope("app-orders", 43L, "x".repeat(200)));
    }
}
