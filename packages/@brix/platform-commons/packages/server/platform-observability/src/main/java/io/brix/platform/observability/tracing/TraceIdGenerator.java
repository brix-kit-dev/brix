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
package io.brix.platform.observability.tracing;

import java.util.UUID;

/**
 * TraceId generator.
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
public class TraceIdGenerator {

    /**
     * Generates a new TraceId.
     * <p>
     * Uses UUID v4 format, removing hyphens to reduce transmission overhead.
     * </p>
     *
     * @return 32-character hexadecimal string
     */
    public String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
