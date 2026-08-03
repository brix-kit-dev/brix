/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.devtools.governance.artifact;

import java.util.Arrays;

/**
 * Runtime Shell module kinds used by the Phase 1 artifact inventory.
 */
public enum ModuleKind {
    PLUGIN_API("plugin-api"),
    PLUGIN_CORE("plugin-core"),
    PLUGIN_SERVER("plugin-server"),
    SHARED_CONTRACT("shared-contract"),
    UI_WEB("ui-web"),
    UI_MOBILE("ui-mobile"),
    PLATFORM_OPERATIONAL("platform-operational"),
    PLATFORM_CAPABILITY("platform-capability"),
    RUNTIME_CAPABILITY("runtime-capability"),
    ADAPTER("adapter"),
    HOST("host");

    private final String wireName;

    ModuleKind(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public boolean isPlugin() {
        return this == PLUGIN_API || this == PLUGIN_CORE || this == PLUGIN_SERVER;
    }

    public boolean isUi() {
        return this == UI_WEB || this == UI_MOBILE;
    }

    public boolean isJavaStaticBoundaryTarget() {
        return this == PLUGIN_API
            || this == PLUGIN_CORE
            || this == PLUGIN_SERVER
            || this == PLATFORM_OPERATIONAL
            || this == PLATFORM_CAPABILITY
            || this == RUNTIME_CAPABILITY
            || this == ADAPTER
            || this == HOST;
    }

    public static ModuleKind fromWireName(String value) {
        return Arrays.stream(values())
            .filter(kind -> kind.wireName.equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported moduleKind: " + value));
    }
}
