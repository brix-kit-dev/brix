/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.api;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.runtime.contract.generator.ContractSources;

/**
 * Query design D38 (root BRX-RS-A-22): the contract types of platform-tenant's queries are what its schemas generate.
 * Regenerate them with {@code -Dbrix.contracts.write=true} on this test.
 */
class ContractSourcesTest {

    @Test
    void theContractTypesAreGeneratedFromTheSchemas() {
        ContractSources.verify(Path.of(""), "io.brix.platform.tenant.api");
    }
}
