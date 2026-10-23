package io.infra.adapter.identity.provider.bff;

/**
 * A login the BFF started and has not finished: the PKCE verifier, nonce and return path
 * for one {@code state}, bound to the browser that started it.
 *
 * @since 3.2.0
 */
record PendingLogin(String codeVerifier, String nonce, String returnTo, String browserBinding, long createdAt) {

    @Override
    public String toString() {
        return "PendingLogin{returnTo=" + returnTo + ", createdAt=" + createdAt + "}";
    }
}
