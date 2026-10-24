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
import java.time.Instant;
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
    TestCapabilities.FakeIdentities identities;

    @Autowired
    BffSessionStore sessions;

    @Autowired
    IdentityProviderAutoConfiguration.TenantConsoleClient client;

    @Autowired
    ApiProbe api;

    @BeforeEach
    void reset() {
        tenants.active = true;
        tenants.invitationAccepted = false;
        identities.status = "ACTIVE";
        identities.lockedUntil = null;
        identities.inviteeStatus = "PENDING_SETUP";
        identities.inviteePasswordHash = null;
        identities.inviteeEnrolled = false;
        api.acceptances.clear();
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
    void aLockSetByAnotherEntryDoesNotKeepStaffOut() {
        identities.status = "LOCKED";
        identities.lockedUntil = Instant.now().plusSeconds(600);

        Browser browser = loggedIn();
        assertThat(identities.status).as("the lock stays in place for the entry that set it").isEqualTo("LOCKED");

        String sessionId = browser.cookies.get(BffCookies.SESSION);
        sessions.save(sessionId, expire(sessions.find(sessionId).orElseThrow()));
        assertThat(apiClaims(browser.get(ISSUER + "/api/test/whoami"))).as("tokens are still renewed")
                .containsEntry("sub", String.valueOf(TestCapabilities.STAFF_ID));
    }

    @Test
    void anExpiredLockIsClearedOnlyAfterBothFactors() {
        identities.status = "LOCKED";
        identities.lockedUntil = Instant.now().minusSeconds(60);
        Browser browser = new Browser();
        String csrf = startHostedLogin(browser);
        identify(browser, csrf, TestCapabilities.STAFF_EMAIL);

        assertThat(password(browser, csrf, "not the password").status()).isEqualTo(401);
        assertThat(password(browser, csrf, TestCapabilities.STAFF_PASSWORD).json().get("step").asString())
                .isEqualTo("totp");
        assertThat(identities.status).as("nothing changes before the second factor").isEqualTo("LOCKED");

        Browser.Response done = browser.post(ISSUER + "/auth/api/totp",
                "{\"code\":\"" + CODES.incrementAndGet() + "\"}", "X-CSRF-TOKEN", csrf);
        assertThat(done.json().get("step").asString()).isEqualTo("done");
        assertThat(identities.status).isEqualTo("ACTIVE");
    }

    @Test
    void identitiesThatAreDisabledOrNotSetUpNeitherSignInNorRenewTokens() {
        Browser browser = loggedIn();
        String sessionId = browser.cookies.get(BffCookies.SESSION);
        identities.status = "DISABLED";
        sessions.save(sessionId, expire(sessions.find(sessionId).orElseThrow()));
        assertThat(apiClaims(browser.get(ISSUER + "/api/test/whoami"))).isEmpty();
        assertThat(sessions.find(sessionId)).isEmpty();

        for (String status : new String[] {"DISABLED", "PENDING_SETUP"}) {
            identities.status = status;
            Browser other = new Browser();
            String csrf = startHostedLogin(other);
            identify(other, csrf, TestCapabilities.STAFF_EMAIL);
            Browser.Response refused = password(other, csrf, TestCapabilities.STAFF_PASSWORD);
            assertThat(refused.status()).as(status).isEqualTo(401);
            assertThat(refused.json().get("error").asString()).as("the same answer as a wrong password")
                    .isEqualTo("invalid_credentials");
        }
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
        assertThat(sessions.find(sessionId).orElseThrow().refreshToken()).isNotEqualTo(original.refreshToken());

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
        for (String page : new String[] {"/auth/setup", "/auth/invitation"}) {
            Browser.Response hosted = browser.get(ISSUER + page);
            assertThat(hosted.status()).as(page).isEqualTo(200);
            assertThat(hosted.header("Referrer-Policy")).as(page).isEqualTo("no-referrer");
            assertThat(hosted.header("Content-Security-Policy")).as(page).contains("frame-ancestors 'none'");
        }

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

    @Test
    void staffOfSeveralTenantsChooseWithOneTimeTicketsAndNeverSeeContextIds() {
        Browser browser = new Browser();
        Browser.Response start = browser.get(ISSUER + "/bff/login");
        browser.get(start.location(), "Accept", "text/html");
        String csrf = startHostedLogin(browser);
        identify(browser, csrf, TestCapabilities.MULTI_EMAIL);
        password(browser, csrf, TestCapabilities.STAFF_PASSWORD);
        Browser.Response selection = browser.post(ISSUER + "/auth/api/totp",
                "{\"code\":\"" + CODES.incrementAndGet() + "\"}", "X-CSRF-TOKEN", csrf);

        assertThat(selection.json().get("step").asString()).isEqualTo("select-tenant");
        assertThat(selection.json().get("options")).hasSize(2);
        // §9.3 (5): the page sees one-time tickets, never the stable contextIds.
        assertThat(selection.body()).doesNotContain(TestCapabilities.MULTI_CONTEXT_A)
                .doesNotContain(TestCapabilities.MULTI_CONTEXT_B).doesNotContain("contextId");
        String staleTicket = ticketFor(selection, "Hospital");

        Browser.Response reloaded = browser.get(ISSUER + "/auth/api/state");
        assertThat(select(browser, csrf, staleTicket).status())
                .as("a reload starts a new ticket generation").isEqualTo(401);
        Browser intruder = new Browser();
        String intruderCsrf = startHostedLogin(intruder);
        identify(intruder, intruderCsrf, TestCapabilities.STAFF_EMAIL);
        password(intruder, intruderCsrf, TestCapabilities.STAFF_PASSWORD);
        intruder.post(ISSUER + "/auth/api/totp", "{\"code\":\"" + CODES.incrementAndGet() + "\"}",
                "X-CSRF-TOKEN", intruderCsrf);
        String ticket = ticketFor(reloaded, "Hospital");

        Browser.Response done = select(browser, csrf, ticket);
        assertThat(done.json().get("step").asString()).isEqualTo("done");
        assertThat(select(browser, csrf, ticket).status()).as("a ticket works once").isEqualTo(409);
        browser.get(browser.get(done.json().get("continueUrl").asString(), "Accept", "text/html").location());

        JsonNode session = browser.get(ISSUER + "/bff/session").json();
        assertThat(session.at("/context/tenantId").asString()).isEqualTo(String.valueOf(TestCapabilities.OTHER_TENANT_ID));
        assertThat(session.at("/context/memberType").asString()).isEqualTo("OWNER");
        assertThat(session.get("contexts")).hasSize(2);
        assertThat(session.get("contexts").toString()).doesNotContain(TestCapabilities.FOREIGN_CONTEXT);
    }

    @Test
    void aSwitchSilentlyReauthorizesForAnotherContextOfTheSameIdentity() {
        Browser browser = loggedInAs(TestCapabilities.MULTI_EMAIL, "Clinic");
        String previousSession = browser.cookies.get(BffCookies.SESSION);
        BffSession before = sessions.find(previousSession).orElseThrow();
        String csrf = browser.get(ISSUER + "/bff/session").json().get("csrfToken").asString();
        String body = "{\"contextId\":\"" + TestCapabilities.MULTI_CONTEXT_B + "\",\"returnTo\":\"/tenant/members\"}";

        assertThat(browser.post(ISSUER + "/bff/switch", body).status()).as("CSRF header required").isEqualTo(403);
        Browser.Response started = browser.post(ISSUER + "/bff/switch", body, "X-Brix-CSRF", csrf);
        String authorize = started.json().get("redirect").asString();
        assertThat(query(authorize)).containsEntry("prompt", "none")
                .containsEntry("brix_context", TestCapabilities.MULTI_CONTEXT_B);

        Browser.Response code = browser.get(authorize, "Accept", "text/html");
        assertThat(code.location()).startsWith(ISSUER + "/bff/callback?code=");
        assertThat(browser.get(code.location()).location()).isEqualTo(ISSUER + "/tenant/members");

        assertThat(browser.cookies.get(BffCookies.SESSION)).isNotEqualTo(previousSession);
        assertThat(sessions.find(previousSession)).isEmpty();
        JsonNode session = browser.get(ISSUER + "/bff/session").json();
        assertThat(session.at("/context/tenantId").asString()).isEqualTo(String.valueOf(TestCapabilities.OTHER_TENANT_ID));
        assertThat(session.at("/context/memberType").asString()).isEqualTo("OWNER");
        assertThat(session.get("permissions").toString())
                .as("permissions are those of the new context only (BRX-TENANT-AUTHZ-009)")
                .contains("tenant:member:invite").doesNotContain("app-booking:template:read");
        BffSession after = sessions.find(browser.cookies.get(BffCookies.SESSION)).orElseThrow();
        assertThat(after.sid()).as("the same login session, a new token family").isEqualTo(before.sid());
        assertThat(tokenRequest("grant_type=refresh_token&refresh_token=" + before.refreshToken()).statusCode())
                .as("the previous family ends with the switch").isEqualTo(400);
        assertThat(apiClaims(browser.get(ISSUER + "/api/test/whoami")))
                .containsEntry("cid", TestCapabilities.MULTI_CONTEXT_B).containsEntry("mtype", "OWNER");
    }

    @Test
    void contextsOfAnotherIdentityCanNeverBeSwitchedTo() {
        Browser browser = loggedInAs(TestCapabilities.MULTI_EMAIL, "Clinic");
        String csrf = browser.get(ISSUER + "/bff/session").json().get("csrfToken").asString();

        Browser.Response denied = browser.post(ISSUER + "/bff/switch",
                "{\"contextId\":\"" + TestCapabilities.FOREIGN_CONTEXT + "\"}", "X-Brix-CSRF", csrf);
        assertThat(denied.status()).isEqualTo(403);
        assertThat(denied.json().get("error").asString()).isEqualTo("context_not_available");

        // A hand-made authorization request with a foreign target is refused by the server too (T-26).
        String forged = browser.get(ISSUER + "/bff/login").location()
                + "&prompt=none&brix_context=" + TestCapabilities.FOREIGN_CONTEXT;
        Browser.Response refused = browser.get(forged, "Accept", "text/html");
        assertThat(refused.location()).startsWith(ISSUER + "/bff/callback?error=access_denied");
    }

    @Test
    void anotherSiteCannotSwitchTheContextOfALoginSessionWithACraftedLink() {
        Browser browser = loggedInAs(TestCapabilities.MULTI_EMAIL, "Clinic");
        // The login session cookie goes along with top-level navigations from any site, so a
        // planted link to the identity's own other context must not switch anything.
        String crafted = browser.get(ISSUER + "/bff/login").location()
                + "&prompt=none&brix_context=" + TestCapabilities.MULTI_CONTEXT_B;
        assertThat(browser.get(crafted, "Accept", "text/html").location())
                .startsWith(ISSUER + "/bff/callback?error=access_denied");

        Browser.Response code = browser.get(browser.get(ISSUER + "/bff/login").location(), "Accept", "text/html");
        browser.get(code.location());
        assertThat(browser.get(ISSUER + "/bff/session").json().at("/context/tenantId").asString())
                .as("the login session kept its context").isEqualTo(String.valueOf(TestCapabilities.TENANT_ID));
    }

    @Test
    void aSilentSwitchWithoutALoginSessionGoesThroughLoginAgain() {
        Browser browser = loggedInAs(TestCapabilities.MULTI_EMAIL, "Clinic");
        String loginCsrf = browser.get(ISSUER + "/auth/api/state").json().get("csrfToken").asString();
        browser.post(ISSUER + "/auth/api/sign-out", "{}", "X-CSRF-TOKEN", loginCsrf);
        String csrf = browser.get(ISSUER + "/bff/session").json().get("csrfToken").asString();

        Browser.Response started = browser.post(ISSUER + "/bff/switch",
                "{\"contextId\":\"" + TestCapabilities.MULTI_CONTEXT_B + "\"}", "X-Brix-CSRF", csrf);
        Browser.Response noLogin = browser.get(started.json().get("redirect").asString(), "Accept", "text/html");
        assertThat(noLogin.location()).as("prompt=none never shows a login page")
                .startsWith(ISSUER + "/bff/callback?error=login_required");
        assertThat(browser.get(noLogin.location()).location()).isEqualTo(ISSUER + "/bff/login?returnTo=%2Ftenant");
    }

    @Test
    void reusingARotatedRefreshTokenEndsTheWholeFamilyAndTheLoginSession() {
        Browser browser = loggedIn();
        String sessionId = browser.cookies.get(BffCookies.SESSION);
        BffSession original = sessions.find(sessionId).orElseThrow();
        sessions.save(sessionId, expire(original));
        apiClaims(browser.get(ISSUER + "/api/test/whoami"));
        String current = sessions.find(sessionId).orElseThrow().refreshToken();

        assertThat(tokenRequest("grant_type=refresh_token&refresh_token=" + original.refreshToken()).statusCode())
                .as("the rotated-out token is refused").isEqualTo(400);
        assertThat(tokenRequest("grant_type=refresh_token&refresh_token=" + current).statusCode())
                .as("and the whole family with it").isEqualTo(400);
        Browser.Response again = browser.get(browser.get(ISSUER + "/bff/login").location(), "Accept", "text/html");
        assertThat(again.location()).as("the login session is revoked: credentials are needed again")
                .isEqualTo(ISSUER + "/auth/login");
    }

    private String ticketFor(Browser.Response selection, String tenantName) {
        for (JsonNode option : selection.json().get("options")) {
            if (tenantName.equals(option.get("tenantName").asString())) {
                return option.get("ticket").asString();
            }
        }
        throw new AssertionError("no option for " + tenantName);
    }

    private static Browser.Response select(Browser browser, String csrf, String ticket) {
        return browser.post(ISSUER + "/auth/api/select", "{\"ticket\":\"" + ticket + "\"}", "X-CSRF-TOKEN", csrf);
    }

    private Browser loggedInAs(String email, String tenantName) {
        Browser browser = new Browser();
        Browser.Response start = browser.get(ISSUER + "/bff/login");
        browser.get(start.location(), "Accept", "text/html");
        String csrf = startHostedLogin(browser);
        identify(browser, csrf, email);
        password(browser, csrf, TestCapabilities.STAFF_PASSWORD);
        Browser.Response step = browser.post(ISSUER + "/auth/api/totp",
                "{\"code\":\"" + CODES.incrementAndGet() + "\"}", "X-CSRF-TOKEN", csrf);
        if ("select-tenant".equals(step.json().get("step").asString())) {
            step = select(browser, csrf, ticketFor(step, tenantName));
        }
        String continueUrl = step.json().get("continueUrl").asString();
        browser.get(browser.get(continueUrl, "Accept", "text/html").location());
        assertThat(browser.cookies).containsKey(BffCookies.SESSION);
        return browser;
    }

    /**
     * identity-auth BRX-IA-ONBOARD-002/003 and BRX-IA-CRED-001: the invitee sets a password and
     * binds TOTP on the hosted setup page; the token is sent once and never comes back.
     */
    @Test
    void aFirstOwnerInviteeSetsUpTheAccountOnTheHostedSetupPage() {
        Browser browser = new Browser();
        Browser.Response state = browser.get(ISSUER + "/auth/api/setup/state");
        assertThat(state.json().get("step").asString()).isEqualTo("setup-start");
        assertThat(state.header("Referrer-Policy")).isEqualTo("no-referrer");
        String csrf = state.json().get("csrfToken").asString();

        assertThat(browser.post(ISSUER + "/auth/api/setup/start", "{\"token\":\"" + TestCapabilities.SETUP_TOKEN + "\"}")
                .status()).as("CSRF is required").isEqualTo(403);
        Browser.Response wrong = browser.post(ISSUER + "/auth/api/setup/start", "{\"token\":\"not-a-setup-token\"}",
                "X-CSRF-TOKEN", csrf);
        assertThat(wrong.status()).isEqualTo(404);
        assertThat(wrong.json().get("step").asString()).isEqualTo("setup-invalid");

        Browser.Response started = browser.post(ISSUER + "/auth/api/setup/start",
                "{\"token\":\"" + TestCapabilities.SETUP_TOKEN + "\"}", "X-CSRF-TOKEN", csrf);
        assertThat(started.status()).isEqualTo(200);
        assertThat(started.json().get("step").asString()).isEqualTo("setup-password");
        assertThat(started.json().get("email").asString()).isEqualTo(TestCapabilities.INVITEE_EMAIL);

        assertThat(setupPassword(browser, csrf, "password1").json().get("error").asString())
                .isEqualTo("password_breached");
        assertThat(setupPassword(browser, csrf, "short").json().get("error").asString())
                .isEqualTo("password_too_short");
        assertThat(setupPassword(browser, csrf, "invitee clinic 2026").json().get("error").asString())
                .isEqualTo("password_contains_account_name");
        Browser.Response enrollment = setupPassword(browser, csrf, "violet kettle harbour");
        assertThat(enrollment.status()).isEqualTo(200);
        assertThat(enrollment.json().get("step").asString()).isEqualTo("setup-totp");
        String otpauth = enrollment.json().get("otpauthUri").asString();
        assertThat(otpauth).startsWith("otpauth://totp/");
        assertThat(browser.get(ISSUER + "/auth/api/setup/state").json().get("otpauthUri").asString())
                .as("a reload shows the same secret").isEqualTo(otpauth);
        assertThat(identities.inviteeStatus).as("nothing is set before the code").isEqualTo("PENDING_SETUP");

        Browser.Response badCode = browser.post(ISSUER + "/auth/api/setup/complete", "{\"code\":\"000000\"}",
                "X-CSRF-TOKEN", csrf);
        assertThat(badCode.status()).isEqualTo(401);
        assertThat(badCode.json().get("error").asString()).isEqualTo("invalid_code");

        Browser.Response done = browser.post(ISSUER + "/auth/api/setup/complete",
                "{\"code\":\"" + CODES.incrementAndGet() + "\"}", "X-CSRF-TOKEN", csrf);
        assertThat(done.status()).isEqualTo(200);
        assertThat(done.json().get("step").asString()).isEqualTo("setup-done");
        assertThat(done.json().get("purpose").asString()).isEqualTo("TENANT_FIRST_OWNER_SETUP");
        assertThat(identities.inviteeStatus).isEqualTo("ACTIVE");
        assertThat(identities.inviteePasswordHash).as("the hash of the accepted password")
                .isEqualTo("h:violet kettle harbour");

        assertThat(browser.post(ISSUER + "/auth/api/setup/start", "{\"token\":\"" + TestCapabilities.SETUP_TOKEN + "\"}",
                "X-CSRF-TOKEN", csrf).status()).as("a completed setup cannot be opened again").isEqualTo(404);
        for (String response : browser.received) {
            assertThat(response).doesNotContain(TestCapabilities.SETUP_TOKEN).doesNotContain("violet kettle");
        }
    }

    /**
     * identity-auth BRX-IA-ONBOARD-002 and multi-tenant 5.1 §5.7: after both factors the tenant
     * Owner's own acceptance entry accepts the invitation as an Identity Token the browser never
     * sees, and the invitee lands in the new tenant without signing in again.
     */
    @Test
    void aFirstOwnerAcceptsTheInvitationAfterBothFactorsAndLandsInTheNewTenant() {
        Browser browser = new Browser();
        String csrf = startHostedLogin(browser);
        Browser.Response opened = browser.post(ISSUER + "/auth/api/invitation",
                "{\"token\":\"" + TestCapabilities.INVITATION_TOKEN + "\"}", "X-CSRF-TOKEN", csrf);
        assertThat(opened.status()).isEqualTo(200);
        assertThat(opened.json().get("step").asString()).isEqualTo("password");
        assertThat(opened.json().at("/invitation/tenantName").asString()).isEqualTo("New Clinic");
        assertThat(opened.json().at("/invitation/inviteeEmail").asString()).isEqualTo(TestCapabilities.OWNER_EMAIL);
        assertThat(opened.body()).doesNotContain(String.valueOf(TestCapabilities.NEW_TENANT_ID));
        assertThat(browser.get(ISSUER + "/auth/api/state").json().at("/invitation/tenantName").asString())
                .as("a reload keeps the invitation").isEqualTo("New Clinic");

        assertThat(password(browser, csrf, TestCapabilities.STAFF_PASSWORD).json().get("step").asString())
                .isEqualTo("totp");
        assertThat(api.acceptances).as("nothing is accepted before the second factor").isEmpty();
        Browser.Response done = browser.post(ISSUER + "/auth/api/totp",
                "{\"code\":\"" + CODES.incrementAndGet() + "\"}", "X-CSRF-TOKEN", csrf);
        assertThat(done.status()).isEqualTo(200);
        assertThat(done.json().get("step").asString()).isEqualTo("done");
        assertThat(done.json().get("continueUrl").asString()).isEqualTo(ISSUER + "/tenant");

        assertThat(api.acceptances).hasSize(1);
        Map<String, Object> acceptance = api.acceptances.get(0);
        assertThat(acceptance).containsEntry("invitationToken", TestCapabilities.INVITATION_TOKEN);
        @SuppressWarnings("unchecked")
        Map<String, Object> claims = (Map<String, Object>) acceptance.get("claims");
        assertThat(claims).containsEntry("aud", "brix-identity")
                .containsEntry("sub", String.valueOf(TestCapabilities.OWNER_ID));
        assertThat(claims.get("allowed_actions").toString()).contains("first_owner_accept");

        Browser.Response start = browser.get(ISSUER + "/bff/login");
        Browser.Response code = browser.get(start.location(), "Accept", "text/html");
        assertThat(code.location()).as("the login session is already open").startsWith(ISSUER + "/bff/callback?code=");
        browser.get(code.location());
        JsonNode session = browser.get(ISSUER + "/bff/session").json();
        assertThat(session.at("/context/tenantId").asString()).isEqualTo(String.valueOf(TestCapabilities.NEW_TENANT_ID));
        assertThat(session.at("/context/memberType").asString()).isEqualTo("OWNER");
        assertThat(session.at("/context/contextId").asString()).isEqualTo(TestCapabilities.NEW_TENANT_CONTEXT);

        // S-20: the browser never receives the Identity Token; the invitation token never comes back.
        for (String response : browser.received) {
            assertThat(response).doesNotContain("eyJ").doesNotContain(TestCapabilities.INVITATION_TOKEN);
        }
    }

    @Test
    void anInvitationThatCannotBeAcceptedEndsBeforeAnyCredential() {
        Browser browser = new Browser();
        String csrf = startHostedLogin(browser);
        Browser.Response unknown = browser.post(ISSUER + "/auth/api/invitation", "{\"token\":\"not-an-invitation\"}",
                "X-CSRF-TOKEN", csrf);
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.json().get("step").asString()).isEqualTo("invitation-invalid");
        assertThat(password(browser, csrf, TestCapabilities.STAFF_PASSWORD).status())
                .as("no login was opened").isEqualTo(409);

        Browser.Response notSetUp = browser.post(ISSUER + "/auth/api/invitation",
                "{\"token\":\"" + TestCapabilities.INVITEE_INVITATION_TOKEN + "\"}", "X-CSRF-TOKEN", csrf);
        assertThat(notSetUp.status()).isEqualTo(200);
        assertThat(notSetUp.json().get("step").asString()).as("the invitee sets the account up first")
                .isEqualTo("invitation-setup-required");
        assertThat(password(browser, csrf, TestCapabilities.STAFF_PASSWORD).status()).isEqualTo(409);
        assertThat(api.acceptances).isEmpty();
    }

    @Test
    void aRefusedAcceptanceEndsTheLoginWithoutALoginSession() {
        Browser browser = new Browser();
        String csrf = startHostedLogin(browser);
        browser.post(ISSUER + "/auth/api/invitation", "{\"token\":\"" + TestCapabilities.TAKEN_INVITATION_TOKEN + "\"}",
                "X-CSRF-TOKEN", csrf);
        password(browser, csrf, TestCapabilities.STAFF_PASSWORD);
        Browser.Response refused = browser.post(ISSUER + "/auth/api/totp",
                "{\"code\":\"" + CODES.incrementAndGet() + "\"}", "X-CSRF-TOKEN", csrf);

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.json().get("step").asString()).isEqualTo("invitation-invalid");
        assertThat(refused.json().get("error").asString()).isEqualTo("invitation_taken");
        assertThat(api.acceptances).hasSize(1);
        assertThat(browser.get(ISSUER + "/auth/api/state").json().get("step").asString())
                .as("no login session was opened").isEqualTo("identify");
    }

    private static Browser.Response setupPassword(Browser browser, String csrf, String value) {
        return browser.post(ISSUER + "/auth/api/setup/password", "{\"password\":\"" + value + "\"}",
                "X-CSRF-TOKEN", csrf);
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
        ApiProbe apiProbe(TestCapabilities.FakeTenants tenants) {
            return new ApiProbe(tenants);
        }
    }

    /** Stands in for the Dispatcher: shows which bearer token reached {@code /api/**}. */
    @RestController
    static class ApiProbe {

        /** Every call of the FIRST_OWNER acceptance entry: the bearer's claims and the invitation token. */
        final java.util.List<Map<String, Object>> acceptances = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final TestCapabilities.FakeTenants tenants;

        ApiProbe(TestCapabilities.FakeTenants tenants) {
            this.tenants = tenants;
        }

        /**
         * Answers like the tenant Owner's FIRST_OWNER acceptance entry: it needs an Identity Token
         * that allows {@code first_owner_accept}; only the pending invitation of tenant 45 succeeds.
         */
        @PostMapping("/api/tenant/first-owner-invitations/accept")
        org.springframework.http.ResponseEntity<Map<String, Object>> acceptFirstOwner(
                @RequestHeader(value = "Authorization", required = false) String authorization,
                @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
            Map<String, Object> claims = claims(authorization);
            acceptances.add(Map.of("claims", claims, "invitationToken", String.valueOf(body.get("invitationToken"))));
            boolean identityToken = "brix-identity".equals(claims.get("aud"))
                    && claims.get("allowed_actions") instanceof java.util.List<?> actions
                    && actions.contains("first_owner_accept");
            if (!identityToken) {
                return org.springframework.http.ResponseEntity.status(403)
                        .body(Map.of("errorCode", "FIRST_OWNER_ACTOR_IDENTITY_REQUIRED", "message", "identity required"));
            }
            if (!TestCapabilities.INVITATION_TOKEN.equals(body.get("invitationToken"))) {
                return org.springframework.http.ResponseEntity.status(409)
                        .body(Map.of("errorCode", "TENANT_NOT_PENDING_ACTIVATION", "message", "not pending"));
            }
            tenants.invitationAccepted = true;
            return org.springframework.http.ResponseEntity.ok(Map.of("tenantId", String.valueOf(TestCapabilities.NEW_TENANT_ID),
                    "memberId", "11", "profileId", "12", "tenantStatus", "ACTIVE"));
        }

        @GetMapping("/api/test/whoami")
        Map<String, Object> whoami(@RequestHeader(value = "Authorization", required = false) String authorization) {
            return claims(authorization);
        }

        /** Answers like the tenant Owner's session endpoint, for the bearer the BFF sends. */
        @GetMapping("/api/tenant/session")
        Map<String, Object> tenantSession(@RequestHeader(value = "Authorization", required = false) String authorization) {
            Map<String, Object> claims = claims(authorization);
            java.util.List<String> permissions = switch (String.valueOf(claims.get("mtype"))) {
                case "MEMBER" -> java.util.List.of("tenant:profile:read", "app-booking:template:read");
                case "OWNER" -> java.util.List.of("tenant:profile:read", "tenant:member:invite");
                default -> throw new IllegalStateException("not a tenant member");
            };
            return Map.of("tenantId", claims.get("tenant_id"), "permissions", permissions);
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
