package io.infra.adapter.identity.provider.bff;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renews a session's access token with its refresh token before it expires. Concurrent
 * requests of one session refresh once: the second request waits and uses the result, so a
 * rotated refresh token is never presented twice.
 *
 * @since 3.2.0
 */
public final class BffTokenRefresher {

    private static final Logger audit = LoggerFactory.getLogger("brix.identity.audit");
    private static final int STRIPES = 64;

    private final BffSessionStore sessions;
    private final BffTokenClient tokens;
    private final Clock clock;
    private final Object[] locks = new Object[STRIPES];

    public BffTokenRefresher(BffSessionStore sessions, BffTokenClient tokens, Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.tokens = Objects.requireNonNull(tokens, "tokens must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new Object();
        }
    }

    /**
     * Returns the session with a usable access token, or empty when the session has ended or
     * cannot be renewed; the caller then treats the request as unauthenticated.
     */
    public Optional<BffSession> currentTokens(String sessionId, BffSession session) {
        if (!session.accessTokenStaleAt(clock.instant())) {
            return Optional.of(session);
        }
        synchronized (locks[Math.floorMod(sessionId.hashCode(), STRIPES)]) {
            Optional<BffSession> latest = sessions.find(sessionId);
            if (latest.isEmpty() || !latest.get().accessTokenStaleAt(clock.instant())) {
                return latest;
            }
            BffSession stale = latest.get();
            if (stale.refreshToken() == null) {
                sessions.delete(sessionId);
                return Optional.empty();
            }
            try {
                BffTokenClient.TokenSet refreshed = tokens.refresh(stale);
                BffSession renewed = stale.withTokens(refreshed.accessToken(), refreshed.accessTokenExpiresAt(),
                        refreshed.refreshToken(), refreshed.idToken().getTokenValue());
                sessions.save(sessionId, renewed);
                return Optional.of(renewed);
            } catch (RuntimeException e) {
                audit.info("event=identity.bff.refresh_failed identity={} error={}",
                        stale.subject(), e.getClass().getSimpleName());
                sessions.delete(sessionId);
                return Optional.empty();
            }
        }
    }
}
