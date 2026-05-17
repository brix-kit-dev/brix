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
package io.brix.platform.tenant.enums;

/**
 * Theme Mode Enumeration.
 *
 * <p>Defines the theme preference for tenant or user settings.
 *
 * <h3>Database Storage</h3>
 * <p>Stored as VARCHAR(10) in sys_tenant.default_theme column.
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
public enum ThemeMode {

    LIGHT("Light", "Light theme"),
    DARK("Dark", "Dark theme"),
    SYSTEM("System", "Follow system preference");

    private final String displayName;
    private final String description;

    ThemeMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
