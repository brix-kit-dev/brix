/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.identity.event;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.runtime.contract.generator.ContractSources;

/**
 * Standard-link plan L5.4 (root BRX-RS-CAP-019 to CAP-021): platform-identity's published event schemas stay within the
 * contract subset, keep their earlier minor versions, and are pinned to their content in
 * {@code src/main/brix/contracts.api}. The payload records stay hand-written; {@link IdentityEventSchemasTest} compares
 * them with the schemas. Rewrite the listing with {@code -Dbrix.contracts.write=true} on this test.
 */
class ContractSourcesTest {

    @Test
    void thePublishedContractsAreCheckedAndPinned() {
        ContractSources.verify(Path.of(""), "io.brix.platform.identity.event");
    }
}
