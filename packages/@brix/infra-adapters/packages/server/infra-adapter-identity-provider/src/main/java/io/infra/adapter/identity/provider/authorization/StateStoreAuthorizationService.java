package io.infra.adapter.identity.provider.authorization;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import io.infra.adapter.identity.provider.support.EncryptedStateStore;
import io.infra.adapter.identity.provider.support.Secrets;

/**
 * Keeps authorization records of the embedded authorization server in the state store,
 * encrypted (identity-auth §2: {@code infra-adapter-redis} stores BFF sessions and
 * authorization records).
 *
 * <p>A record is found by its ID or by the SHA-256 of one of its token values; the lookup
 * only succeeds when the record still holds that exact token, so a rotated-out refresh token
 * or a replaced code no longer resolves. Records expire with their longest-lived token.</p>
 *
 * <p>Only the attributes of the authorization code flow are kept: the authorization request
 * and the {@link HostedLoginAuthentication} principal. Saving any other attribute fails.</p>
 *
 * @since 3.2.0
 */
public final class StateStoreAuthorizationService implements OAuth2AuthorizationService {

    private static final String RECORD = "authz:id:";
    private static final String TOKEN_INDEX = "authz:token:";
    private static final String PRINCIPAL_ATTRIBUTE = Principal.class.getName();
    private static final String REQUEST_ATTRIBUTE = OAuth2AuthorizationRequest.class.getName();
    private static final Duration MIN_TTL = Duration.ofMinutes(1);

    private final EncryptedStateStore store;
    private final RegisteredClientRepository clients;
    private final Supplier<Instant> clock;

    public StateStoreAuthorizationService(EncryptedStateStore store, RegisteredClientRepository clients) {
        this(store, clients, Instant::now);
    }

    StateStoreAuthorizationService(EncryptedStateStore store, RegisteredClientRepository clients,
                                   Supplier<Instant> clock) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.clients = Objects.requireNonNull(clients, "clients must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        StoredAuthorization stored = StoredAuthorization.from(authorization);
        Duration ttl = stored.timeToLive(clock.get());
        store.put(RECORD + stored.id(), stored, ttl);
        for (StoredToken token : stored.tokens()) {
            Duration tokenTtl = token.expiresAt() == null ? ttl
                    : max(Duration.between(clock.get(), Instant.ofEpochSecond(token.expiresAt())), MIN_TTL);
            store.put(TOKEN_INDEX + Secrets.sha256(token.value()), stored.id(), tokenTtl);
        }
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        store.remove(RECORD + authorization.getId());
        for (String kind : StoredToken.KINDS) {
            OAuth2Authorization.Token<?> token = authorization.getToken(StoredToken.typeOf(kind));
            if (token != null) {
                store.remove(TOKEN_INDEX + Secrets.sha256(token.getToken().getTokenValue()));
            }
        }
    }

