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
package io.brix.platform.admin.dto;

/**
 * Stable error response for platform administrator lifecycle endpoints.
 *
 * @param code machine-readable error code
 * @param message safe client-facing message
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record PlatformAdminErrorResponse(String code, String message) {
}