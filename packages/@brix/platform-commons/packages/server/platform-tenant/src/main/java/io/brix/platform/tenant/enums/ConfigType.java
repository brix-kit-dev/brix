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
 * Configuration Value Type Enumeration.
 *
 * <p>Defines the data type of a tenant configuration value stored in
 * {@code sys_tenant_config}. Used for UI rendering and validation hints.
 *
 * <h3>Database Storage</h3>
 * <p>Stored as VARCHAR(20) in sys_tenant_config.config_type column.
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @Frozen - DO NOT modify enum values without database migration
 */
public enum ConfigType {

    STRING("String", "Plain text value"),
    NUMBER("Number", "Numeric value (integer or decimal)"),
    BOOLEAN("Boolean", "True/false toggle"),
    JSON("JSON", "Complex structured JSON object"),
    ENUM("Enum", "Enumerated value from a predefined set");

    private final String displayName;
    private final String description;

    ConfigType(String displayName, String description) {
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
