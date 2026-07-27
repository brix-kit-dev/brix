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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class DefaultPluginBootstrapContextTest {

    @Test
    void recordsDeclaredEndpointBinding() {
        DefaultPluginBootstrapContext context = new DefaultPluginBootstrapContext(
            PluginRuntimeDescriptor.builder("plugin-a")
                .endpoints(List.of("tenant.list"))
                .build());

        context.bindEndpoint("tenant.list", request -> "ok");

        assertEquals(1, context.endpoints().size());
    }

    @Test
    void rejectsUndeclaredAndDuplicateBindings() {
        DefaultPluginBootstrapContext context = new DefaultPluginBootstrapContext(
            PluginRuntimeDescriptor.builder("plugin-a")
                .endpoints(List.of("tenant.list"))
                .build());

        assertThrows(PluginRuntimeException.class, () -> context.bindEndpoint("tenant.detail", request -> "ok"));
        context.bindEndpoint("tenant.list", request -> "ok");
        assertThrows(PluginRuntimeException.class, () -> context.bindEndpoint("tenant.list", request -> "ok"));
    }
}
