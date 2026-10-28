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

import java.util.List;

/**
 * A platform audit record as super-admin §5 defines it. Every field is typed; there is no free text.
 *
 * @param action audit action id from {@link io.brix.platform.audit.api.PlatformAuditActions}
 * @param actorIdentityId authenticated actor, or {@code null} for an anonymous bootstrap request
 * @param executionIdentity identity the operation ran as, for example {@code platform-admin}
 * @param targetType type of the affected resource
 * @param targetId id of the affected resource, or {@code null}
 * @param affectedTenants tenants the operation touched; empty when none
 * @param success whether the operation succeeded
 * @param reasonCode stable reason, or {@code null}
 * @param errorCode stable error code of a failed operation, or {@code null}
 * @param traceId trace or correlation id of the invocation (required)
 * @param sourceIp client address; the owner keeps only its masked prefix
 * @param userAgent client user agent; the owner truncates it
 * @since 3.2.0
 */
public record RecordPlatformAuditCommand(
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
        String userAgent) {

    public RecordPlatformAuditCommand {
        affectedTenants = affectedTenants == null ? List.of() : List.copyOf(affectedTenants);
    }
}
