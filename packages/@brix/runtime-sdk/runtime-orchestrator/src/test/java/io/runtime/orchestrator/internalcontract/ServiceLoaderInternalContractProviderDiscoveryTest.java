/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.internalcontract;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;

import org.junit.jupiter.api.Test;

class ServiceLoaderInternalContractProviderDiscoveryTest {

    @Test
    void returnsEmptyCandidateSetWhenArtifactPublishesNoProvider() {
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[0],
                ServiceLoaderInternalContractProviderDiscoveryTest.class.getClassLoader())) {
            assertTrue(new ServiceLoaderInternalContractProviderDiscovery(classLoader).discover().isEmpty());
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }
}
