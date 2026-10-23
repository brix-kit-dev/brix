package io.infra.adapter.identity.provider.bff;

import java.time.Instant;
import java.util.List;

/**
 * Server-side state of one browser session of a console (root {@code BRX-RS-SECURITY-012}).
 * The tokens never leave the server; the browser only holds the opaque session ID cookie.
 *
 * @since 3.2.0
 */
public record BffSession(
        String csrfToken,
        long createdAt,
        long absoluteExpiresAt,
        long lastSeenAt,
        String accessToken,
        long accessTokenExpiresAt,
        String refreshToken,
        String idToken,
        String subject,
        String email,
        String username,
        String contextKind,
        String contextId,
        String tenantId,
        String tenantName,
        String memberType,
        String acr,
        List<String> amr,
        long authTime,
        String sid
) {

    boolean expiredAt(Instant now) {
        return now.getEpochSecond() >= absoluteExpiresAt;
    }

    /** Whether the access token must be refreshed before it is used at {@code now}. */
    boolean accessTokenStaleAt(Instant now) {
        return now.getEpochSecond() + 30 >= accessTokenExpiresAt;
    }

    BffSession seenAt(Instant now) {
        return new BffSession(csrfToken, createdAt, absoluteExpiresAt, now.getEpochSecond(), accessToken,
                accessTokenExpiresAt, refreshToken, idToken, subject, email, username, contextKind, contextId,
                tenantId, tenantName, memberType, acr, amr, authTime, sid);
    }

    BffSession withTokens(String newAccessToken, long newAccessTokenExpiresAt, String newRefreshToken,
                          String newIdToken) {
        return new BffSession(csrfToken, createdAt, absoluteExpiresAt, lastSeenAt, newAccessToken,
                newAccessTokenExpiresAt, newRefreshToken == null ? refreshToken : newRefreshToken,
                newIdToken == null ? idToken : newIdToken, subject, email, username, contextKind, contextId,
                tenantId, tenantName, memberType, acr, amr, authTime, sid);
    }

    @Override
    public String toString() {
        return "BffSession{subject=" + subject + ", contextKind=" + contextKind + ", tenantId=" + tenantId
                + ", acr=" + acr + ", absoluteExpiresAt=" + absoluteExpiresAt + "}";
    }
}
