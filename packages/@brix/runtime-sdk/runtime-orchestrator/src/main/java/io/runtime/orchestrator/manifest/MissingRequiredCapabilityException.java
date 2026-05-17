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
package io.runtime.orchestrator.manifest;

import java.util.Collections;
import java.util.List;

/**
 * Missing Required Capability Exception.
 *
 * <p>Thrown at startup when a plugin's {@code plugin-manifest.json} declares
 * {@code capabilities.required} entries that are not provided by the Runtime Shell
 * (Host). The Runtime Shell <strong>refuses to start</strong> in this case, per
 * Architecture Red-Line P0-2 fail-fast contract.</p>
 *
 * <h3>Why a Distinct Exception</h3>
 * <p>The runtime has two parallel manifest channels:</p>
 * <ul>
 *   <li><strong>YAML</strong> ({@code module-manifest.yaml}, see
 *       {@link io.runtime.orchestrator.lifecycle.CapabilityMissingException}) —
 *       enforced by {@code DefaultModuleLifecycleManager.validateCapabilities}
 *       during per-module {@code initialize()}.</li>
 *   <li><strong>JSON</strong> ({@code META-INF/plugin-manifest.json}, this exception) —
 *       enforced by {@code UIManifestLoader} immediately after classpath scanning,
 *       <em>before</em> any module lifecycle starts. This protects plugins that ship
 *       only a JSON manifest (the public, recommended channel since v3.2.0).</li>
 * </ul>
 *
 * <h3>Resolution Suggestions</h3>
 * <ul>
 *   <li>Add the missing {@code Capability} bean to the Host's Spring context, or</li>
 *   <li>Move the capability from {@code capabilities.required} to
 *       {@code capabilities.optional} in the plugin's manifest — only do this if
 *       the plugin truly degrades gracefully without it.</li>
 * </ul>
 *
 * @author Runtime SDK Team
 * @since 3.2.0
 */
public class MissingRequiredCapabilityException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String pluginId;
    private final List<String> missingCapabilities;

    /**
     * Creates a new exception.
     *
     * @param pluginId            plugin identifier from {@code pluginId} field
     * @param missingCapabilities immutable defensive copy of missing capability names
     */
    public MissingRequiredCapabilityException(String pluginId, List<String> missingCapabilities) {
        super(buildMessage(pluginId, missingCapabilities));
        this.pluginId = pluginId;
        this.missingCapabilities = missingCapabilities == null
            ? Collections.emptyList()
            : List.copyOf(missingCapabilities);
    }

    /**
     * @return the offending plugin's {@code pluginId}
     */
    public String getPluginId() {
        return pluginId;
    }

    /**
     * @return immutable list of missing required capability names (never {@code null})
     */
    public List<String> getMissingCapabilities() {
        return missingCapabilities;
    }

    private static String buildMessage(String pluginId, List<String> missing) {
        return "Plugin '" + pluginId
            + "' declares required capabilities that are not provided by the Host: "
            + missing
            + ". Either add the matching Capability bean to the Host context, "
            + "or move the capability into capabilities.optional if the plugin "
            + "degrades gracefully without it.";
    }
}
