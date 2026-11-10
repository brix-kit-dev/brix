/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.runtime.sdk.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The role check is deprecated for removal under both names of the auth capability (root BRX-RS-CAP-022; standard-link
 * plan queue item 5, D119): code compiled against it is warned until 4.0.0 removes it.
 */
class AuthCapabilityContractTest {

    @Test
    @SuppressWarnings("removal")
    void hasRoleIsDeprecatedForRemoval() throws NoSuchMethodException {
        for (Class<?> type : List.of(AuthCapability.class, AuthContextCapability.class)) {
            Deprecated deprecated = type.getDeclaredMethod("hasRole", String.class).getAnnotation(Deprecated.class);

            assertNotNull(deprecated, type.getName() + ".hasRole is deprecated");
            assertTrue(deprecated.forRemoval(), type.getName() + ".hasRole is deprecated for removal");
            assertEquals("3.3.0", deprecated.since(), type.getName() + ".hasRole is deprecated since 3.3.0");
        }
    }
}
