package io.infra.adapter.identity.provider;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.SessionTrackingMode;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import io.infra.adapter.identity.provider.authorization.ActorTokenGenerator;
import io.infra.adapter.identity.provider.authorization.AuthorizationEntryPoint;
import io.infra.adapter.identity.provider.authorization.ClientPolicyValidator;
import io.infra.adapter.identity.provider.authorization.HostedLoginSessionFilter;
import io.infra.adapter.identity.provider.authorization.StateStoreAuthorizationService;
import io.infra.adapter.identity.provider.bff.BffController;
import io.infra.adapter.identity.provider.bff.BffSessionRelayFilter;
import io.infra.adapter.identity.provider.bff.BffSessionStore;
import io.infra.adapter.identity.provider.bff.BffTokenClient;
import io.infra.adapter.identity.provider.bff.BffTokenRefresher;
import io.infra.adapter.identity.provider.login.DispatcherInvitationAcceptance;
import io.infra.adapter.identity.provider.login.HostedLoginController;
import io.infra.adapter.identity.provider.login.HostedLoginPages;
import io.infra.adapter.identity.provider.login.HostedSetupController;
import io.infra.adapter.identity.provider.login.InvitationAcceptance;
import io.infra.adapter.identity.provider.support.ContextSwitchIntents;
import io.infra.adapter.identity.provider.support.EncryptedStateStore;
import io.infra.adapter.identity.provider.support.LoginSessionRevocations;
import io.infra.adapter.identity.provider.support.ProtocolRateLimitFilter;
import io.infra.adapter.identity.provider.support.Secrets;
import io.runtime.sdk.capability.IdentityAccountCapability;
import io.runtime.sdk.capability.JwtIssuerCapability;
import io.runtime.sdk.capability.PasswordCapability;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.StateStoreCapability;
import io.runtime.sdk.capability.TenantAccessCapability;
import tools.jackson.databind.json.JsonMapper;

