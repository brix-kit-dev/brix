/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ServiceLoaderOperationalModuleDiscoveryTest {

    @Test
    void discoversOneProviderAndAssociatesItsSameArtifactDescriptor() {
        var discovered = new ServiceLoaderOperationalModuleDiscovery().discover();

        assertEquals(1, discovered.size());
        assertEquals("runtime-test-operational", discovered.get(0).descriptor().identity().moduleId());
        assertEquals(ServiceLoadedTestOperationalModule.class, discovered.get(0).provider().getClass());
    }
}
