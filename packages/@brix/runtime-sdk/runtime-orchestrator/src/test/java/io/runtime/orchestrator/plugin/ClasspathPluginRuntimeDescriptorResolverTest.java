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
package io.runtime.orchestrator.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ClasspathPluginRuntimeDescriptorResolverTest {

    @Test
    void resolvesSameArtifactPluginManifest() {
        ClasspathPluginRuntimeDescriptorResolver resolver =
            new ClasspathPluginRuntimeDescriptorResolver(Thread.currentThread().getContextClassLoader());

        Optional<PluginRuntimeDescriptor> descriptor = resolver.resolve(new ServiceLoadedTestPlugin());

        assertTrue(descriptor.isPresent());
        assertEquals("service-loaded-test", descriptor.get().identity().pluginId());
        assertTrue(descriptor.get().requiredCapabilities().contains("TestCapability"));
        assertTrue(descriptor.get().optionalCapabilities().contains("OptionalCapability"));
    }
}
