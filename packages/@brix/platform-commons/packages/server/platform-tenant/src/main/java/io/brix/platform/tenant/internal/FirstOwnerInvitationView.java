package io.brix.platform.tenant.internal;

import java.time.OffsetDateTime;

/** FIRST_OWNER invitation view that never exposes token material or URLs. */
public record FirstOwnerInvitationView(
        Long id,
        Long tenantId,
        String inviteeEmail,
        String status,
        OffsetDateTime expiresAt) {
}
