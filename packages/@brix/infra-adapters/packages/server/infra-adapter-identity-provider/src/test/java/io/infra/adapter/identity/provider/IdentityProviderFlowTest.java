package io.infra.adapter.identity.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.infra.adapter.identity.provider.bff.BffCookies;
import io.infra.adapter.identity.provider.bff.BffSession;
import io.infra.adapter.identity.provider.bff.BffSessionStore;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The tenant console login loop over real HTTP: BFF login, hosted login (identifier, password,
 * TOTP), authorization code + PKCE, BFF callback, session, the {@code /api} relay and logout.
 * Covers identity-auth IA-1 (no credential-for-token endpoint is used), IA-7 and root S-20.
 */
@SpringBootTest(classes = IdentityProviderFlowTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class IdentityProviderFlowTest {

    private static final int PORT = freePort();
    private static final String ISSUER = "http://localhost:" + PORT;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicInteger CODES = new AtomicInteger(100_000);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("brix.identity-provider.enabled", () -> "true");
        registry.add("brix.identity-provider.issuer", () -> ISSUER);
    }

    @Autowired
    TestCapabilities.InMemoryStateStore stateStore;

    @Autowired
    TestCapabilities.FakePasswords passwords;

    @Autowired
    TestCapabilities.FakeTenants tenants;

    @Autowired
    BffSessionStore sessions;

    @Autowired
    IdentityProviderAutoConfiguration.TenantConsoleClient client;

    @BeforeEach
    void reset() {
        tenants.active = true;
    }

    @Test
    void staffLogInThroughTheHostedLoginAndTheBrowserNeverHoldsAToken() {
        Browser browser = new Browser();

        Browser.Response start = browser.get(ISSUER + "/bff/login?returnTo=/tenant/members");
        assertThat(start.status()).isEqualTo(302);
        Map<String, String> authorize = query(start.location());
        assertThat(start.location()).startsWith(ISSUER + "/oauth2/authorize?");
        assertThat(authorize).containsEntry("response_type", "code")
                .containsEntry("client_id", "tenant-console")
                .containsEntry("redirect_uri", ISSUER + "/bff/callback")
                .containsEntry("code_challenge_method", "S256")
                .containsKeys("state", "nonce", "code_challenge");
        assertThat(browser.setCookieLines.get(BffCookies.LOGIN_BINDING))
                .contains("Secure").contains("HttpOnly").contains("SameSite=Lax").contains("Path=/");

        Browser.Response toLogin = browser.get(start.location(), "Accept", "text/html");
        assertThat(toLogin.status()).isEqualTo(302);
        assertThat(toLogin.location()).isEqualTo(ISSUER + "/auth/login");
        assertThat(browser.setCookieLines.get(IdentityProviderAutoConfiguration.LOGIN_SESSION_COOKIE))
                .contains("Secure").contains("HttpOnly").containsIgnoringCase("SameSite=Lax");

        String continueUrl = completeHostedLogin(browser, TestCapabilities.STAFF_EMAIL, TestCapabilities.STAFF_PASSWORD);
        assertThat(continueUrl).isEqualTo(start.location());

        Browser.Response code = browser.get(continueUrl, "Accept", "text/html");
        assertThat(code.status()).isEqualTo(302);
        assertThat(code.location()).startsWith(ISSUER + "/bff/callback?code=");
        assertThat(query(code.location())).containsEntry("state", authorize.get("state"));

        Browser.Response landed = browser.get(code.location());
        assertThat(landed.status()).isEqualTo(302);
        assertThat(landed.location()).isEqualTo(ISSUER + "/tenant/members");
        assertThat(browser.setCookieLines.get(BffCookies.SESSION))
                .startsWith(BffCookies.SESSION + "=").contains("Secure").contains("HttpOnly")
                .contains("SameSite=Strict").contains("Path=/").doesNotContain("Domain");
        assertThat(browser.cookies).doesNotContainKey(BffCookies.LOGIN_BINDING);

        Browser.Response session = browser.get(ISSUER + "/bff/session");
        JsonNode body = session.json();
        assertThat(body.get("authenticated").asBoolean()).isTrue();
        assertThat(body.at("/identity/id").asString()).isEqualTo(String.valueOf(TestCapabilities.STAFF_ID));
        assertThat(body.at("/identity/email").asString()).isEqualTo(TestCapabilities.STAFF_EMAIL);
        assertThat(body.at("/context/kind").asString()).isEqualTo("actor");
        assertThat(body.at("/context/tenantId").asString()).isEqualTo(String.valueOf(TestCapabilities.TENANT_ID));
        assertThat(body.at("/context/memberType").asString()).isEqualTo("MEMBER");
        assertThat(body.at("/authentication/acr").asString()).isEqualTo("brix:aal2");
        assertThat(body.get("permissions").toString())
                .as("effective permissions come from the Dispatcher's answer for the session's token")
                .isEqualTo("[\"app-booking:template:read\",\"tenant:profile:read\"]");
        assertThat(session.header("Content-Security-Policy")).contains("frame-ancestors 'none'");
        assertThat(session.header("Referrer-Policy")).isEqualTo("no-referrer");
        String csrf = body.get("csrfToken").asString();

        Map<String, Object> whoami = apiClaims(browser.get(ISSUER + "/api/test/whoami"));
        assertThat(whoami).containsEntry("sub", String.valueOf(TestCapabilities.STAFF_ID))
                .containsEntry("aud", "brix-actor").containsEntry("acr", "brix:aal2")
                .containsEntry("cid", TestCapabilities.CONTEXT_ID).doesNotContainKey("permissions");
        assertThat(browser.post(ISSUER + "/api/test/echo", "{}").status()).isEqualTo(403);
        assertThat(browser.post(ISSUER + "/api/test/echo", "{}", "X-Brix-CSRF", "wrong").status()).isEqualTo(403);
        assertThat(browser.post(ISSUER + "/api/test/echo", "{}", "X-Brix-CSRF", csrf).status()).isEqualTo(200);

        BffSession stored = sessions.find(browser.cookies.get(BffCookies.SESSION)).orElseThrow();
        assertThat(stateStore.dump()).doesNotContain(stored.accessToken()).doesNotContain(stored.refreshToken())
                .doesNotContain(browser.cookies.get(BffCookies.SESSION));
        // S-20: nothing the browser received carries an access, refresh or ID token.
        for (String response : browser.received) {
            assertThat(response).doesNotContain(stored.accessToken()).doesNotContain(stored.refreshToken())
                    .doesNotContain(stored.idToken()).doesNotContain("eyJ");
        }

        assertThat(browser.post(ISSUER + "/bff/logout", "{}").status()).isEqualTo(403);
        assertThat(browser.post(ISSUER + "/bff/logout", "{}", "X-Brix-CSRF", csrf).status()).isEqualTo(200);
        assertThat(browser.get(ISSUER + "/bff/session").json().get("authenticated").asBoolean()).isFalse();
        assertThat(apiClaims(browser.get(ISSUER + "/api/test/whoami"))).isEmpty();
        assertThat(tokenRequest("grant_type=refresh_token&refresh_token=" + stored.refreshToken()).statusCode())
                .isEqualTo(400);
        Browser.Response again = browser.get(start.location(), "Accept", "text/html");
        assertThat(again.location()).as("logout also ends the login session").isEqualTo(ISSUER + "/auth/login");
    }

    @Test
    void unknownAccountsCannotBeToldApartFromKnownOnes() {
        Browser known = new Browser();
        Browser unknown = new Browser();
        String knownCsrf = startHostedLogin(known);
        String unknownCsrf = startHostedLogin(unknown);

        Browser.Response knownIdentify = identify(known, knownCsrf, TestCapabilities.STAFF_EMAIL);
        Browser.Response unknownIdentify = identify(unknown, unknownCsrf, "nobody@example.test");
        assertThat(unknownIdentify.status()).isEqualTo(knownIdentify.status()).isEqualTo(200);
        assertThat(unknownIdentify.body()).isEqualTo(knownIdentify.body());

        int before = passwords.verifications.get();
        Browser.Response wrongPassword = password(known, knownCsrf, "not the password");
        Browser.Response unknownPassword = password(unknown, unknownCsrf, "not the password");
        assertThat(unknownPassword.status()).isEqualTo(wrongPassword.status()).isEqualTo(401);
        assertThat(unknownPassword.body()).isEqualTo(wrongPassword.body());
        assertThat(passwords.verifications.get() - before).as("both paths hash the password").isEqualTo(2);
    }

    @Test
    void failedAttemptsAreThrottledForThatAccountAndSourceOnly() {
        Browser browser = new Browser();
        String csrf = startHostedLogin(browser);
        identify(browser, csrf, "throttled@example.test");
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(password(browser, csrf, "guess-" + attempt).status()).isEqualTo(401);
        }
        assertThat(password(browser, csrf, "guess-5").status()).isEqualTo(429);

        Browser other = new Browser();
        String otherCsrf = startHostedLogin(other);
        identify(other, otherCsrf, TestCapabilities.STAFF_EMAIL);
        assertThat(password(other, otherCsrf, TestCapabilities.STAFF_PASSWORD).status())
                .as("another account from the same source is not locked").isEqualTo(200);
    }

    @Test
    void authorizationRequestsNeedPkceAndTheRegisteredRedirectUri() {
        Browser browser = new Browser();
        Browser.Response noPkce = browser.get(ISSUER + "/oauth2/authorize?response_type=code&client_id=tenant-console"
                + "&scope=openid&state=s1&redirect_uri=" + ISSUER + "/bff/callback", "Accept", "text/html");
        assertThat(noPkce.status()).isEqualTo(302);
        assertThat(noPkce.location()).startsWith(ISSUER + "/bff/callback?error=invalid_request");

        Browser.Response foreignRedirect = browser.get(ISSUER + "/oauth2/authorize?response_type=code"
                + "&client_id=tenant-console&scope=openid&state=s2&code_challenge=abc&code_challenge_method=S256"
                + "&redirect_uri=https://attacker.example/callback", "Accept", "text/html");
        assertThat(foreignRedirect.status()).isEqualTo(400);
        assertThat(foreignRedirect.location()).isNull();

        assertThat(browser.get(ISSUER + "/oauth2/unknown").status()).isEqualTo(403);
        assertThat(browser.get(ISSUER + "/.well-known/openid-configuration").json().get("issuer").asString())
                .isEqualTo(ISSUER);
    }

    @Test
    void theCallbackOnlyCompletesInTheBrowserThatStartedTheLogin() {
        Browser victim = new Browser();
        Browser.Response start = victim.get(ISSUER + "/bff/login");
        victim.get(start.location(), "Accept", "text/html");
        String continueUrl = completeHostedLogin(victim, TestCapabilities.STAFF_EMAIL, TestCapabilities.STAFF_PASSWORD);
        String callback = victim.get(continueUrl, "Accept", "text/html").location();

        Browser attacker = new Browser();
        Browser.Response replayed = attacker.get(callback);
        assertThat(replayed.location()).isEqualTo(ISSUER + "/auth/error?reason=login_expired");
        assertThat(attacker.cookies).doesNotContainKey(BffCookies.SESSION);
        assertThat(victim.get(callback).location()).as("a state is used once")
                .isEqualTo(ISSUER + "/auth/error?reason=login_expired");
    }

    @Test
    void expiredAccessTokensAreRenewedUntilTheContextEnds() {
        Browser browser = loggedIn();
        String sessionId = browser.cookies.get(BffCookies.SESSION);
        BffSession original = sessions.find(sessionId).orElseThrow();
        String firstJti = (String) apiClaims(browser.get(ISSUER + "/api/test/whoami")).get("jti");

        sessions.save(sessionId, expire(original));
        Map<String, Object> renewed = apiClaims(browser.get(ISSUER + "/api/test/whoami"));
        assertThat(renewed.get("jti")).isNotEqualTo(firstJti);
        assertThat(renewed).containsEntry("sid", original.sid());
        assertThat(tokenRequest("grant_type=refresh_token&refresh_token=" + original.refreshToken()).statusCode())
                .as("a rotated refresh token is no longer accepted").isEqualTo(400);

        tenants.active = false;
        sessions.save(sessionId, expire(sessions.find(sessionId).orElseThrow()));
        assertThat(apiClaims(browser.get(ISSUER + "/api/test/whoami"))).isEmpty();
        assertThat(sessions.find(sessionId)).isEmpty();
        assertThat(browser.cookies).doesNotContainKey(BffCookies.SESSION);
    }

    @Test
    void returnPathsStayInsideTheConsole() {
        for (String target : new String[] {"https://attacker.example/", "//attacker.example", "/\\attacker.example",
                "/platform/tenants", "/tenant/../platform"}) {
            Browser browser = new Browser();
            Browser.Response start = browser.get(ISSUER + "/bff/login?returnTo=" + target.replace("\\", "%5C"));
            browser.get(start.location(), "Accept", "text/html");
            String continueUrl = completeHostedLogin(browser, TestCapabilities.STAFF_EMAIL,
                    TestCapabilities.STAFF_PASSWORD);
            String callback = browser.get(continueUrl, "Accept", "text/html").location();
            assertThat(browser.get(callback).location()).as(target).isEqualTo(ISSUER + "/tenant");
        }
    }

    @Test
    void hostedLoginPagesCarryAFreshNonceAndAStrictPolicy() {
        Browser browser = new Browser();
        Browser.Response first = browser.get(ISSUER + "/auth/login");
        Browser.Response second = browser.get(ISSUER + "/auth/error?reason=login_expired");

        assertThat(first.status()).isEqualTo(200);
        String policy = first.header("Content-Security-Policy");
        String nonce = policy.replaceAll(".*'nonce-([^']+)'.*", "$1");
        assertThat(first.body()).contains("content=\"" + nonce + "\"").doesNotContain("__BRIX_CSP_NONCE__");
        assertThat(policy).contains("script-src 'self'").contains("frame-ancestors 'none'")
                .contains("object-src 'none'").doesNotContain("unsafe-inline").doesNotContain("unsafe-eval");
        assertThat(second.header("Content-Security-Policy")).doesNotContain(nonce);
        assertThat(first.header("Cache-Control")).contains("no-store");
        assertThat(first.header("Referrer-Policy")).isEqualTo("no-referrer");

        assertThat(browser.get(ISSUER + "/auth/assets/app-test.js").status()).isEqualTo(200);
        assertThat(browser.get(ISSUER + "/auth/assets/..%2Findex.html").status()).isIn(400, 404);
        assertThat(browser.get(ISSUER + "/auth/assets/missing.js").status()).isEqualTo(404);
    }

    @Test
    void theApiIsNotPartOfTheProtocolSurfaceChain() {
        Browser.Response api = new Browser().get(ISSUER + "/api/test/whoami");

        assertThat(api.status()).isEqualTo(200);
        assertThat(api.header("Content-Security-Policy")).isNull();
        assertThat(apiClaims(api)).isEmpty();
    }

    private Browser loggedIn() {
        Browser browser = new Browser();
        Browser.Response start = browser.get(ISSUER + "/bff/login");
        browser.get(start.location(), "Accept", "text/html");
        String continueUrl = completeHostedLogin(browser, TestCapabilities.STAFF_EMAIL, TestCapabilities.STAFF_PASSWORD);
        browser.get(browser.get(continueUrl, "Accept", "text/html").location());
        assertThat(browser.cookies).containsKey(BffCookies.SESSION);
        return browser;
    }

    private String completeHostedLogin(Browser browser, String email, String password) {
        String csrf = startHostedLogin(browser);
        assertThat(identify(browser, csrf, email).json().get("step").asString()).isEqualTo("password");
        assertThat(password(browser, csrf, password).json().get("step").asString()).isEqualTo("totp");
        Browser.Response done = browser.post(ISSUER + "/auth/api/totp",
                "{\"code\":\"" + CODES.incrementAndGet() + "\"}", "X-CSRF-TOKEN", csrf);
        assertThat(done.status()).isEqualTo(200);
        assertThat(done.json().get("step").asString()).isEqualTo("done");
        return done.json().get("continueUrl").asString();
    }

    private static String startHostedLogin(Browser browser) {
        Browser.Response state = browser.get(ISSUER + "/auth/api/state");
        assertThat(state.status()).isEqualTo(200);
        assertThat(state.header("Content-Security-Policy")).contains("frame-ancestors 'none'");
        return state.json().get("csrfToken").asString();
    }

    private static Browser.Response identify(Browser browser, String csrf, String email) {
        return browser.post(ISSUER + "/auth/api/identify", "{\"identifier\":\"" + email + "\"}", "X-CSRF-TOKEN", csrf);
    }

    private static Browser.Response password(Browser browser, String csrf, String password) {
        return browser.post(ISSUER + "/auth/api/password", "{\"password\":\"" + password + "\"}", "X-CSRF-TOKEN", csrf);
    }

    private HttpResponse<String> tokenRequest(String form) {
        try {
            String basic = Base64.getEncoder().encodeToString(
                    (client.clientId() + ":" + client.clientSecret()).getBytes(StandardCharsets.UTF_8));
            return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(ISSUER + "/oauth2/token"))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The same session with an access token that has run out. */
    private static BffSession expire(BffSession s) {
        return new BffSession(s.csrfToken(), s.createdAt(), s.absoluteExpiresAt(), s.lastSeenAt(), s.accessToken(), 0L,
                s.refreshToken(), s.idToken(), s.subject(), s.email(), s.username(), s.contextKind(), s.contextId(),
                s.tenantId(), s.tenantName(), s.memberType(), s.acr(), s.amr(), s.authTime(), s.sid());
    }

    private static Map<String, Object> apiClaims(Browser.Response response) {
        assertThat(response.status()).isEqualTo(200);
        return JSON.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
    }

    private static Map<String, String> query(String url) {
        Map<String, String> result = new LinkedHashMap<>();
        String query = URI.create(url).getRawQuery();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            result.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return result;
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(TestCapabilities.class)
    static class TestApplication {

        @Bean
        ApiProbe apiProbe() {
            return new ApiProbe();
        }
    }

    /** Stands in for the Dispatcher: shows which bearer token reached {@code /api/**}. */
    @RestController
    static class ApiProbe {

        @GetMapping("/api/test/whoami")
        Map<String, Object> whoami(@RequestHeader(value = "Authorization", required = false) String authorization) {
            return claims(authorization);
        }

        /** Answers like the tenant Owner's session endpoint, for the bearer the BFF sends. */
        @GetMapping("/api/tenant/session")
        Map<String, Object> tenantSession(@RequestHeader(value = "Authorization", required = false) String authorization) {
            Map<String, Object> claims = claims(authorization);
            if (!"MEMBER".equals(claims.get("mtype"))) {
                throw new IllegalStateException("not a tenant member");
            }
            return Map.of("tenantId", claims.get("tenant_id"),
                    "permissions", java.util.List.of("tenant:profile:read", "app-booking:template:read"));
        }

        @PostMapping("/api/test/echo")
        Map<String, Object> echo(@RequestHeader(value = "Authorization", required = false) String authorization) {
            return claims(authorization);
        }

        private static Map<String, Object> claims(String authorization) {
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return Map.of();
            }
            String payload = authorization.substring(7).split("\\.")[1];
            return JSON.readValue(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8),
                    new TypeReference<Map<String, Object>>() {});
        }
    }
}
