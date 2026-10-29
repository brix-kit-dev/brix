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
package io.brix.platform.identity.event;

import java.util.Objects;

import io.runtime.sdk.event.IntegrationEvent;

/**
 * A platform audit fact of the identity owner (P4 design §4.2, D14). It is a PLATFORM event: it carries
 * no tenant, and the platform audit owner records it after delivery.
 *
 * @since 3.2.0
 */
public final class IdentityAuditFactRecordedEvent extends IntegrationEvent {

    /** Event type the manifest declares under {@code events.publishes}. */
    public static final String EVENT_TYPE = "IdentityAuditFactRecorded";

    /** Producer of the event. */
    public static final String SOURCE_MODULE = "platform-identity";

    private final IdentityAuditFact fact;

    public IdentityAuditFactRecordedEvent(IdentityAuditFact fact) {
        super(SOURCE_MODULE);
        this.fact = Objects.requireNonNull(fact, "fact must not be null");
    }

    /** Facts about the same resource keep their order (root BRX-RS-MESSAGE-027). */
    @Override
    public String getRoutingKey() {
        Object subject = fact.targetId() != null ? fact.targetId() : fact.actorIdentityId();
        return fact.targetType() + ":" + (subject == null ? "-" : subject);
    }

    @Override
    public String eventTypeId() {
        return EVENT_TYPE;
    }

    @Override
    public IdentityAuditFact payload() {
        return fact;
    }
}
