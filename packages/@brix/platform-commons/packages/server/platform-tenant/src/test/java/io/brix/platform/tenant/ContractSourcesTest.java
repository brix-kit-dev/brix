/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.runtime.contract.generator.ContractSources;

/**
 * Every schema version this module ships, the request and response schemas of its endpoints, is pinned to its content in {@code src/main/brix/contracts.api}
 * (standard-link plan queue item 3, D114): a published version never changes, a change is a new version.
 * {@code -Dbrix.contracts.write=true} writes the listing again.
 */
class ContractSourcesTest {

    @Test
    void everyShippedSchemaVersionIsPinned() {
        ContractSources.verify(Path.of(""), "io.brix.platform.tenant");
    }
}
