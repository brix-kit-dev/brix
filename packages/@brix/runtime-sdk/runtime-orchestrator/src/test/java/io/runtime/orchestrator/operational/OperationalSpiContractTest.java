/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class OperationalSpiContractTest {

    @Test
    void operationalSpiHasFrozenMethodSets() {
        assertEquals(Set.of("create"), methods(OperationalHandlerFactory.class));
        assertEquals(
            Set.of("bindEndpointHandlerFactory", "bindTaskFactory"),
            methods(OperationalBootstrapContext.class));
        assertEquals(
            Set.of("moduleIdentity", "runtimeView", "requireInternalContract"),
            methods(OperationalContext.class));
        assertEquals(
            Set.of("configure", "onStart", "onStop", "health"),
            methods(PlatformOperationalModule.class));
    }

    @Test
    void operationalContextDoesNotExposeContainerOrRegistry() {
        Set<String> returnTypes = Arrays.stream(OperationalContext.class.getMethods())
            .map(Method::getReturnType)
            .map(Class::getName)
            .collect(Collectors.toSet());

        assertFalse(returnTypes.stream().anyMatch(name -> name.contains("ApplicationContext")));
        assertFalse(returnTypes.stream().anyMatch(name -> name.contains("BeanFactory")));
        assertFalse(returnTypes.stream().anyMatch(name -> name.contains("CapabilityRegistry")));
    }

    private static Set<String> methods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).map(Method::getName).collect(Collectors.toSet());
    }
}
