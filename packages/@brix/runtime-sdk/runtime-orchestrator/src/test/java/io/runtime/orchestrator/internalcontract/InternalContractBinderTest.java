/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.internalcontract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.operational.OperationalModuleDescriptor;
import io.runtime.orchestrator.operational.OperationalModuleIdentity;
import io.runtime.orchestrator.operational.OperationalRuntimeException;
import io.runtime.sdk.internalcontract.InternalContractProvider;

class InternalContractBinderTest {

    @Test
    void bindsAndResolvesOneDescriptorDeclaredProvider() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        InternalContractBinder binder =
            new InternalContractBinder(registry, registry, getClass().getClassLoader());
        binder.bind(providerDescriptor(), provider(), Set.of());
        binder.activateAndFreeze();

        Runnable contract = binder.require(requirement(">=1.0.0 <2.0.0"), Runnable.class);

        contract.run();
        assertEquals(
            "owner-module",
            registry.getInternalContract("test.contract", Runnable.class)
                .orElseThrow()
                .ownerIdentity()
                .moduleId());
    }

    @Test
    void missingBindingFailsBidirectionalValidation() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        InternalContractBinder binder =
            new InternalContractBinder(registry, registry, getClass().getClassLoader());

        OperationalRuntimeException failure = assertThrows(
            OperationalRuntimeException.class,
            () -> binder.bind(providerDescriptor(), bootstrap -> { }, Set.of()));

        assertEquals("internal_contract.binding_mismatch", failure.diagnosticCode());
    }

    @Test
    void versionMismatchFailsClosed() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        InternalContractBinder binder =
            new InternalContractBinder(registry, registry, getClass().getClassLoader());
        binder.bind(providerDescriptor(), provider(), Set.of());
        binder.activateAndFreeze();

        OperationalRuntimeException failure = assertThrows(
            OperationalRuntimeException.class,
            () -> binder.require(requirement(">=2.0.0 <3.0.0"), Runnable.class));

        assertEquals("internal_contract.version_mismatch", failure.diagnosticCode());
    }

    @Test
    void h3FreezeRejectsLateProviderMutation() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        InternalContractBinder binder =
            new InternalContractBinder(registry, registry, getClass().getClassLoader());
        binder.activateAndFreeze();

        assertThrows(
            IllegalStateException.class,
            () -> binder.bind(providerDescriptor(), provider(), Set.of()));
    }

    private InternalContractProvider provider() {
        return bootstrap -> bootstrap.bind("test.contract", Runnable.class, context -> () -> { });
    }

    private OperationalModuleDescriptor providerDescriptor() {
        return new OperationalModuleDescriptor(
            new OperationalModuleIdentity("owner-module", "3.2.0", "runtime-tests"),
            ">=3.2.0 <4.0.0",
            List.of(new OperationalModuleDescriptor.ProvidedInternalContract(
                "test.contract",
                Runnable.class.getName(),
                "1.1.0",
                "test.provider",
                "runtime-tests")),
            List.of(),
            Set.of(),
            Map.of(),
            Map.of());
    }

    private OperationalModuleDescriptor.RequiredInternalContract requirement(String range) {
        return new OperationalModuleDescriptor.RequiredInternalContract(
            "test.contract",
            Runnable.class.getName(),
            range,
            true,
            "contract.test");
    }
}
