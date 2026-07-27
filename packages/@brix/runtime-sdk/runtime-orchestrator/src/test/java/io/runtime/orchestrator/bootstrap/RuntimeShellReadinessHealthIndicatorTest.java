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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;

class RuntimeShellReadinessHealthIndicatorTest {

    @Test
    void healthIsUpWhenRuntimeShellReady() {
        RuntimeShellBootstrap bootstrap = new RuntimeShellBootstrap(new PluginRuntimeManager(
            () -> List.of(),
            plugin -> Optional.empty(),
            new DefaultCapabilityRegistry(),
            List.of()));
        bootstrap.start();

        Health health = new RuntimeShellReadinessHealthIndicator(bootstrap).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(true, health.getDetails().get("runtimeShellReady"));
    }

    @Test
    void healthIsOutOfServiceWhenRequiredPluginIsNotReady() {
        RuntimeShellBootstrap bootstrap = new RuntimeShellBootstrap(new PluginRuntimeManager(
            () -> List.of(),
            plugin -> Optional.empty(),
            new DefaultCapabilityRegistry(),
            List.of("brix-app-tenant")));

        Health health = new RuntimeShellReadinessHealthIndicator(bootstrap).health();

        assertEquals(Status.OUT_OF_SERVICE, health.getStatus());
        assertEquals(false, health.getDetails().get("runtimeShellReady"));
    }
}
