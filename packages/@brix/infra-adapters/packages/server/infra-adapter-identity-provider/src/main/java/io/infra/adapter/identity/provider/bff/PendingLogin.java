package io.infra.adapter.identity.provider.bff;

/**
 * A login the BFF started and has not finished: the PKCE verifier, nonce and return path
 * for one {@code state}, bound to the browser that started it. A context switch also names
 * the Actor context the new tokens must be bound to.
 *
 * @since 3.2.0
 */
record PendingLogin(String codeVerifier, String nonce, String returnTo, String browserBinding, long createdAt,
                    String targetContextId) {

    PendingLogin(String codeVerifier, String nonce, String returnTo, String browserBinding, long createdAt) {
        this(codeVerifier, nonce, returnTo, browserBinding, createdAt, null);
    }

    @Override
    public String toString() {
        return "PendingLogin{returnTo=" + returnTo + ", createdAt=" + createdAt + ", switch=" + (targetContextId != null) + "}";
    }
}
