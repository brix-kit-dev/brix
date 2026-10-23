package io.infra.adapter.identity.provider.authorization;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import io.runtime.sdk.capability.JwtIssuerCapability.AuthenticationContext;

/**
 * The result of a completed hosted login: who authenticated, how and when, and the Actor
 * context selected after authentication (identity-auth {@code BRX-IA-FLOW-002}).
 *
 * <p>It lives in the login session of the identity protocol surface. When an authorization
 * code is issued, it is stored with the authorization, so the code is bound to the selected
 * context ({@code BRX-IA-FLOW-003}). It carries no credentials and no authorities: tenant
 * permissions come from the Runtime grant table on every request.</p>
 *
 * @since 3.2.0
 */
public final class HostedLoginAuthentication extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    /** Context kind of Actor (B-side staff) logins. */
    public static final String CONTEXT_ACTOR = "actor";

    private final Long identityId;
    private final String username;
    private final String email;
    private final String acr;
    private final List<String> amr;
    private final Instant authTime;
    private final String sid;
    private final String contextKind;
    private final String contextId;
    private final Long tenantId;

    public HostedLoginAuthentication(Long identityId, String username, String email, String acr,
                                     List<String> amr, Instant authTime, String sid,
                                     String contextKind, String contextId, Long tenantId) {
        super(List.of());
        this.identityId = Objects.requireNonNull(identityId, "identityId must not be null");
        this.username = username;
        this.email = email;
        this.acr = Objects.requireNonNull(acr, "acr must not be null");
        this.amr = List.copyOf(amr);
        this.authTime = Objects.requireNonNull(authTime, "authTime must not be null");
        this.sid = Objects.requireNonNull(sid, "sid must not be null");
        this.contextKind = Objects.requireNonNull(contextKind, "contextKind must not be null");
        this.contextId = Objects.requireNonNull(contextId, "contextId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        setAuthenticated(true);
    }

    @Override
    public String getName() {
        return String.valueOf(identityId);
    }

    @Override
    public Object getPrincipal() {
        return getName();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    public AuthenticationContext authenticationContext() {
        return new AuthenticationContext(acr, amr, authTime, sid);
    }

    public Long getIdentityId() {
        return identityId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getAcr() {
        return acr;
    }

    public List<String> getAmr() {
        return amr;
    }

    public Instant getAuthTime() {
        return authTime;
    }

    public String getSid() {
        return sid;
    }

    public String getContextKind() {
        return contextKind;
    }

    public String getContextId() {
        return contextId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    @Override
    public String toString() {
        return "HostedLoginAuthentication{identityId=" + identityId + ", acr=" + acr + ", amr=" + amr
                + ", contextKind=" + contextKind + ", tenantId=" + tenantId + "}";
    }
}
