package io.infra.adapter.identity.provider.authorization;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import io.infra.adapter.identity.provider.support.IdentityStates;
import io.infra.adapter.identity.provider.support.LoginSessionRevocations;
import io.runtime.sdk.capability.IdentityAccountCapability;
import io.runtime.sdk.capability.IdentityAccountCapability.IdentityRecord;
import io.runtime.sdk.capability.JwtIssuerCapability;
import io.runtime.sdk.capability.JwtIssuerCapability.ActorTokenRequest;
import io.runtime.sdk.capability.JwtIssuerCapability.IssuedToken;
import io.runtime.sdk.capability.JwtIssuerCapability.OidcIdTokenRequest;
import io.runtime.sdk.capability.TenantAccessCapability;
import io.runtime.sdk.capability.TenantAccessCapability.TenantMembershipRecord;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Issues the tokens of the {@code tenant-console} client by delegating signing to the Layer 2A
 * {@link JwtIssuerCapability}, so the Dispatcher validates them like every other Actor token.
 *
 * <p>Before every access or ID token the identity and the selected membership are read again
 * through Layer 2A contracts; a disabled identity, a membership that is no longer active or a
 * revoked login session ends the authorization ({@code invalid_grant}); the temporary lock of
 * another entry does not (root {@code BRX-RS-SECURITY-014}). Access tokens carry the Actor claims of
 * multi-tenant §8 plus {@code acr}, {@code amr}, {@code auth_time} and {@code sid} and no
 * permission list (multi-tenant T-30). Refresh tokens are opaque random values.</p>
 *
 * @since 3.2.0
 */
public final class ActorTokenGenerator implements OAuth2TokenGenerator<OAuth2Token> {

    static final OAuth2TokenType ID_TOKEN = new OAuth2TokenType(OidcParameterNames.ID_TOKEN);
    private static final String MEMBERSHIP_ACTIVE = "ACTIVE";

    private final JwtIssuerCapability issuer;
    private final IdentityAccountCapability identities;
    private final TenantAccessCapability tenants;
    private final LoginSessionRevocations revocations;
    private final String issuerUri;
    private final JsonMapper json;
    private final OAuth2RefreshTokenGenerator refreshTokens = new OAuth2RefreshTokenGenerator();

    public ActorTokenGenerator(JwtIssuerCapability issuer, IdentityAccountCapability identities,
                               TenantAccessCapability tenants, LoginSessionRevocations revocations,
                               String issuerUri, JsonMapper json) {
        this.issuer = Objects.requireNonNull(issuer, "issuer must not be null");
        this.identities = Objects.requireNonNull(identities, "identities must not be null");
        this.tenants = Objects.requireNonNull(tenants, "tenants must not be null");
        this.revocations = Objects.requireNonNull(revocations, "revocations must not be null");
        this.issuerUri = Objects.requireNonNull(issuerUri, "issuerUri must not be null");
        this.json = Objects.requireNonNull(json, "json must not be null");
    }

    @Override
    public OAuth2Token generate(OAuth2TokenContext context) {
        OAuth2TokenType type = context.getTokenType();
        if (OAuth2TokenType.REFRESH_TOKEN.equals(type)) {
            return refreshTokens.generate(context);
        }
        boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(type);
        if (!accessToken && !ID_TOKEN.equals(type)) {
            return null;
        }
        HostedLoginAuthentication principal = principalOf(context.getPrincipal());
        if (revocations.isRevoked(principal.getSid())) {
            throw invalidGrant("The login session has ended");
        }
        IdentityRecord identity = activeIdentity(principal);
        TenantMembershipRecord membership = activeMembership(principal);
        if (accessToken) {
            IssuedToken token = issuer.issueActorAccessToken(new ActorTokenRequest(
                    identity.id(), identity.email(), identity.username(),
                    membership.tenantId(), membership.memberId(), membership.memberType(),
                    List.of(membership.memberType()), List.of(), identity.tokenVersion(),
                    membership.contextId(), membership.authzVersion()), principal.authenticationContext());
            return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, token.value(),
                    token.issuedAt(), token.expiresAt(), context.getAuthorizedScopes());
        }
        Map<String, Object> contextClaims = new LinkedHashMap<>();
        if (context.getAuthorizedScopes().contains("email") && identity.email() != null) {
            contextClaims.put("email", identity.email());
        }
        if (context.getAuthorizedScopes().contains("profile") && identity.username() != null) {
            contextClaims.put("preferred_username", identity.username());
        }
        contextClaims.put("ctx", principal.getContextKind());
        contextClaims.put("cid", membership.contextId());
        contextClaims.put("tid", String.valueOf(membership.tenantId()));
        IssuedToken token = issuer.issueOidcIdToken(new OidcIdTokenRequest(
                issuerUri, principal.getName(), context.getRegisteredClient().getClientId(),
                nonceOf(context), principal.authenticationContext(), contextClaims));
        return toJwt(token);
    }

    private static HostedLoginAuthentication principalOf(Authentication principal) {
        if (principal instanceof HostedLoginAuthentication login
                && HostedLoginAuthentication.CONTEXT_ACTOR.equals(login.getContextKind())) {
            return login;
        }
        throw invalidGrant("The authorization was not granted by a hosted Actor login");
    }

    private IdentityRecord activeIdentity(HostedLoginAuthentication principal) {
        return identities.findIdentityById(principal.getIdentityId())
                .filter(identity -> IdentityStates.maySignIn(identity.status()))
                .orElseThrow(() -> invalidGrant("The identity is no longer active"));
    }

    private TenantMembershipRecord activeMembership(HostedLoginAuthentication principal) {
        return tenants.findMembershipByContextId(principal.getContextId())
                .filter(membership -> MEMBERSHIP_ACTIVE.equals(membership.status()))
                .filter(membership -> principal.getIdentityId().equals(membership.identityId()))
                .filter(membership -> principal.getTenantId().equals(membership.tenantId()))
                .orElseThrow(() -> invalidGrant("The selected tenant context is no longer active"));
    }

    private static String nonceOf(OAuth2TokenContext context) {
        if (!AuthorizationGrantType.AUTHORIZATION_CODE.equals(context.getAuthorizationGrantType())
                || context.getAuthorization() == null) {
            return null;
        }
        OAuth2AuthorizationRequest request =
                context.getAuthorization().getAttribute(OAuth2AuthorizationRequest.class.getName());
        Object nonce = request == null ? null : request.getAdditionalParameters().get(OidcParameterNames.NONCE);
        return nonce instanceof String value ? value : null;
    }

    /** The authorization server keeps ID tokens as {@link Jwt}s; read back exactly what was signed. */
    private Jwt toJwt(IssuedToken token) {
        String[] parts = token.value().split("\\.");
        Map<String, Object> headers = decode(parts[0]);
        Map<String, Object> claims = new LinkedHashMap<>(decode(parts[1]));
        claims.put("iat", token.issuedAt());
        claims.put("exp", token.expiresAt());
        return new Jwt(token.value(), token.issuedAt(), token.expiresAt(), headers, claims);
    }

    private Map<String, Object> decode(String part) {
        String value = new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
        return json.readValue(value, new TypeReference<Map<String, Object>>() {});
    }

    private static OAuth2AuthenticationException invalidGrant(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, description, null));
    }
}
