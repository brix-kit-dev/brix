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
package io.runtime.sdk.plugin;

/**
 * v3.0.10 plugin lifecycle states.
 *
 * <p>Readiness is intentionally not represented as a lifecycle state. Runtime
 * readiness is derived from lifecycle, required dependencies, durable command
 * acceptance, and health.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public enum PluginLifecycleState {

    /**
     * The plugin provider has been discovered.
     */
    DISCOVERED,

    /**
     * The plugin manifest and dependencies have been resolved.
     */
    RESOLVED,

    /**
     * Manifest-declared code bindings have been wired.
     */
    WIRED,

    /**
     * The plugin has started.
     */
    STARTED,

    /**
     * The plugin is draining inbound work.
     */
    DRAINING,

    /**
     * The plugin is stopped.
     */
    STOPPED,

    /**
     * The plugin failed and must be stopped by the Runtime Shell.
     */
    FAILED
}
