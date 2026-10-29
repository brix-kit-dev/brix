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
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;

/**
 * The identity owner's fact that a FIRST_OWNER invitee completed setup (P4 design §4.3, D16), schema
 * {@code brix://schemas/platform-identity/first-owner-invitee-setup-completed/1.0.0}. It is a PLATFORM
 * event; platform-tenant resends the invitee's pending invitations after delivery, in the scope the
 * runtime binds from this fact.
 *
 * @since 3.2.0
 */
public final class InviteeSetupCompletedEvent extends IntegrationEvent {

    /** Event type the manifest declares under {@code events.publishes}. */
    public static final String EVENT_TYPE = "FirstOwnerInviteeSetupCompleted";

    private final FirstOwnerInviteeSetupCompletedEvent fact;

    public InviteeSetupCompletedEvent(Long identityId) {
        super(IdentityAuditFactRecordedEvent.SOURCE_MODULE);
        this.fact = new FirstOwnerInviteeSetupCompletedEvent(Objects.requireNonNull(identityId, "identityId must not be null"));
    }

    /** One identity's facts keep their order (root BRX-RS-MESSAGE-027). */
    @Override
    public String getRoutingKey() {
        return "IDENTITY:" + fact.identityId();
    }

    @Override
    public String eventTypeId() {
        return EVENT_TYPE;
    }

    @Override
    public FirstOwnerInviteeSetupCompletedEvent payload() {
        return fact;
    }
}
