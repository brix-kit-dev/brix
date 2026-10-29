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
package io.brix.platform.audit.server.event;

import java.util.List;
import java.util.Objects;

/**
 * Payload of platform-identity's {@code IdentityAuditFactRecorded} event, schema
 * {@code brix://schemas/platform-identity/identity-audit-fact/1.0.0} (P4 design §4.2, D14): the typed
 * fields of one platform audit record, nothing else. The schema reference is the contract between the
 * two owners; neither depends on the other's types.
 *
 * @param action audit action id
 * @param actorIdentityId authenticated actor, or {@code null} for an anonymous bootstrap request
 * @param executionIdentity identity the change ran as
 * @param targetType type of the affected resource
 * @param targetId id of the affected resource, or {@code null}
 * @param affectedTenants tenants the change touched; empty when none
 * @param success whether the change succeeded
 * @param reasonCode stable reason, or {@code null}
 * @param errorCode stable error code of a failed change, or {@code null}
 * @param traceId trace id of the invocation that made the change, or {@code null} when it had none
 * @param sourceIp masked network prefix of the client address, or {@code null}
 * @since 3.2.0
 */
public record IdentityAuditFactPayload(
        String action,
        Long actorIdentityId,
        String executionIdentity,
        String targetType,
        String targetId,
        List<Long> affectedTenants,
        Boolean success,
        String reasonCode,
        String errorCode,
        String traceId,
        String sourceIp) {

    public IdentityAuditFactPayload {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(executionIdentity, "executionIdentity must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(success, "success must not be null");
        affectedTenants = affectedTenants == null ? List.of() : List.copyOf(affectedTenants);
    }
}
