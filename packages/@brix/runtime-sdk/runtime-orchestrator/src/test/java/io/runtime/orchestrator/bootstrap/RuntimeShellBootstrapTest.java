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
package io.runtime.orchestrator.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.orchestrator.plugin.PluginRuntimeException;

class RuntimeShellBootstrapTest {

    @Test
    void staticStartClosesApplicationContextWhenBootstrapFails() {
        PluginRuntimeManager manager = new PluginRuntimeManager(
            List::of,
            plugin -> Optional.empty(),
            new DefaultCapabilityRegistry(),
            List.of("required-plugin"));
        RuntimeShellBootstrap bootstrap = new RuntimeShellBootstrap(manager);
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(RuntimeShellBootstrap.class, () -> bootstrap);
        context.refresh();

        assertThrows(PluginRuntimeException.class, () -> RuntimeShellBootstrap.start(context));
        assertFalse(context.isActive());
    }
}
