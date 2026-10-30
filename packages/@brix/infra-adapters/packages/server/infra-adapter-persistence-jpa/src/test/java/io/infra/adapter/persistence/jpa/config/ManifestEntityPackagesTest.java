/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.infra.adapter.persistence.jpa.fixture.OwnedNote;

/**
 * Kafka design §14 K18 (root BRX-RS-DATA-027): the entities of an Owner that declares PersistenceCapability are
 * found from its manifest, without an {@code @EntityScan} of its own.
 */
class ManifestEntityPackagesTest {

    @Test
    void theEntitiesPackagedWithAnOwnersManifestAreScanned() {
        // The test manifest belongs to the test classes, which hold the owner's fixture entities.
        Set<String> packages = ManifestEntityPackages.entityPackages(getClass().getClassLoader());

        assertTrue(packages.contains(OwnedNote.class.getPackageName()), packages.toString());
    }
}
