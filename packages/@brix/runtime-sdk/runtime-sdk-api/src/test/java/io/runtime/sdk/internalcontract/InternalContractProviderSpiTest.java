/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.runtime.sdk.internalcontract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class InternalContractProviderSpiTest {

    @Test
    void providerBindingSpiHasFrozenMinimalMethods() {
        assertEquals(Set.of("configure"), declaredMethods(InternalContractProvider.class));
        assertEquals(Set.of("bind"), declaredMethods(InternalContractProviderBootstrap.class));
        assertEquals(Set.of("create"), declaredMethods(InternalContractProviderFactory.class));
        assertEquals(
            Set.of("ownerIdentity", "requireOwnerCapability"),
            declaredMethods(InternalContractProviderContext.class));
    }

    @Test
    void providerBindingSpiDoesNotExposeConsumerOrRegistryLookup() {
        Set<String> methodNames = Arrays.stream(new Class<?>[] {
                InternalContractProvider.class,
                InternalContractProviderBootstrap.class,
                InternalContractProviderFactory.class,
                InternalContractProviderContext.class
            })
            .flatMap(type -> Arrays.stream(type.getMethods()))
            .map(Method::getName)
            .collect(Collectors.toSet());

        assertFalse(methodNames.contains("find"));
        assertFalse(methodNames.contains("getRegistry"));
        assertFalse(methodNames.contains("list"));
    }

    @Test
    void runtimeModuleIdentityRejectsBlankDescriptorFields() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new RuntimeModuleIdentity("owner", "plugin-server", " "));
    }

    private static Set<String> declaredMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).map(Method::getName).collect(Collectors.toSet());
    }
}
