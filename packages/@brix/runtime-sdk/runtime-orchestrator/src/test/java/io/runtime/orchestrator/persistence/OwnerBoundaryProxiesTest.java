/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

class OwnerBoundaryProxiesTest {

    @Test
    void everyContractCallRunsInsideTheOwnerUnitOfWork() {
        RecordingPersistence persistence = new RecordingPersistence();
        Greeting target = name -> {
            assertTrue(persistence.active(), "owner code must run inside its unit of work");
            return "hello " + name;
        };

        Greeting boundary = OwnerBoundaryProxies.wrap(Greeting.class, target, persistence.unitOfWork());

        assertEquals("hello brix", boundary.greet("brix"));
        assertEquals(List.of("required"), persistence.modes());
    }

    @Test
    void runtimeFailuresReachTheCallerUnchanged() {
        RecordingPersistence persistence = new RecordingPersistence();
        IllegalStateException failure = new IllegalStateException("rejected");
        Greeting boundary = OwnerBoundaryProxies.wrap(Greeting.class, name -> {
            throw failure;
        }, persistence.unitOfWork());

        assertSame(failure, assertThrows(IllegalStateException.class, () -> boundary.greet("brix")));
    }

    @Test
    void checkedFailuresReachTheCallerUnchanged() {
        RecordingPersistence persistence = new RecordingPersistence();
        IOException failure = new IOException("disk");
        Loader boundary = OwnerBoundaryProxies.wrap(Loader.class, () -> {
            throw failure;
        }, persistence.unitOfWork());

        assertSame(failure, assertThrows(IOException.class, boundary::load));
        assertEquals(List.of("required"), persistence.modes());
    }

    @Test
    void objectMethodsDoNotOpenAUnitOfWork() {
        RecordingPersistence persistence = new RecordingPersistence();
        Greeting target = name -> name;
        Greeting boundary = OwnerBoundaryProxies.wrap(Greeting.class, target, persistence.unitOfWork());

        assertEquals(boundary, boundary);
        assertNotEquals(boundary, target);
        assertEquals(System.identityHashCode(boundary), boundary.hashCode());
        assertTrue(boundary.toString().startsWith("OwnerBoundary["));
        assertTrue(persistence.modes().isEmpty());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void onlyInterfacesImplementedByTheTargetCanBeWrapped() {
        RecordingPersistence persistence = new RecordingPersistence();
        Class rawGreeting = Greeting.class;

        assertThrows(IllegalArgumentException.class,
            () -> OwnerBoundaryProxies.wrap(Object.class, new Object(), persistence.unitOfWork()));
        assertThrows(IllegalArgumentException.class,
            () -> OwnerBoundaryProxies.wrap(rawGreeting, new Object(), persistence.unitOfWork()));
    }

    interface Greeting {
        String greet(String name);
    }

    interface Loader {
        String load() throws IOException;
    }
}
