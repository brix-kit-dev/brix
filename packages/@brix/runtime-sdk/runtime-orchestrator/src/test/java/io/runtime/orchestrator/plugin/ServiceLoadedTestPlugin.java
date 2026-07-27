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

import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;

/**
 * Public test provider discovered through Java ServiceLoader.
 */
public final class ServiceLoadedTestPlugin implements BrixPlugin {

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
    }

    @Override
    public void onStart(PluginContext context) {
    }

    @Override
    public void onStop() {
    }

    @Override
    public BrixHealth health() {
        return BrixHealth.up();
    }
}
