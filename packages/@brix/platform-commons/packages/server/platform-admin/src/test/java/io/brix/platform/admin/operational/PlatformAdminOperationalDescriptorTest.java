/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.operational.OperationalModuleDescriptorLoader;
import io.runtime.orchestrator.operational.PlatformOperationalModule;
import io.runtime.orchestrator.operational.ServiceLoaderOperationalModuleDiscovery;

class PlatformAdminOperationalDescriptorTest {

    @Test
    void descriptorDeclaresPlatformAdminAsTenantOperationalConsumer() {
        var descriptor = new OperationalModuleDescriptorLoader().load(
            getClass().getClassLoader().getResource(
                OperationalModuleDescriptorLoader.DESCRIPTOR_RESOURCE));

        assertEquals("platform-admin", descriptor.identity().moduleId());
        assertEquals("platform-commons", descriptor.identity().owner());
        assertEquals(">=3.0.10 <4.0.0", descriptor.runtimeRange());
        assertEquals(16, descriptor.endpoints().size());
        assertTrue(descriptor.endpoints().values().stream()
            .allMatch(endpoint -> endpoint.path().startsWith("/api/platform/")));

        assertEquals(4, descriptor.requiredContracts().size());
        var tenantContract = descriptor.requiredContracts().get(0);
        assertEquals("brix.internal.tenant.administration", tenantContract.contractId());
        assertEquals("io.brix.platform.tenant.internal.TenantAdministration", tenantContract.contractType());
        assertEquals("platform-admin.tenant-administration", tenantContract.privilegeAllowlistRef());
        assertTrue(descriptor.requiredContracts().stream()
            .anyMatch(contract -> "brix.internal.platform.bootstrap-administration".equals(contract.contractId())));
        assertTrue(descriptor.requiredContracts().stream()
            .anyMatch(contract -> "brix.internal.platform.identity-administration".equals(contract.contractId())));
        assertTrue(descriptor.requiredContracts().stream()
            .anyMatch(contract -> "brix.internal.platform.auth-flow".equals(contract.contractId())));
        assertTrue(descriptor.endpoints().containsKey("platform.auth.login.v1"));
        assertTrue(descriptor.endpoints().containsKey("platform.auth.login.totp.v1"));
        assertTrue(descriptor.endpoints().containsKey("platform.admins.list.v1"));
        assertTrue(descriptor.endpoints().containsKey("platform.tenants.list.v1"));
        assertTrue(descriptor.endpoints().containsKey("platform.license.quota.v1"));
    }

    @Test
    void serviceLoaderPublishesExactlyOneOperationalProvider() {
        List<PlatformOperationalModule> providers = ServiceLoader
            .load(PlatformOperationalModule.class, getClass().getClassLoader())
            .stream()
            .map(ServiceLoader.Provider::get)
            .toList();

        assertEquals(1, providers.size());
        assertInstanceOf(PlatformAdminOperationalModule.class, providers.get(0));
    }

    @Test
    void discoveryAssociatesProviderWithSameArtifactDescriptor() {
        var discovered = new ServiceLoaderOperationalModuleDiscovery(
            getClass().getClassLoader(),
            new OperationalModuleDescriptorLoader()).discover();

        assertEquals(1, discovered.size());
        assertEquals("platform-admin", discovered.get(0).descriptor().identity().moduleId());
        assertInstanceOf(PlatformAdminOperationalModule.class, discovered.get(0).provider());
    }
}
