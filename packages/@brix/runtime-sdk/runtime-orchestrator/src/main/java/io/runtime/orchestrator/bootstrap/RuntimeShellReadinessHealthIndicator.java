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

import java.util.List;
import java.util.Map;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;

import io.runtime.orchestrator.plugin.PluginRuntimeState;
import io.runtime.orchestrator.operational.OperationalModuleRuntimeState;

/**
 * Actuator health indicator that maps Runtime Shell readiness to Host readiness.
 *
 * <p>The indicator is owned by L2B Runtime so Host applications can include it
 * in the actuator readiness group through configuration without implementing
 * plugin lifecycle or readiness rules in Host source.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class RuntimeShellReadinessHealthIndicator implements HealthIndicator {

    private final RuntimeShellBootstrap bootstrap;

    /**
     * Creates a readiness health indicator.
     *
     * @param bootstrap Runtime Shell bootstrap API
     */
    public RuntimeShellReadinessHealthIndicator(RuntimeShellBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    /**
     * Returns Host readiness contribution derived from Runtime Shell state.
     *
     * @return actuator health for Runtime Shell readiness
     */
    @Override
    public Health health() {
        List<Map<String, Object>> pluginStates = bootstrap.pluginStates().stream()
            .map(RuntimeShellReadinessHealthIndicator::pluginState)
            .toList();
        Health.Builder builder = bootstrap.ready()
            ? Health.up()
            : Health.status(Status.OUT_OF_SERVICE);
        return builder
            .withDetail("runtimeShellReady", bootstrap.ready())
            .withDetail("plugins", pluginStates)
            .withDetail(
                "operationalModules",
                bootstrap.operationalStates().stream()
                    .map(RuntimeShellReadinessHealthIndicator::operationalState)
                    .toList())
            .build();
    }

    private static Map<String, Object> pluginState(PluginRuntimeState state) {
        return Map.of(
            "pluginId", state.identity().pluginId(),
            "lifecycle", state.lifecycleState().name(),
            "ready", state.ready(),
            "health", state.health().status().name(),
            "detail", state.detail());
    }

    private static Map<String, Object> operationalState(OperationalModuleRuntimeState state) {
        return Map.of(
            "moduleId", state.identity().moduleId(),
            "lifecycle", state.lifecycleState().name(),
            "entriesPublished", state.entriesPublished(),
            "ready", state.ready(),
            "health", state.health().status().name(),
            "diagnosticCode", state.diagnosticCode());
    }
}
