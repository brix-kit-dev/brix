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

import java.util.Objects;

/**
 * Runtime-verified plugin identity.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class PluginIdentity {

    private final String pluginId;

    /**
     * Creates a plugin identity.
     *
     * @param pluginId globally unique plugin id from the YAML manifest
     */
    public PluginIdentity(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId must not be blank");
        }
        this.pluginId = pluginId;
    }

    /**
     * Returns the plugin id.
     *
     * @return plugin id
     */
    public String pluginId() {
        return pluginId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PluginIdentity)) {
            return false;
        }
        PluginIdentity that = (PluginIdentity) o;
        return pluginId.equals(that.pluginId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pluginId);
    }

    @Override
    public String toString() {
        return "PluginIdentity{pluginId='" + pluginId + "'}";
    }
}
