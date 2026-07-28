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
package io.runtime.sdk.internalcontract;

/**
 * Immutable identity of a Runtime descriptor owner.
 *
 * @param moduleId descriptor module identifier
 * @param moduleKind descriptor module kind
 * @param moduleVersion descriptor module version
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record RuntimeModuleIdentity(String moduleId, String moduleKind, String moduleVersion) {

    /**
     * Validates and normalizes the descriptor-owned identity.
     */
    public RuntimeModuleIdentity {
        moduleId = requireText(moduleId, "moduleId");
        moduleKind = requireText(moduleKind, "moduleKind");
        moduleVersion = requireText(moduleVersion, "moduleVersion");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
