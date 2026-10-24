package io.infra.adapter.identity.provider.login;

import io.runtime.sdk.capability.IdentityAccountCapability.IdentityRecord;

/**
 * Accepts a FIRST_OWNER invitation for an identity that has just completed both factors on the
 * hosted invitation page. The tenant Owner decides in its own acceptance entry (multi-tenant 5.1
 * §5.7, {@code BRX-TENANT-ENTRY-005}); the identity protocol surface never writes tenant data.
 *
 * @since 3.2.0
 */
public interface InvitationAcceptance {

    /**
     * Accepts the invitation as the identity.
     *
     * @param identity identity that completed both factors
     * @param invitationToken raw token of the invitation link
     * @return the tenant the invitation activated, or why it was not accepted
     */
    Outcome accept(IdentityRecord identity, String invitationToken);

    /**
     * The tenant an accepted invitation activated, or the stable reason the page shows.
     *
     * @param tenantId activated tenant, {@code null} when not accepted
     * @param error stable error code, {@code null} when accepted
     */
    record Outcome(Long tenantId, String error) {

        static Outcome accepted(Long tenantId) {
            return new Outcome(tenantId, null);
        }

        static Outcome refused(String error) {
            return new Outcome(null, error);
        }

        boolean isAccepted() {
            return tenantId != null;
        }
    }
}
