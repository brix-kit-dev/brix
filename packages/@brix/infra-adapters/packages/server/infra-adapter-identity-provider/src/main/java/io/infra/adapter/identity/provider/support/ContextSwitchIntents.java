package io.infra.adapter.identity.provider.support;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Context switches the BFF asked for on behalf of a login session (by {@code sid}). The BFF
 * records one only for a CSRF-checked {@code POST /bff/switch} to another context of the
 * session's own identity; the authorization endpoint switches a login only when it uses up the
 * matching intent. The login session cookie goes along with top-level navigations from any site,
 * so without this a planted link could change the context of a login session.
 *
 * <p>The intent carries the tenant of the context, so the switch resolves the context inside that
 * tenant (multi-tenant §6.4).</p>
 *
 * @since 3.2.0
 */
public final class ContextSwitchIntents {

    /** One-time and short-lived like a selection ticket: the browser follows the switch at once. */
    static final Duration TTL = Duration.ofMinutes(5);
    private static final String INTENT = "sid:switch:";

    private final EncryptedStateStore store;

    public ContextSwitchIntents(EncryptedStateStore store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    public void expect(String sid, String contextId, Long tenantId) {
        if (sid != null && !sid.isBlank() && contextId != null && tenantId != null) {
            store.put(INTENT + Secrets.sha256(sid), new Intent(contextId, tenantId), TTL);
        }
    }

    /** Uses up the login session's intent; returns the context's tenant only when it names this context. */
    public Optional<Long> consume(String sid, String contextId) {
        if (sid == null || sid.isBlank() || contextId == null) {
            return Optional.empty();
        }
        return store.take(INTENT + Secrets.sha256(sid), Intent.class)
                .filter(intent -> contextId.equals(intent.contextId()))
                .map(Intent::tenantId);
    }

    record Intent(String contextId, Long tenantId) {
    }
}
