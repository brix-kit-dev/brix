/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OperationalModuleDescriptorTest {

    @Test
    void bundledSchemaMatchesDevtoolsAuthoringSchema() throws Exception {
        assertArrayEquals(
            Files.readAllBytes(Path.of("../../platform-devtools/schemas/platform-operational.schema.json")),
            Files.readAllBytes(Path.of(
                "src/main/resources/META-INF/schemas/platform-operational.schema.json")));
    }

    @Test
    void strictLoaderAcceptsTheServiceLoaderFixture() {
        OperationalModuleDescriptor descriptor = new OperationalModuleDescriptorLoader().load(
            getClass().getClassLoader().getResource(
                OperationalModuleDescriptorLoader.DESCRIPTOR_RESOURCE));

        assertEquals("runtime-test-operational", descriptor.identity().moduleId());
        assertEquals(1, descriptor.endpoints().size());
    }

    @Test
    void descriptorRejectsUnapprovedInternalContractPrivilege() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalModuleDescriptor(
                new OperationalModuleIdentity("runtime-test", "3.2.0", "runtime"),
                ">=3.2.0 <4.0.0",
                java.util.List.of(),
                java.util.List.of(new OperationalModuleDescriptor.RequiredInternalContract(
                    "test.contract",
                    Runnable.class.getName(),
                    ">=1.0.0 <2.0.0",
                    true,
                    "contract.test")),
                java.util.Set.of(),
                java.util.Map.of(),
                java.util.Map.of()));
    }
}
