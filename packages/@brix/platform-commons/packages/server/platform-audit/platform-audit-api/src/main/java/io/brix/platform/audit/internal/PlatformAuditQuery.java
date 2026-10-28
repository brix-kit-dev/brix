/*
 * Copyright 2026 Brix Authors
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
package io.brix.platform.audit.internal;

import java.time.Instant;

/**
 * Restricted query of the platform console: one page, newest first, with optional filters.
 *
 * @param page zero-based page number
 * @param size page size, 1 to {@value #MAX_SIZE}
 * @param action only this action, or {@code null}
 * @param actorIdentityId only this actor, or {@code null}
 * @param from earliest creation time (inclusive), or {@code null}
 * @param to latest creation time (exclusive), or {@code null}
 * @param success only successful ({@code true}) or failed ({@code false}) records, or {@code null}; contract 1.1.0
 * @since 3.2.0
 */
public record PlatformAuditQuery(
        int page, int size, String action, Long actorIdentityId, Instant from, Instant to, Boolean success) {

    /** Largest page the owner returns. */
    public static final int MAX_SIZE = 200;

    public PlatformAuditQuery {
        if (page < 0) {
            throw new PlatformAuditRejectedException(PlatformAuditRejectedException.QUERY_INVALID, "page must not be negative");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new PlatformAuditRejectedException(
                PlatformAuditRejectedException.QUERY_INVALID, "size must be between 1 and " + MAX_SIZE);
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new PlatformAuditRejectedException(PlatformAuditRejectedException.QUERY_INVALID, "from must be before to");
        }
    }

    /**
     * A query over records of either result, as in contract 1.0.0.
     */
    public PlatformAuditQuery(int page, int size, String action, Long actorIdentityId, Instant from, Instant to) {
        this(page, size, action, actorIdentityId, from, to, null);
    }
}
