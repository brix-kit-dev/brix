/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The runtime keeps no answer with a secret or a credential (root BRX-RS-SECURITY-008; idempotency design §4.6, D74).
 */
class ReplayableAnswersTest {

    private static final ClassLoader CLASSES = ReplayableAnswersTest.class.getClassLoader();

    @Test
    void anAnswerWithoutSuchAFieldIsKept() {
        assertEquals(Optional.empty(),
            ReplayableAnswers.fieldNotKept("brix://schemas/test-endpoints/thing/1.0.0", CLASSES));
    }

    @Test
    void aCredentialFieldIsFound() {
        assertEquals(Optional.of("brix://schemas/test-endpoints/session/1.0.0/properties/token"),
            ReplayableAnswers.fieldNotKept("brix://schemas/test-endpoints/session/1.0.0", CLASSES));
    }

    @Test
    void aFieldInAReferredSchemaIsFound() {
        assertEquals(Optional.of("brix://schemas/test-endpoints/session/1.0.0/properties/token"),
            ReplayableAnswers.fieldNotKept("brix://schemas/test-endpoints/session-envelope/1.0.0", CLASSES));
    }

    @Test
    void aSchemaThatIsNotOnTheClassPathHidesNothing() {
        assertEquals(Optional.empty(),
            ReplayableAnswers.fieldNotKept("brix://schemas/test-endpoints/absent/1.0.0", CLASSES));
    }
}