    @Override
    public OAuth2Authorization findById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return store.get(RECORD + id, StoredAuthorization.class).map(this::toAuthorization).orElse(null);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Optional<String> id = store.get(TOKEN_INDEX + Secrets.sha256(token), String.class);
        if (id.isEmpty()) {
            return null;
        }
        OAuth2Authorization authorization = findById(id.get());
        if (authorization == null) {
            return null;
        }
        for (String kind : StoredToken.kindsFor(tokenType)) {
            OAuth2Authorization.Token<?> held = authorization.getToken(StoredToken.typeOf(kind));
            if (held != null && Secrets.matches(held.getToken().getTokenValue(), token)) {
                return authorization;
            }
        }
        return null;
    }

    private OAuth2Authorization toAuthorization(StoredAuthorization stored) {
        RegisteredClient client = clients.findById(stored.registeredClientId());
        if (client == null) {
            return null;
        }
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(client)
                .id(stored.id())
                .principalName(stored.principalName())
                .authorizationGrantType(new AuthorizationGrantType(stored.grantType()))
                .authorizedScopes(stored.scopes());
        if (stored.request() != null) {
            builder.attribute(REQUEST_ATTRIBUTE, stored.request().toRequest());
        }
        if (stored.principal() != null) {
            builder.attribute(PRINCIPAL_ATTRIBUTE, stored.principal().toAuthentication());
        }
        for (StoredToken token : stored.tokens()) {
            builder.token(token.toToken(), metadata -> {
                metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, token.invalidated());
                if (token.claims() != null) {
                    metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, token.claims());
                }
            });
        }
        return builder.build();
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** Serialized form of an authorization. */
    record StoredAuthorization(String id, String registeredClientId, String principalName, String grantType,
                               Set<String> scopes, StoredRequest request, StoredPrincipal principal,
                               List<StoredToken> tokens) {

        static StoredAuthorization from(OAuth2Authorization authorization) {
            for (String attribute : authorization.getAttributes().keySet()) {
                if (!PRINCIPAL_ATTRIBUTE.equals(attribute) && !REQUEST_ATTRIBUTE.equals(attribute)) {
                    throw new IllegalArgumentException("Unsupported authorization attribute: " + attribute);
                }
            }
            OAuth2AuthorizationRequest request = authorization.getAttribute(REQUEST_ATTRIBUTE);
            Object principal = authorization.getAttribute(PRINCIPAL_ATTRIBUTE);
            if (principal != null && !(principal instanceof HostedLoginAuthentication)) {
                throw new IllegalArgumentException("Unsupported authorization principal: " + principal.getClass().getName());
            }
            List<StoredToken> tokens = new ArrayList<>();
            for (String kind : StoredToken.KINDS) {
                OAuth2Authorization.Token<?> token = authorization.getToken(StoredToken.typeOf(kind));
                if (token != null) {
                    tokens.add(StoredToken.from(kind, token));
                }
            }
            return new StoredAuthorization(authorization.getId(), authorization.getRegisteredClientId(),
                    authorization.getPrincipalName(), authorization.getAuthorizationGrantType().getValue(),
                    Set.copyOf(authorization.getAuthorizedScopes()),
                    request == null ? null : StoredRequest.from(request),
                    principal == null ? null : StoredPrincipal.from((HostedLoginAuthentication) principal),
                    List.copyOf(tokens));
        }

        Duration timeToLive(Instant now) {
            Instant latest = null;
            for (StoredToken token : tokens) {
                if (token.expiresAt() != null) {
                    Instant expiresAt = Instant.ofEpochSecond(token.expiresAt());
                    latest = latest == null || expiresAt.isAfter(latest) ? expiresAt : latest;
                }
            }
            // An authorization request in flight has no token yet; keep it as long as a code lives.
            Duration ttl = latest == null ? Duration.ofMinutes(10) : Duration.between(now, latest);
            return max(ttl, MIN_TTL);
        }
    }

    record StoredRequest(String authorizationUri, String clientId, String redirectUri, Set<String> scopes,
                         String state, Map<String, Object> additionalParameters, String authorizationRequestUri) {

        static StoredRequest from(OAuth2AuthorizationRequest request) {
            Map<String, Object> parameters = new LinkedHashMap<>();
            request.getAdditionalParameters().forEach((name, value) -> {
                if (value instanceof String text) {
                    parameters.put(name, text);
                } else if (value instanceof String[] values) {
                    parameters.put(name, List.of(values));
                } else {
                    throw new IllegalArgumentException("Unsupported authorization request parameter: " + name);
                }
            });
            return new StoredRequest(request.getAuthorizationUri(), request.getClientId(), request.getRedirectUri(),
                    Set.copyOf(request.getScopes()), request.getState(), parameters,
                    request.getAuthorizationRequestUri());
        }

        OAuth2AuthorizationRequest toRequest() {
            Map<String, Object> parameters = new LinkedHashMap<>();
            additionalParameters.forEach((name, value) -> parameters.put(name,
                    value instanceof List<?> values ? values.stream().map(String::valueOf).toArray(String[]::new)
                            : value));
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(authorizationUri)
                    .clientId(clientId)
                    .redirectUri(redirectUri)
                    .scopes(scopes)
                    .state(state)
                    .additionalParameters(parameters)
                    .authorizationRequestUri(authorizationRequestUri)
                    .build();
        }
    }

    record StoredPrincipal(Long identityId, String username, String email, String acr, List<String> amr,
                           long authTime, String sid, String contextKind, String contextId, Long tenantId) {

        static StoredPrincipal from(HostedLoginAuthentication login) {
            return new StoredPrincipal(login.getIdentityId(), login.getUsername(), login.getEmail(), login.getAcr(),
                    login.getAmr(), login.getAuthTime().getEpochSecond(), login.getSid(), login.getContextKind(),
                    login.getContextId(), login.getTenantId());
        }

        HostedLoginAuthentication toAuthentication() {
            return new HostedLoginAuthentication(identityId, username, email, acr, amr,
                    Instant.ofEpochSecond(authTime), sid, contextKind, contextId, tenantId);
        }
    }

    record StoredToken(String kind, String value, Long issuedAt, Long expiresAt, String tokenType,
                       Set<String> scopes, boolean invalidated, Map<String, Object> claims) {

        static final List<String> KINDS = List.of("code", "access", "refresh", "id");

        static Class<? extends OAuth2Token> typeOf(String kind) {
            return switch (kind) {
                case "code" -> OAuth2AuthorizationCode.class;
                case "access" -> OAuth2AccessToken.class;
                case "refresh" -> OAuth2RefreshToken.class;
                case "id" -> OidcIdToken.class;
                default -> throw new IllegalArgumentException("Unknown token kind: " + kind);
            };
        }

        static List<String> kindsFor(OAuth2TokenType tokenType) {
            if (tokenType == null) {
                return KINDS;
            }
            return switch (tokenType.getValue()) {
                case "code" -> List.of("code");
                case "access_token" -> List.of("access");
                case "refresh_token" -> List.of("refresh");
                case "id_token" -> List.of("id");
                default -> List.of();
            };
        }

        static StoredToken from(String kind, OAuth2Authorization.Token<?> held) {
            OAuth2Token token = held.getToken();
            String tokenType = null;
            Set<String> scopes = null;
            if (token instanceof OAuth2AccessToken accessToken) {
                tokenType = accessToken.getTokenType().getValue();
                scopes = Set.copyOf(accessToken.getScopes());
            }
            Map<String, Object> claims = null;
            if (token instanceof OidcIdToken idToken) {
                claims = jsonClaims(idToken.getClaims());
            }
            return new StoredToken(kind, token.getTokenValue(), epoch(token.getIssuedAt()), epoch(token.getExpiresAt()),
                    tokenType, scopes, held.isInvalidated(), claims);
        }

        OAuth2Token toToken() {
            Instant issued = issuedAt == null ? null : Instant.ofEpochSecond(issuedAt);
            Instant expires = expiresAt == null ? null : Instant.ofEpochSecond(expiresAt);
            return switch (kind) {
                case "code" -> new OAuth2AuthorizationCode(value, issued, expires);
                case "access" -> new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER.getValue().equalsIgnoreCase(tokenType)
                                ? OAuth2AccessToken.TokenType.BEARER : OAuth2AccessToken.TokenType.DPOP,
                        value, issued, expires, scopes == null ? Set.of() : scopes);
                case "refresh" -> new OAuth2RefreshToken(value, issued, expires);
                case "id" -> new OidcIdToken(value, issued, expires, claims);
                default -> throw new IllegalArgumentException("Unknown token kind: " + kind);
            };
        }

        private static Long epoch(Instant instant) {
            return instant == null ? null : instant.getEpochSecond();
        }

        /** ID token claims are strings, numbers, booleans, lists of strings and instants. */
        private static Map<String, Object> jsonClaims(Map<String, Object> claims) {
            Map<String, Object> result = new LinkedHashMap<>();
            claims.forEach((name, value) -> {
                if (value instanceof Instant instant) {
                    result.put(name, instant.getEpochSecond());
                } else if (value instanceof java.net.URL url) {
                    result.put(name, url.toString());
                } else if (value instanceof java.util.Collection<?> values) {
                    result.put(name, new ArrayList<>(values.stream().map(String::valueOf).toList()));
                } else if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                    result.put(name, value);
                } else if (value != null) {
                    result.put(name, String.valueOf(value));
                }
            });
            return result;
        }

        @Override
        public String toString() {
            return "StoredToken{kind=" + kind + ", invalidated=" + invalidated + "}";
        }
    }
}
