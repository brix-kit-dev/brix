package io.infra.adapter.identity.provider.support;

import java.time.Duration;
import java.util.Objects;

/**
 * Login sessions (by {@code sid}) that ended before their time: after logout or after a refresh
 * token replay (multi-tenant 5.1 §9.3 Phase 5: revoke the family and force a new login). A
 * revoked login session can no longer authorize clients or refresh tokens. Entries live as long
 * as a login session can live.
 *
 * @since 3.2.0
 */
public final class LoginSessionRevocations {

    private static final String REVOKED = "sid:revoked:";

    private final EncryptedStateStore store;
    private final Duration maxLoginSessionLifetime;

    public LoginSessionRevocations(EncryptedStateStore store, Duration maxLoginSessionLifetime) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.maxLoginSessionLifetime = Objects.requireNonNull(maxLoginSessionLifetime,
                "maxLoginSessionLifetime must not be null");
    }

    public void revoke(String sid) {
        if (sid != null && !sid.isBlank()) {
            store.put(REVOKED + Secrets.sha256(sid), Boolean.TRUE, maxLoginSessionLifetime);
        }
    }

    public boolean isRevoked(String sid) {
        return sid == null || store.get(REVOKED + Secrets.sha256(sid), Boolean.class).orElse(false);
    }
}
