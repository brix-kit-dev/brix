/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.identity.provider.login;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * What the hosted page says about the tenant Owner's answer to an acceptance (identity-auth BRX-IA-ONBOARD-002).
 */
class DispatcherInvitationAcceptanceTest {

    @Test
    void anAcceptanceThatIsStillRunningSaysNothingAgainstTheInvitation() {
        // Standard-link plan point 10b-1: a second submit while the first runs must not call the invitation invalid.
        assertThat(DispatcherInvitationAcceptance.reason(409, "endpoint.idempotency_in_progress"))
                .isEqualTo("unavailable");
    }

    @Test
    void theOwnersRefusalsKeepTheirReasons() {
        assertThat(DispatcherInvitationAcceptance.reason(409, "INSTALLATION_QUOTA_EXCEEDED"))
                .isEqualTo("invitation_quota");
        assertThat(DispatcherInvitationAcceptance.reason(409, "FIRST_OWNER_ALREADY_EXISTS"))
                .isEqualTo("invitation_taken");
        assertThat(DispatcherInvitationAcceptance.reason(403, "FIRST_OWNER_INVITATION_EMAIL_MISMATCH"))
                .isEqualTo("invitation_mismatch");
        assertThat(DispatcherInvitationAcceptance.reason(404, "FIRST_OWNER_INVITATION_NOT_FOUND"))
                .isEqualTo("invitation_invalid");
        assertThat(DispatcherInvitationAcceptance.reason(503, null)).isEqualTo("unavailable");
    }
}
