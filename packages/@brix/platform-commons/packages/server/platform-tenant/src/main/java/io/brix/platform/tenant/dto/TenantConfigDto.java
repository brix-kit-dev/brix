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
package io.brix.platform.tenant.dto;

import io.brix.platform.tenant.enums.ConfigType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating or updating a tenant config entry.
 *
 * <p>Used by {@code PUT /api/v1/tenant/config/{namespace}/{key}}.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons DTO</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
public class TenantConfigDto {

    @NotBlank(message = "Config value is required")
    private String value;

    @NotNull(message = "Config type is required")
    private ConfigType type;

    @Size(max = 500)
    private String description;

    private Boolean sensitive;

    // ========================================================================
    // Getters and Setters
    // ========================================================================

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public ConfigType getType() {
        return type;
    }

    public void setType(ConfigType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getSensitive() {
        return sensitive;
    }

    public void setSensitive(Boolean sensitive) {
        this.sensitive = sensitive;
    }
}
