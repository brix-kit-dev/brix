/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.auth.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.operational.OperationalModuleDescriptorLoader;
import io.runtime.orchestrator.operational.PlatformOperationalModule;
import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.internalcontract.InternalContractProvider;

class PlatformAuthFlowOperationalDescriptorTest {

    @Test
    void descriptorPublishesOnlyAuthFlowInternalContract() {
        var descriptor = new OperationalModuleDescriptorLoader().load(
            getClass().getClassLoader().getResource(
                OperationalModuleDescriptorLoader.DESCRIPTOR_RESOURCE));

        assertEquals("platform-auth-flow", descriptor.identity().moduleId());
        assertTrue(descriptor.endpoints().isEmpty());
        assertTrue(descriptor.requiredContracts().isEmpty());
        assertEquals(1, descriptor.providedContracts().size());
        var contract = descriptor.providedContracts().get(0);
        assertEquals("brix.internal.platform.auth-flow", contract.contractId());
        assertEquals(AuthFlowCapability.class.getName(), contract.contractType());
        assertEquals("1.0.0", contract.contractVersion());
        assertEquals("platform-commons", contract.owner());
    }

    @Test
    void serviceLoaderPublishesOperationalModuleAndInternalProvider() {
        var modules = ServiceLoader.load(PlatformOperationalModule.class, getClass().getClassLoader())
            .stream()
            .map(ServiceLoader.Provider::get)
            .toList();
        var providers = ServiceLoader.load(InternalContractProvider.class, getClass().getClassLoader())
            .stream()
            .map(ServiceLoader.Provider::get)
            .toList();

        assertEquals(1, modules.size());
        assertEquals(1, providers.size());
        assertInstanceOf(PlatformAuthFlowOperationalModule.class, modules.get(0));
        assertInstanceOf(PlatformAuthFlowInternalContractProvider.class, providers.get(0));
    }
}
