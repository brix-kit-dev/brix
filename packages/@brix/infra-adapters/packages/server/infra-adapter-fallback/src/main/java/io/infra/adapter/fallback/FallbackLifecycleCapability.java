/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.fallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.HealthStatus;
import io.runtime.sdk.capability.LifecycleCapability;
import io.runtime.sdk.capability.ModuleMetadata;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;
import io.runtime.sdk.context.RuntimeContext;

/**
 * Fallback Lifecycle Capability Implementation.
 * 
 * <p>No-op implementation that only logs lifecycle events.</p>
 * 
 * @author Brix Team
 * @version 3.0.0
 */
@Capability(
    type = LifecycleCapability.class,
    name = "fallback-lifecycle",
    description = "No-op fallback lifecycle implementation",
    level = CapabilityLevel.EXPERIMENTAL,
    aliases = {"fallbackLifecycle"}
)
public class FallbackLifecycleCapability implements LifecycleCapability {

    private static final Logger log = LoggerFactory.getLogger(FallbackLifecycleCapability.class);

    @Override
    public void onInit(RuntimeContext context) {
        log.debug("[Fallback] Module initialized");
    }

    @Override
    public void onStart() {
        log.debug("[Fallback] Module started");
    }

    @Override
    public void onStop() {
        log.debug("[Fallback] Module stopped");
    }

    @Override
    public void onDestroy() {
        log.debug("[Fallback] Module destroyed");
    }

    @Override
    public HealthStatus healthCheck() {
        return HealthStatus.UP;
    }

    @Override
    public ModuleMetadata getMetadata() {
        return ModuleMetadata.builder()
                .moduleId("fallback-module")
                .moduleName("Fallback Module")
                .version("3.0.0")
                .description("Fallback capability implementation for development")
                .build();
    }
}
