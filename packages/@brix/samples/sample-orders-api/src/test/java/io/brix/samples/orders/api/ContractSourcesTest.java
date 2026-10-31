/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.api;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.runtime.contract.generator.ContractSources;

/**
 * The checked-in command contract types are what the schemas generate (standard-link plan L4.1);
 * {@code -Dbrix.contracts.write=true} writes them again.
 */
class ContractSourcesTest {

    @Test
    void theContractTypesAreGeneratedFromTheSchemas() {
        ContractSources.verify(Path.of(""), "io.brix.samples.orders.api");
    }
}
