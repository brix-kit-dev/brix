/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.identity.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.runtime.sdk.capability.IdentityAccountCapability.PasswordRejection;

/** identity-auth BRX-IA-CRED-001 for new passwords of staff, who always use a second factor. */
class PasswordPolicyTest {

    private static final String EMAIL = "kawasaki.clinic@example.test";
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsAPassphraseOfEightOrMoreCharactersWithoutCompositionRules() {
        assertEquals(Optional.empty(), policy.check("violet kettle harbour", EMAIL, EMAIL));
        assertEquals(Optional.empty(), policy.check("lowercaseonlypassphrase", EMAIL, EMAIL));
    }

    @Test
    void countsCharactersAsCodePoints() {
        assertEquals(Optional.of(PasswordRejection.TOO_SHORT), policy.check("🌸🌸🌸🌸", EMAIL, EMAIL),
                "four characters, although eight UTF-16 units");
        assertEquals(Optional.empty(), policy.check("桜の花が咲きました", EMAIL, EMAIL));
        assertEquals(Optional.of(PasswordRejection.TOO_SHORT), policy.check("7 chars", EMAIL, EMAIL));
        assertEquals(Optional.of(PasswordRejection.TOO_SHORT), policy.check(null, EMAIL, EMAIL));
        assertEquals(Optional.of(PasswordRejection.TOO_LONG), policy.check("x".repeat(129), EMAIL, EMAIL));
    }

    @Test
    void rejectsBreachedPasswordsWhateverTheirCase() {
        for (String breached : new String[] {"password1", "PassWord1", "qwertyuiop", "1q2w3e4r5t", "iloveyou"}) {
            assertEquals(Optional.of(PasswordRejection.BREACHED), policy.check(breached, EMAIL, EMAIL), breached);
        }
    }

    @Test
    void rejectsRepeatedCharactersAndStraightRuns() {
        for (String trivial : new String[] {"zzzzzzzzzz", "!!!!!!!!", "abcdefghijk", "98765432", "4567890"}) {
            Optional<PasswordRejection> result = policy.check(trivial, EMAIL, EMAIL);
            assertTrue(result.isPresent(), trivial);
        }
        assertTrue(PasswordPolicy.isTrivial("mnopqrst"));
        assertTrue(!PasswordPolicy.isTrivial("89:;<=>?"), "a run is digits or letters only");
    }

    @Test
    void rejectsTheAccountNameAndTheServiceName() {
        assertEquals(Optional.of(PasswordRejection.CONTAINS_ACCOUNT_NAME),
                policy.check("Kawasaki.Clinic-2026", EMAIL, EMAIL));
        assertEquals(Optional.of(PasswordRejection.CONTAINS_ACCOUNT_NAME),
                policy.check("my brix console", EMAIL, EMAIL));
        assertEquals(Optional.empty(), policy.check("violet kettle harbour", "abc@example.test", "abc"),
                "names shorter than four characters do not count");
    }

    @Test
    void theSecurityProfileCannotLowerTheMinimumBelowEight() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordPolicy(7, 128));
        assertThrows(IllegalArgumentException.class, () -> new PasswordPolicy(8, 32));
        assertEquals(Optional.of(PasswordRejection.TOO_SHORT),
                new PasswordPolicy(12, 128).check("violet tea!", EMAIL, EMAIL));
    }
}
