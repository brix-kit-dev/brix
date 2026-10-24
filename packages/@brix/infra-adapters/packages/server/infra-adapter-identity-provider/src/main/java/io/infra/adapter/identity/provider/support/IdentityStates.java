package io.infra.adapter.identity.provider.support;

/**
 * Identity states that may sign in at the staff entry of the identity protocol surface and keep
 * receiving its tokens.
 *
 * <p>{@code LOCKED} is the temporary lock the legacy platform login sets after repeated failed
 * attempts. Root {@code BRX-RS-SECURITY-014} confines such a lock to the entry and credential
 * where the failures happened, so it does not keep staff out here; this entry throttles its own
 * failures per account and source. Identities still being set up or disabled never sign in
 * here.</p>
 *
 * @since 3.2.0
 */
public final class IdentityStates {

    private static final String ACTIVE = "ACTIVE";
    private static final String LOCKED = "LOCKED";

    private IdentityStates() {
    }

    /** Whether an identity in this state may sign in to the tenant console. */
    public static boolean maySignIn(String status) {
        return ACTIVE.equals(status) || LOCKED.equals(status);
    }

    /** Whether the identity carries the temporary lock of another entry. */
    public static boolean isLocked(String status) {
        return LOCKED.equals(status);
    }
}
