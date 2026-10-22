/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.runtime.orchestrator.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class UIManifestLoaderTest {

    @Test
    void loadsActiveYamlManifestFromClasspath() {
        UIManifestLoader loader = new UIManifestLoader(new ObjectMapper());

        loader.loadManifests();

        assertNotNull(loader.getManifest("service-loaded-test"));
    }

    @Test
    void verifiesObjectBasedRequiredCapabilities() {
        UIManifestLoader loader = new UIManifestLoader(new ObjectMapper());
        loader.loadManifests();

        assertThrows(MissingRequiredCapabilityException.class,
            () -> loader.verifyRequiredCapabilities(capability -> false));
    }

    @Test
    void ownerAwareVerificationSeesTheRequestingPluginOfEachRequiredCapability() {
        UIManifestLoader loader = new UIManifestLoader(new ObjectMapper());
        loader.loadManifests();
        List<String> checked = new ArrayList<>();

        loader.verifyRequiredCapabilities((pluginId, capability) -> checked.add(pluginId + ":" + capability));

        assertTrue(checked.contains("service-loaded-test:TestCapability"));
        assertFalse(checked.contains("service-loaded-test:OptionalCapability"));
    }

    @Test
    void ownerAwareVerificationNamesThePluginWhoseRequirementIsUnmet() {
        UIManifestLoader loader = new UIManifestLoader(new ObjectMapper());
        loader.loadManifests();

        MissingRequiredCapabilityException failure = assertThrows(MissingRequiredCapabilityException.class,
            () -> loader.verifyRequiredCapabilities(
                (pluginId, capability) -> !"service-loaded-test".equals(pluginId)));

        assertEquals("service-loaded-test", failure.getPluginId());
    }
}
