package io.infra.adapter.identity.provider.login;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Progress of one hosted credential setup in the login session. The setup token stays on the
 * server for the rest of the setup; the page only held it until it sent it. After the password
 * step only the password's hash is kept, never the password.
 *
 * @since 3.2.0
 */
final class SetupTransaction implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String token;
    private final Long identityId;
    private final String email;
    private final String purpose;
    private final Instant startedAt;
    private String step;
    private String passwordHash;
    private String otpauthUri;

    SetupTransaction(String token, Long identityId, String email, String purpose, Instant startedAt, String step) {
        this.token = token;
        this.identityId = identityId;
        this.email = email;
        this.purpose = purpose;
        this.startedAt = startedAt;
        this.step = step;
    }

    String token() {
        return token;
    }

    Long identityId() {
        return identityId;
    }

    String email() {
        return email;
    }

    String purpose() {
        return purpose;
    }

    Instant startedAt() {
        return startedAt;
    }

    String step() {
        return step;
    }

    String passwordHash() {
        return passwordHash;
    }

    /** The enrollment the authenticator app reads; kept so a reload shows the same secret. */
    String otpauthUri() {
        return otpauthUri;
    }

    void passwordAccepted(String passwordHash, String otpauthUri, String nextStep) {
        this.passwordHash = passwordHash;
        this.otpauthUri = otpauthUri;
        this.step = nextStep;
    }

    @Override
    public String toString() {
        return "SetupTransaction[identityId=" + identityId + ", step=" + step + ", token=<redacted>]";
    }
}
