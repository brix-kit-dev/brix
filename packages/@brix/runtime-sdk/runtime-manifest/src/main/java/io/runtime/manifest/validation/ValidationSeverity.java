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
package io.runtime.manifest.validation;

/**
 * Validation Severity Level.
 * 验证严重级别
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public enum ValidationSeverity {

    /**
     * Error - validation failed.
     * 错误 - 验证失败
     */
    ERROR("Error"),

    /**
     * Warning - does not affect validation pass, but recommended to fix.
     * 警告 - 不影响验证通过，但建议修复
     */
    WARNING("Warning"),

    /**
     * Info - for reference only.
     * 信息 - 仅供参考
     */
    INFO("Info");

    private final String description;

    ValidationSeverity(String description) {
        this.description = description;
    }

    /**
     * Gets level description.
     *
     * @return Level description
     */
    public String getDescription() {
        return description;
    }
}
