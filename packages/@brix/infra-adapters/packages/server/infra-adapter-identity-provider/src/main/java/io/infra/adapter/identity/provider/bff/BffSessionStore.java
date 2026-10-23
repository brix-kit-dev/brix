package io.infra.adapter.identity.provider.bff;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.infra.adapter.identity.provider.support.EncryptedStateStore;
import io.infra.adapter.identity.provider.support.Secrets;

/**
 * Encrypted BFF sessions in the state store, addressed by the SHA-256 of the session ID so
 * the store never holds a usable cookie value. A session ends at its absolute lifetime or
 * after the idle timeout without use.
 *
 * @since 3.2.0
 */
public final class BffSessionStore {

    private static final String SESSION = "bff:session:";
    private static final String PENDING = "bff:pending:";
    private static final Duration PENDING_TTL = Duration.ofMinutes(10);
    /** Writes for "last seen" are skipped when the previous one is this recent. */
    private static final long TOUCH_INTERVAL_SECONDS = 60;

    private final EncryptedStateStore store;
    private final Duration idleTimeout;
    private final Clock clock;

    public BffSessionStore(EncryptedStateStore store, Duration idleTimeout, Clock clock) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.idleTimeout = Objects.requireNonNull(idleTimeout, "idleTimeout must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** Stores a new session and returns its ID, the value of the session cookie. */
    public String create(BffSession session) {
        String id = Secrets.randomToken(32);
        save(id, session);
        return id;
    }

    /** Finds a live session and records its use. */
    public Optional<BffSession> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Optional<BffSession> found = store.get(SESSION + Secrets.sha256(id), BffSession.class);
        if (found.isEmpty()) {
            return found;
        }
        BffSession session = found.get();
        if (session.expiredAt(now)) {
            delete(id);
            return Optional.empty();
        }
        if (now.getEpochSecond() - session.lastSeenAt() >= TOUCH_INTERVAL_SECONDS) {
            session = session.seenAt(now);
            save(id, session);
        }
        return Optional.of(session);
    }

    public void save(String id, BffSession session) {
        Instant now = clock.instant();
        Duration remaining = Duration.ofSeconds(session.absoluteExpiresAt() - now.getEpochSecond());
        store.put(SESSION + Secrets.sha256(id), session, remaining.compareTo(idleTimeout) < 0 ? remaining : idleTimeout);
    }

    public void delete(String id) {
        if (id != null && !id.isBlank()) {
            store.remove(SESSION + Secrets.sha256(id));
        }
    }

    void savePending(String state, PendingLogin pending) {
        store.put(PENDING + Secrets.sha256(state), pending, PENDING_TTL);
    }

    /** Returns the pending login of {@code state} and removes it, so a state is used once. */
    Optional<PendingLogin> takePending(String state) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        return store.take(PENDING + Secrets.sha256(state), PendingLogin.class)
                .filter(pending -> clock.instant().getEpochSecond() - pending.createdAt() < PENDING_TTL.toSeconds());
    }
}