/**
 * Assembles the identity protocol surface (root {@code BRX-RS-SECURITY-011}, identity-auth
 * 1.0 §2): the embedded Spring Authorization Server, the hosted login API and pages, and the
 * BFF of the tenant console.
 *
 * <p>It only runs when the Host Composition selects it ({@code brix.identity-provider.enabled}).
 * Its Spring Security filter chains only see the reserved prefixes {@code /oauth2/**},
 * {@code /.well-known/**}, {@code /auth/**} and {@code /bff/**}; {@code /api/**} stays with the
 * Dispatcher, which only gains the BFF session relay in front of its authentication step.
 * Identity, credential and tenant data are read through Layer 2A contracts only
 * ({@code BRX-IA-LAYER-001}).</p>
 *
 * @since 3.2.0
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "brix.identity-provider", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(IdentityProviderProperties.class)
@EnableWebSecurity
public class IdentityProviderAutoConfiguration {

    /** Client ID of the tenant console (identity-auth {@code BRX-IA-CLIENT-001}). */
    public static final String TENANT_CONSOLE = "tenant-console";
    /** Login session cookie of the identity protocol surface. */
    public static final String LOGIN_SESSION_COOKIE = "__Host-brix-idp";

    static final String[] RESERVED_PREFIXES = {"/oauth2/*", "/.well-known/*", "/auth/*", "/bff/*"};
    static final String PROTOCOL_POLICY = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; "
            + "form-action 'self'; base-uri 'none'; object-src 'none'";

    private static final Clock CLOCK = Clock.systemUTC();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Bean
    TenantConsoleClient identityProviderTenantConsoleClient(IdentityProviderProperties properties) {
        String configured = properties.getTenantConsole().getClientSecret();
        return new TenantConsoleClient(TENANT_CONSOLE,
                configured == null || configured.isBlank() ? Secrets.randomToken(32) : configured);
    }

    @Bean
    EncryptedStateStore identityProviderStateStore(StateStoreCapability stateStore,
                                                   SecretEncryptionCapability encryption) {
        return new EncryptedStateStore(stateStore, encryption, JSON);
    }

    @Bean
    RegisteredClientRepository identityProviderClients(IdentityProviderProperties properties,
                                                       TenantConsoleClient client) {
        String issuer = properties.requireIssuerOrigin();
        RegisteredClient tenantConsole = RegisteredClient.withId(TENANT_CONSOLE)
                .clientId(client.clientId())
                .clientSecret("{noop}" + client.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(issuer + "/bff/callback")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .authorizationCodeTimeToLive(Duration.ofMinutes(2))
                        .accessTokenTimeToLive(Duration.ofSeconds(JwtIssuerCapability.MAX_SESSION_ACCESS_TOKEN_SECONDS))
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .refreshTokenTimeToLive(properties.getSession().getAbsoluteTimeout())
                        .reuseRefreshTokens(false)
                        .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                        .build())
                .build();
        return new InMemoryRegisteredClientRepository(tenantConsole);
    }

    @Bean
    LoginSessionRevocations identityProviderLoginSessionRevocations(EncryptedStateStore store,
                                                                    IdentityProviderProperties properties) {
        return new LoginSessionRevocations(store, properties.getSession().getAbsoluteTimeout());
    }

    @Bean
    ContextSwitchIntents identityProviderContextSwitchIntents(EncryptedStateStore store) {
        return new ContextSwitchIntents(store);
    }

    @Bean
    OAuth2AuthorizationService identityProviderAuthorizations(EncryptedStateStore store,
                                                              RegisteredClientRepository clients,
                                                              LoginSessionRevocations revocations) {
        return new StateStoreAuthorizationService(store, clients, revocations);
    }

    @Bean
    JWKSource<SecurityContext> identityProviderJwkSource(JwtIssuerCapability issuer) {
        try {
            JWKSet keys = JWKSet.parse(issuer.publicJwkSet());
            if (keys.getKeys().isEmpty() || keys.getKeys().stream().anyMatch(key -> key.isPrivate())) {
                throw new IllegalStateException("The JWT issuer must publish public signing keys only");
            }
            return new ImmutableJWKSet<>(keys);
        } catch (ParseException e) {
            throw new IllegalStateException("The JWT issuer published an invalid JWK Set", e);
        }
    }

    /**
     * Authenticates access tokens at the OpenID Connect userinfo endpoint: the signature and
     * lifetime here, and the authorization server additionally requires an active authorization
     * record for the token.
     */
    @Bean
    JwtDecoder identityProviderUserInfoDecoder(JWKSource<SecurityContext> jwkSource) {
        return NimbusJwtDecoder.withJwkSource(jwkSource).build();
    }

    @Bean
    OAuth2TokenGenerator<OAuth2Token> identityProviderTokenGenerator(JwtIssuerCapability issuer,
                                                                    IdentityAccountCapability identities,
                                                                    TenantAccessCapability tenants,
                                                                    LoginSessionRevocations revocations,
                                                                    IdentityProviderProperties properties) {
        return new ActorTokenGenerator(issuer, identities, tenants, revocations, properties.requireIssuerOrigin(), JSON);
    }

    @Bean
    AuthorizationServerSettings identityProviderServerSettings(IdentityProviderProperties properties) {
        // Every endpoint stays under the reserved prefixes (identity-auth BRX-IA-LAYER-003).
        return AuthorizationServerSettings.builder()
                .issuer(properties.requireIssuerOrigin())
                .oidcUserInfoEndpoint("/oauth2/userinfo")
                .oidcLogoutEndpoint("/oauth2/logout")
                .oidcClientRegistrationEndpoint("/oauth2/oidc-register")
                .build();
    }

    @Bean
    SessionRegistry identityProviderSessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Removes ended login sessions from the session registry the authorization server keeps. */
    @Bean
    ServletListenerRegistrationBean<HttpSessionEventPublisher> identityProviderSessionEvents() {
        return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
    }

    /**
     * Holds the shared request limit of both chains. It is deliberately not a {@code Filter}
     * bean: Spring Boot would also register such a bean for every path, {@code /api/**} included.
     */
    @Bean
    ProtocolLimits identityProviderRateLimit(IdentityProviderProperties properties,
                                             LoginSessionRevocations revocations,
                                             ContextSwitchIntents switchIntents,
                                             TenantAccessCapability tenants) {
        return new ProtocolLimits(
                new ProtocolRateLimitFilter(properties.getLogin().getMaxRequestsPerSourcePerMinute()),
                new HostedLoginSessionFilter(revocations, switchIntents, tenants,
                        properties.getSession().getAbsoluteTimeout(), CLOCK));
    }

    @Bean
    @Order(1)
    SecurityFilterChain identityAuthorizationServerChain(HttpSecurity http,
                                                         IdentityProviderProperties properties,
                                                         RegisteredClientRepository clients,
                                                         OAuth2AuthorizationService authorizations,
                                                         OAuth2TokenGenerator<OAuth2Token> tokens,
                                                         AuthorizationServerSettings settings,
                                                         ProtocolLimits limits) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();
        ClientPolicyValidator clientPolicy = new ClientPolicyValidator(TENANT_CONSOLE);
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, server -> server
                        .registeredClientRepository(clients)
                        .authorizationService(authorizations)
                        .tokenGenerator(tokens)
                        .authorizationServerSettings(settings)
                        .authorizationEndpoint(endpoint -> endpoint.authenticationProviders(clientPolicy.install()))
                        .oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new AuthorizationEntryPoint(
                                new LoginUrlAuthenticationEntryPoint(properties.requireIssuerOrigin() + "/auth/login")),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .headers(IdentityProviderAutoConfiguration::protocolHeaders)
                .addFilterBefore(limits.filter(), SecurityContextHolderFilter.class)
                .addFilterAfter(limits.loginSession(), SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain identityProtocolSurfaceChain(HttpSecurity http, ProtocolLimits limits) throws Exception {
        http.securityMatcher("/auth/**", "/bff/**", "/oauth2/**", "/.well-known/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/auth/**", "/bff/**").permitAll()
                        .anyRequest().denyAll())
                // The BFF checks its own per-session CSRF token; hosted login uses the login session's.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/bff/**"))
                .headers(IdentityProviderAutoConfiguration::protocolHeaders)
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(limits.filter(), SecurityContextHolderFilter.class)
                .addFilterAfter(limits.loginSession(), SecurityContextHolderFilter.class);
        return http.build();
    }

    /** The security chains only run for the reserved prefixes, never for {@code /api/**}. */
    @Bean
    DelegatingFilterProxyRegistrationBean identityProtocolSecurityFilter() {
        DelegatingFilterProxyRegistrationBean registration =
                new DelegatingFilterProxyRegistrationBean("springSecurityFilterChain");
        registration.addUrlPatterns(RESERVED_PREFIXES);
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR));
        registration.setOrder(-100);
        return registration;
    }

    /**
     * The login session of the identity protocol surface: a {@code __Host-} cookie, sent on
     * top-level navigations to the authorization endpoint ({@code SameSite=Lax}); never in URLs.
     */
    @Bean
    ServletContextInitializer identityProviderLoginSessionCookie() {
        return servletContext -> {
            servletContext.setSessionTrackingModes(Set.of(SessionTrackingMode.COOKIE));
            SessionCookieConfig cookie = servletContext.getSessionCookieConfig();
            cookie.setName(LOGIN_SESSION_COOKIE);
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setPath("/");
            cookie.setAttribute("SameSite", "Lax");
        };
    }

    /** Accepts FIRST_OWNER invitations through the tenant Owner's own entry, over the loopback interface. */
    @Bean
    InvitationAcceptance identityProviderInvitationAcceptance(JwtIssuerCapability issuer,
                                                             IdentityProviderProperties properties,
                                                             Environment environment) {
        return new DispatcherInvitationAcceptance(issuer, internalBaseUrl(properties, environment),
                properties.getTenantConsole().getFirstOwnerAcceptPath());
    }

    @Bean
    HostedLoginController identityProviderHostedLogin(IdentityAccountCapability identities,
                                                      PasswordCapability passwords,
                                                      TenantAccessCapability tenants,
                                                      InvitationAcceptance invitations,
                                                      IdentityProviderProperties properties,
                                                      EncryptedStateStore store,
                                                      LoginSessionRevocations revocations) {
        return new HostedLoginController(identities, passwords, tenants, invitations, properties, store,
                revocations, CLOCK);
    }

    @Bean
    HostedSetupController identityProviderHostedSetup(IdentityAccountCapability identities,
                                                      PasswordCapability passwords,
                                                      IdentityProviderProperties properties) {
        return new HostedSetupController(identities, passwords, properties, CLOCK);
    }

    @Bean
    HostedLoginPages identityProviderHostedLoginPages() {
        return new HostedLoginPages();
    }

    @Bean
    BffSessionStore identityProviderBffSessions(EncryptedStateStore store, IdentityProviderProperties properties) {
        return new BffSessionStore(store, properties.getSession().getIdleTimeout(), CLOCK);
    }

    @Bean
    BffTokenClient identityProviderBffTokens(IdentityProviderProperties properties, TenantConsoleClient client,
                                             JWKSource<SecurityContext> jwkSource, Environment environment) {
        return new BffTokenClient(properties.requireIssuerOrigin(), client.clientId(), client.clientSecret(),
                internalBaseUrl(properties, environment), jwkSource);
    }

    @Bean
    BffTokenRefresher identityProviderBffRefresher(BffSessionStore sessions, BffTokenClient tokens) {
        return new BffTokenRefresher(sessions, tokens, CLOCK);
    }

    @Bean
    BffController identityProviderBff(BffSessionStore sessions, BffTokenClient tokens, BffTokenRefresher refresher,
                                      TenantAccessCapability tenants, LoginSessionRevocations revocations,
                                      ContextSwitchIntents switchIntents, IdentityProviderProperties properties) {
        return new BffController(sessions, tokens, refresher, tenants, revocations, switchIntents, properties, CLOCK);
    }

    /** Runs before the Dispatcher's authentication step ({@code SecurityContextFilter}, order -100). */
    @Bean
    FilterRegistrationBean<BffSessionRelayFilter> identityProviderBffRelay(BffSessionStore sessions,
                                                                          BffTokenRefresher refresher) {
        FilterRegistrationBean<BffSessionRelayFilter> registration =
                new FilterRegistrationBean<>(new BffSessionRelayFilter(sessions, refresher));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(-110);
        registration.setName("bffSessionRelayFilter");
        return registration;
    }

    static void protocolHeaders(HeadersConfigurer<HttpSecurity> headers) {
        headers.contentSecurityPolicy(csp -> csp.policyDirectives(PROTOCOL_POLICY))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny);
    }

    private static Supplier<String> internalBaseUrl(IdentityProviderProperties properties, Environment environment) {
        return () -> {
            String configured = properties.getInternalBaseUrl();
            if (configured != null && !configured.isBlank()) {
                return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
            }
            String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
            return "http://127.0.0.1:" + port;
        };
    }

    /**
     * Filters shared by both chains. They are deliberately not {@code Filter} beans: Spring Boot
     * would also register such beans for every path, {@code /api/**} included.
     */
    record ProtocolLimits(ProtocolRateLimitFilter filter, HostedLoginSessionFilter loginSession) {
    }

    /** Credentials of the tenant console client, shared by the authorization server and the BFF. */
    record TenantConsoleClient(String clientId, String clientSecret) {
        @Override
        public String toString() {
            return "TenantConsoleClient{clientId=" + clientId + "}";
        }
    }
}
