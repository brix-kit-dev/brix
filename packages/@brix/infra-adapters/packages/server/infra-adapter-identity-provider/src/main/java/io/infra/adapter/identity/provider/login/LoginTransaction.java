package io.infra.adapter.identity.provider.login;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Progress of one hosted login in the login session. It names the identity only after the
 * identifier step, and the identity may be absent: an unknown account goes through the same
 * steps and fails at the password step (identity-auth {@code BRX-IA-FLOW-001}, IA-7).
 *
 * @since 3.2.0
 */
final class LoginTransaction implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String identifier;
    private final Long identityId;
    private final Instant startedAt;
    private String step;

    LoginTransaction(String identifier, Long identityId, Instant startedAt, String step) {
        this.identifier = identifier;
        this.identityId = identityId;
        this.startedAt = startedAt;
        this.step = step;
    }

    String identifier() {
        return identifier;
    }

    Long identityId() {
        return identityId;
    }

    Instant startedAt() {
        return startedAt;
    }

    String step() {
        return step;
    }

    void step(String step) {
        this.step = step;
    }
}
