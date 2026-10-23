package io.infra.adapter.identity.provider.authorization;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;

/**
 * Checks the client policy before an authorization code is issued (identity-auth
 * {@code BRX-IA-CLIENT-002}): {@code tenant-console} codes are only issued to a hosted Actor
 * login that reached {@code brix:aal2} ({@code BRX-IA-MFA-002}: staff always use two factors)
 * and selected an Actor context.
 *
 * <p>Requests without a logged-in user pass through, so the authorization endpoint sends the
 * browser to the hosted login first.</p>
 *
 * @since 3.2.0
 */
public final class ClientPolicyValidator implements Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> {

    /** Authentication levels that satisfy {@code brix:aal2}. */
    static final Set<String> AAL2_OR_STRONGER = Set.of("brix:aal2", "brix:aal2-phr");

    private final String tenantConsoleClientId;

    public ClientPolicyValidator(String tenantConsoleClientId) {
        this.tenantConsoleClientId = tenantConsoleClientId;
    }

    /** Adds this policy after the default redirect URI and scope validation. */
    public Consumer<List<AuthenticationProvider>> install() {
        Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> validator =
                new OAuth2AuthorizationCodeRequestAuthenticationValidator().andThen(this);
        return providers -> providers.forEach(provider -> {
            if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider codeRequestProvider) {
                codeRequestProvider.setAuthenticationValidator(validator);
            }
        });
    }

    @Override
    public void accept(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        OAuth2AuthorizationCodeRequestAuthenticationToken request = context.getAuthentication();
        if (!(request.getPrincipal() instanceof Authentication user)
                || user instanceof AnonymousAuthenticationToken || !user.isAuthenticated()) {
            return;
        }
        if (!tenantConsoleClientId.equals(context.getRegisteredClient().getClientId())) {
            throw denied(request, "This client is not served by the hosted login");
        }
        if (!(user instanceof HostedLoginAuthentication login)
                || !HostedLoginAuthentication.CONTEXT_ACTOR.equals(login.getContextKind())) {
            throw denied(request, "The tenant console requires an Actor context");
        }
        if (!AAL2_OR_STRONGER.contains(login.getAcr())) {
            throw denied(request, "The tenant console requires two-factor authentication");
        }
        // A requested context the login could not switch to (another identity's, inactive, or a
        // Subject context) is refused rather than silently replaced (multi-tenant T-26).
        Object requested = request.getAdditionalParameters().get(HostedLoginSessionFilter.CONTEXT_PARAMETER);
        if (requested != null && !requested.equals(login.getContextId())) {
            throw denied(request, "The requested context is not available");
        }
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationException denied(
            OAuth2AuthorizationCodeRequestAuthenticationToken request, String description) {
        return new OAuth2AuthorizationCodeRequestAuthenticationException(
                new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED, description, null), request);
    }
}
