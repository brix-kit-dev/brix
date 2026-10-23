package io.infra.adapter.identity.provider.bff;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import io.infra.adapter.identity.provider.support.Secrets;

/**
 * The BFF as an OAuth 2.0 confidential client of the identity protocol surface: authorization
 * code + PKCE S256 with {@code state} and {@code nonce} (root {@code BRX-RS-SECURITY-002}),
 * token requests authenticated with the client secret, and ID tokens validated locally for
 * signature, algorithm, issuer, audience, lifetime and nonce ({@code BRX-RS-SECURITY-004}).
 *
 * <p>Token requests go to this process's own token endpoint over the loopback interface, so
 * the standard protocol path also runs in embedded mode.</p>
 *
 * @since 3.2.0
 */
public final class BffTokenClient {

    static final String CALLBACK_PATH = "/bff/callback";
    private static final Set<String> AAL2_OR_STRONGER = Set.of("brix:aal2", "brix:aal2-phr");

    private final String issuer;
    private final String clientId;
    private final String clientSecret;
    private final Supplier<String> internalBaseUrl;
    private final JWKSource<SecurityContext> jwkSource;
    private final RestClient restClient;
    private final RestClient apiClient;
    private volatile Registration registration;

    public BffTokenClient(String issuer, String clientId, String clientSecret, Supplier<String> internalBaseUrl,
                          JWKSource<SecurityContext> jwkSource) {
        this.issuer = Objects.requireNonNull(issuer, "issuer must not be null");
        this.clientId = Objects.requireNonNull(clientId, "clientId must not be null");
        this.clientSecret = Objects.requireNonNull(clientSecret, "clientSecret must not be null");
        this.internalBaseUrl = Objects.requireNonNull(internalBaseUrl, "internalBaseUrl must not be null");
        this.jwkSource = Objects.requireNonNull(jwkSource, "jwkSource must not be null");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .configureMessageConverters(converters -> converters
                        .addCustomConverter(new FormHttpMessageConverter())
                        .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build();
        this.apiClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public String redirectUri() {
        return issuer + CALLBACK_PATH;
    }

    /** The authorization request for a new login; its URI is where the browser goes next. */
    OAuth2AuthorizationRequest authorizationRequest(String state, String nonce, String codeVerifier) {
        return authorizationRequest(state, nonce, codeVerifier, Map.of());
    }

    /**
     * The authorization request with extra parameters, for example {@code prompt=none} and the
     * target context of a switch.
     */
    OAuth2AuthorizationRequest authorizationRequest(String state, String nonce, String codeVerifier,
                                                    Map<String, String> extraParameters) {
        Map<String, Object> parameters = new java.util.LinkedHashMap<>(extraParameters);
        parameters.put(OidcParameterNames.NONCE, nonce);
        parameters.put(PkceParameterNames.CODE_CHALLENGE, s256(codeVerifier));
        parameters.put(PkceParameterNames.CODE_CHALLENGE_METHOD, "S256");
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(issuer + "/oauth2/authorize")
                .clientId(clientId)
                .redirectUri(redirectUri())
                .scopes(Set.of("openid", "profile", "email"))
                .state(state)
                .additionalParameters(parameters)
                .attributes(Map.of(PkceParameterNames.CODE_VERIFIER, codeVerifier))
                .build();
    }

    /** Exchanges an authorization code and validates the ID token against the login's nonce. */
    TokenSet exchange(PendingLogin pending, String state, String code) {
        OAuth2AuthorizationRequest request = authorizationRequest(state, pending.nonce(), pending.codeVerifier());
        OAuth2AuthorizationResponse response = OAuth2AuthorizationResponse.success(code)
                .redirectUri(redirectUri())
                .state(state)
                .build();
        RestClientAuthorizationCodeTokenResponseClient client = new RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(restClient);
        OAuth2AccessTokenResponse tokens = client.getTokenResponse(new OAuth2AuthorizationCodeGrantRequest(
                registration().client(), new OAuth2AuthorizationExchange(request, response)));
        Jwt idToken = validateIdToken(tokens);
        if (!Secrets.matches(pending.nonce(), idToken.getClaimAsString(OidcParameterNames.NONCE))) {
            throw new JwtException("ID token nonce does not match the login");
        }
        return new TokenSet(tokens, idToken);
    }

    /** Refreshes the session's tokens; the new ID token must name the same subject and login session. */
    TokenSet refresh(BffSession session) {
        RestClientRefreshTokenTokenResponseClient client = new RestClientRefreshTokenTokenResponseClient();
        client.setRestClient(restClient);
        OAuth2AccessToken current = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                session.accessToken(), null, Instant.ofEpochSecond(session.accessTokenExpiresAt()));
        OAuth2AccessTokenResponse tokens = client.getTokenResponse(new OAuth2RefreshTokenGrantRequest(
                registration().client(), current, new OAuth2RefreshToken(session.refreshToken(), null)));
        Jwt idToken = validateIdToken(tokens);
        if (!session.subject().equals(idToken.getSubject())
                || !Secrets.matches(session.sid(), idToken.getClaimAsString("sid"))) {
            throw new JwtException("Refreshed ID token belongs to another login");
        }
        return new TokenSet(tokens, idToken);
    }

    /**
     * Calls a Dispatcher API of this process with the session's access token, as the browser's
     * requests do through the relay, and returns the JSON object it answers.
     */
    Map<String, Object> callApi(String path, String accessToken) {
        return apiClient.get()
                .uri(internalBaseUrl.get() + path)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /** Revokes a refresh token (RFC 7009); the authorization server invalidates its access tokens too. */
    void revoke(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");
        restClient.post()
                .uri(internalBaseUrl.get() + "/oauth2/revoke")
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret, StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }

    private Jwt validateIdToken(OAuth2AccessTokenResponse tokens) {
        Object value = tokens.getAdditionalParameters().get(OidcParameterNames.ID_TOKEN);
        if (!(value instanceof String idToken) || idToken.isBlank()) {
            throw new JwtException("Token response has no ID token");
        }
        Jwt jwt = registration().idTokenDecoder().decode(idToken);
        if (!AAL2_OR_STRONGER.contains(jwt.getClaimAsString("acr"))) {
            throw new JwtException("ID token does not meet the client's authentication level");
        }
        return jwt;
    }

    private Registration registration() {
        Registration current = registration;
        if (current == null) {
            synchronized (this) {
                current = registration;
                if (current == null) {
                    current = createRegistration();
                    registration = current;
                }
            }
        }
        return current;
    }

    private Registration createRegistration() {
        String base = internalBaseUrl.get();
        ClientRegistration client = ClientRegistration.withRegistrationId(clientId)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri())
                .scope("openid", "profile", "email")
                .authorizationUri(issuer + "/oauth2/authorize")
                .tokenUri(base + "/oauth2/token")
                .jwkSetUri(base + "/oauth2/jwks")
                .issuerUri(issuer)
                .userNameAttributeName("sub")
                .build();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(60)), new OidcIdTokenValidator(client)));
        return new Registration(client, decoder);
    }

    private static String s256(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private record Registration(ClientRegistration client, NimbusJwtDecoder idTokenDecoder) {
    }

    /** Tokens of one token response and its validated ID token. */
    record TokenSet(OAuth2AccessTokenResponse response, Jwt idToken) {

        String accessToken() {
            return response.getAccessToken().getTokenValue();
        }

        long accessTokenExpiresAt() {
            Instant expiresAt = response.getAccessToken().getExpiresAt();
            return expiresAt == null ? Instant.now().getEpochSecond() : expiresAt.getEpochSecond();
        }

        String refreshToken() {
            return response.getRefreshToken() == null ? null : response.getRefreshToken().getTokenValue();
        }

        @Override
        public String toString() {
            return "TokenSet{subject=" + idToken.getSubject() + "}";
        }
    }
}
