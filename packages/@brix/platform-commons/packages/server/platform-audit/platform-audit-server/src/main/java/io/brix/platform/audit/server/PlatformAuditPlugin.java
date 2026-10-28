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
package io.brix.platform.audit.server;

import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;

/**
 * Runtime Shell plugin provider for the platform audit Data Owner (super-admin §1.3a). The owner
 * has no endpoints: the platform console reads audit records through platform-admin, which
 * consumes the owner's internal contract.
 *
 * @since 3.2.0
 */
public final class PlatformAuditPlugin implements BrixPlugin {

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
        // No endpoint, task or event bindings: the owner only provides an internal contract.
    }

    @Override
    public void onStart(PluginContext context) {
        // No managed resources.
    }

    @Override
    public void onStop() {
        // No managed resources.
    }

    @Override
    public BrixHealth health() {
        return BrixHealth.up();
    }
}
