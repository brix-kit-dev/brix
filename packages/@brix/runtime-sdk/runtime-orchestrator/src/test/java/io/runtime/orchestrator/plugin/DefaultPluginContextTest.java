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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;

class DefaultPluginContextTest {

    @Test
    void returnsOnlyManifestDeclaredRequiredCapability() {
        TestCapability capability = new TestCapability() {
        };
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        registry.register(TestCapability.class, capability);
        DefaultPluginContext context = new DefaultPluginContext(
            PluginRuntimeDescriptor.builder("plugin-a")
                .requiredCapabilities(List.of(TestCapability.class.getSimpleName()))
                .build(),
            registry);

        assertSame(capability, context.require(TestCapability.class));
        assertThrows(PluginRuntimeException.class, () -> context.find(TestCapability.class));
    }

    @Test
    void optionalCapabilityCanBeAbsentWhenDeclared() {
        DefaultPluginContext context = new DefaultPluginContext(
            PluginRuntimeDescriptor.builder("plugin-a")
                .optionalCapabilities(List.of(TestCapability.class.getName()))
                .build(),
            new DefaultCapabilityRegistry());

        assertTrue(context.find(TestCapability.class).isEmpty());
    }

    @Test
    void rejectsUndeclaredCapabilityAccess() {
        DefaultPluginContext context = new DefaultPluginContext(
            PluginRuntimeDescriptor.builder("plugin-a").build(),
            new DefaultCapabilityRegistry());

        assertThrows(PluginRuntimeException.class, () -> context.require(TestCapability.class));
        assertThrows(PluginRuntimeException.class, () -> context.find(TestCapability.class));
    }

    interface TestCapability {
    }
}
