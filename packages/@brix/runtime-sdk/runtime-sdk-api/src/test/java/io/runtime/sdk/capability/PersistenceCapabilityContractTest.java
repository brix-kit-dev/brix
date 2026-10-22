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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.runtime.sdk.error.BrixException;

/**
 * Freezes the L2A persistence ABI of root blueprint BRX-RS-DATA-043.
 */
class PersistenceCapabilityContractTest {

    @Test
    void persistenceAbiHasFrozenMinimalMethods() {
        assertEquals(Set.of("repository", "unitOfWork"), declaredMethods(PersistenceCapability.class));
        assertEquals(Set.of("required", "readOnly"), declaredMethods(UnitOfWork.class));
    }

    @Test
    void unitOfWorkMethodsTakeOnlyASupplier() throws NoSuchMethodException {
        assertEquals(Object.class, UnitOfWork.class.getMethod("required", Supplier.class).getReturnType());
        assertEquals(Object.class, UnitOfWork.class.getMethod("readOnly", Supplier.class).getReturnType());
    }

    @Test
    void persistenceAbiDoesNotReferenceFrameworkTypes() {
        Set<String> referencedTypes = Stream.of(PersistenceCapability.class, UnitOfWork.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .flatMap(method -> Stream.concat(
                        Stream.of(method.getGenericReturnType()),
                        Arrays.stream(method.getGenericParameterTypes())))
                .map(Type::getTypeName)
                .collect(Collectors.toSet());

        assertTrue(referencedTypes.stream().noneMatch(name -> name.startsWith("org.springframework")),
                () -> "Spring types leaked into the L2A persistence ABI: " + referencedTypes);
        assertTrue(referencedTypes.stream().noneMatch(name -> name.startsWith("jakarta.persistence")),
                () -> "ORM types leaked into the L2A persistence ABI: " + referencedTypes);
        assertTrue(referencedTypes.stream().noneMatch(name -> name.startsWith("org.hibernate")),
                () -> "Hibernate types leaked into the L2A persistence ABI: " + referencedTypes);
    }

    @Test
    void persistenceFailuresUseStableCapabilityScopedWireCodes() {
        for (PersistenceCapabilityException.Code code : PersistenceCapabilityException.Code.values()) {
            assertTrue(code.wireCode().matches("persistence\\.[a-z_]+"),
                    () -> "Unexpected wire code format: " + code.wireCode());
        }
        long distinct = Arrays.stream(PersistenceCapabilityException.Code.values())
                .map(PersistenceCapabilityException.Code::wireCode)
                .distinct()
                .count();
        assertEquals(PersistenceCapabilityException.Code.values().length, distinct);
    }

    @Test
    void persistenceFailureIsAStableBrixException() {
        PersistenceCapabilityException failure = new PersistenceCapabilityException(
                PersistenceCapabilityException.Code.OPTIMISTIC_LOCK_CONFLICT,
                Map.of("entity", "Order"));

        assertTrue(BrixException.class.isInstance(failure));
        assertSame(PersistenceCapabilityException.Code.OPTIMISTIC_LOCK_CONFLICT, failure.code());
        assertEquals("persistence.optimistic_lock_conflict", failure.getMessage());
        assertEquals(Map.of("entity", "Order"), failure.safeParameters());
    }

    @Test
    @SuppressWarnings("removal")
    void legacyDatabaseCapabilityIsDeprecatedForRemoval() {
        Deprecated capability = DatabaseCapability.class.getAnnotation(Deprecated.class);
        Deprecated dialect = DatabaseDialect.class.getAnnotation(Deprecated.class);

        assertTrue(capability != null && capability.forRemoval());
        assertTrue(dialect != null && dialect.forRemoval());
        assertFalse(declaredMethods(PersistenceCapability.class).contains("getDialect"));
    }

    private static Set<String> declaredMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).map(Method::getName).collect(Collectors.toSet());
    }
}
