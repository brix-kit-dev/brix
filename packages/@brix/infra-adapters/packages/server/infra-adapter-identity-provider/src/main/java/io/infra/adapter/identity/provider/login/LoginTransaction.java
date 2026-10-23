package io.infra.adapter.identity.provider.login;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import io.infra.adapter.identity.provider.support.Secrets;

/**
 * Progress of one hosted login in the login session. It names the identity only after the
 * identifier step, and the identity may be absent: an unknown account goes through the same
 * steps and fails at the password step (identity-auth {@code BRX-IA-FLOW-001}, IA-7).
 *
 * <p>After the second factor it keeps when and how the identity authenticated, so choosing
 * one of several tenants does not change the authentication event.</p>
 *
 * @since 3.2.0
 */
final class LoginTransaction implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    private final String id = Secrets.randomToken(16);
    private final String identifier;
    private final Long identityId;
    private final Instant startedAt;
    private String step;
    private Instant authTime;
    private String sid;
    private int ticketGeneration;

    LoginTransaction(String identifier, Long identityId, Instant startedAt, String step) {
        this.identifier = identifier;
        this.identityId = identityId;
        this.startedAt = startedAt;
        this.step = step;
    }

    String id() {
        return id;
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

    /** Records the completed second factor; the selected context comes later. */
    void authenticated(Instant authTime, String sid) {
        this.authTime = authTime;
        this.sid = sid;
    }

    Instant authTime() {
        return authTime;
    }

    String sid() {
        return sid;
    }

    int ticketGeneration() {
        return ticketGeneration;
    }

    /** Starts a new set of selection tickets; earlier ones stop working. */
    int nextTicketGeneration() {
        return ++ticketGeneration;
    }
}
