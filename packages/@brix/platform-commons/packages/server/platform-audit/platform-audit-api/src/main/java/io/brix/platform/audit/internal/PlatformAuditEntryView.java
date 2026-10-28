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
import java.util.List;

/**
 * A stored platform audit record. Records written before the owner existed leave the newer fields
 * ({@code executionIdentity}, {@code reasonCode}, {@code traceId}, {@code sourceIp}, {@code userAgent})
 * empty; they were never recorded and are not back-filled.
 *
 * @param id record id
 * @param action audit action id
 * @param actorIdentityId actor, or {@code null}
 * @param executionIdentity identity the operation ran as, or {@code null}
 * @param targetType type of the affected resource
 * @param targetId id of the affected resource, or {@code null}
 * @param affectedTenants tenants the operation touched
 * @param success whether the operation succeeded
 * @param reasonCode stable reason, or {@code null}
 * @param errorCode stable error code, or {@code null}
 * @param traceId trace or correlation id, or {@code null}
 * @param sourceIp masked client address, or {@code null}
 * @param userAgent truncated user agent, or {@code null}
 * @param writer registered writer of the row, see {@link io.brix.platform.audit.api.PlatformAuditWriters}
 * @param createdAt server time of the record
 * @since 3.2.0
 */
public record PlatformAuditEntryView(
        long id,
        String action,
        Long actorIdentityId,
        String executionIdentity,
        String targetType,
        String targetId,
        List<Long> affectedTenants,
        boolean success,
        String reasonCode,
        String errorCode,
        String traceId,
        String sourceIp,
        String userAgent,
        String writer,
        Instant createdAt) {

    public PlatformAuditEntryView {
        affectedTenants = affectedTenants == null ? List.of() : List.copyOf(affectedTenants);
    }
}
