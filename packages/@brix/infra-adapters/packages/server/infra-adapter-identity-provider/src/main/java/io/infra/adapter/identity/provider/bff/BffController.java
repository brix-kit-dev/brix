package io.infra.adapter.identity.provider.bff;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.infra.adapter.identity.provider.IdentityProviderProperties;
import io.infra.adapter.identity.provider.authorization.HostedLoginSessionFilter;
import io.infra.adapter.identity.provider.support.ContextSwitchIntents;
import io.infra.adapter.identity.provider.support.LoginSessionRevocations;
import io.infra.adapter.identity.provider.support.Secrets;
import io.runtime.sdk.capability.TenantAccessCapability;
import io.runtime.sdk.capability.TenantAccessCapability.TenantMembershipRecord;

/**
 * BFF session endpoints of the tenant console (root {@code BRX-RS-SECURITY-012}, identity-auth
 * §8). The browser only ever receives the {@code __Host-} session cookie and a CSRF token; the
 * access, refresh and ID tokens stay in the encrypted server-side session (S-20).
 *
 * <ul>
 *   <li>{@code GET /bff/login} starts authorization code + PKCE and sends the browser to the
 *       authorization endpoint;</li>
 *   <li>{@code GET /bff/callback} finishes it and opens the session;</li>
 *   <li>{@code GET /bff/session} describes the session: identity, context, the identity's other
 *       Actor contexts, effective permissions, authentication;</li>
 *   <li>{@code POST /bff/switch} re-authorizes silently ({@code prompt=none}) for another Actor
 *       context of the same identity (identity-auth {@code BRX-IA-FLOW-004}, multi-tenant T-26);</li>
 *   <li>{@code POST /bff/logout} ends the BFF session, revokes the refresh token and ends the
 *       login session of the identity protocol surface ({@code BRX-IA-SESSION-005}).</li>
 * </ul>
 *
 * @since 3.2.0
 */
@RestController
@RequestMapping("/bff")
public class BffController {

    /** Request header that carries the session's CSRF token on state-changing requests. */
    public static final String CSRF_HEADER = "X-Brix-CSRF";

    private static final Logger audit = LoggerFactory.getLogger("brix.identity.audit");
    private static final Duration LOGIN_BINDING_TTL = Duration.ofMinutes(10);
    private static final Pattern SAFE_PATH = Pattern.compile("^/[A-Za-z0-9._~!$&'()*+,;=:@%/-]*(\\?[A-Za-z0-9._~!$&'()*+,;=:@%/?-]*)?$");
    private static final Pattern ERROR_CODE = Pattern.compile("^[a-z_]{1,64}$");

    private final BffSessionStore sessions;
    private final BffTokenClient tokens;
    private final BffTokenRefresher refresher;
    private final TenantAccessCapability tenants;
    private final LoginSessionRevocations revocations;
    private final ContextSwitchIntents switchIntents;
    private final IdentityProviderProperties properties;
    private final String issuer;
    private final Clock clock;

    public BffController(BffSessionStore sessions, BffTokenClient tokens, BffTokenRefresher refresher,
                         TenantAccessCapability tenants, LoginSessionRevocations revocations,
                         ContextSwitchIntents switchIntents, IdentityProviderProperties properties, Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.tokens = Objects.requireNonNull(tokens, "tokens must not be null");
        this.refresher = Objects.requireNonNull(refresher, "refresher must not be null");
        this.tenants = Objects.requireNonNull(tenants, "tenants must not be null");
        this.revocations = Objects.requireNonNull(revocations, "revocations must not be null");
        this.switchIntents = Objects.requireNonNull(switchIntents, "switchIntents must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.issuer = properties.requireIssuerOrigin();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @GetMapping("/login")
    public ResponseEntity<Void> login(@RequestParam(name = "returnTo", required = false) String returnTo,
                                      HttpServletResponse response) {
        String state = Secrets.randomToken(32);
        String nonce = Secrets.randomToken(32);
        String codeVerifier = Secrets.randomToken(48);
        String binding = Secrets.randomToken(32);
        sessions.savePending(state, new PendingLogin(codeVerifier, nonce, safeReturnTo(returnTo),
                Secrets.sha256(binding), clock.instant().getEpochSecond()));
        BffCookies.write(response, BffCookies.LOGIN_BINDING, binding, LOGIN_BINDING_TTL, "Lax");
        return redirect(tokens.authorizationRequest(state, nonce, codeVerifier).getAuthorizationRequestUri());
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(name = "code", required = false) String code,
                                         @RequestParam(name = "state", required = false) String state,
                                         @RequestParam(name = "error", required = false) String error,
                                         HttpServletRequest request, HttpServletResponse response) {
        Optional<PendingLogin> pending = sessions.takePending(state);
        Optional<String> binding = BffCookies.read(request, BffCookies.LOGIN_BINDING);
        BffCookies.clear(response, BffCookies.LOGIN_BINDING, "Lax");
        if (error != null) {
            audit.info("event=identity.bff.login_failed reason=authorization_error error={}", errorCode(error));
            if ("login_required".equals(error) && pending.isPresent()) {
                // A silent switch found no login session: log in again and return to the same page.
                return redirect(issuer + "/bff/login?returnTo="
                        + java.net.URLEncoder.encode(pending.get().returnTo(), java.nio.charset.StandardCharsets.UTF_8));
            }
            return failure(errorCode(error));
        }
        if (pending.isEmpty() || code == null) {
            return failure("login_expired");
        }
        if (binding.isEmpty() || !Secrets.matches(pending.get().browserBinding(), Secrets.sha256(binding.get()))) {
            audit.warn("event=identity.bff.login_failed reason=browser_binding_mismatch source={}",
                    request.getRemoteAddr());
            return failure("login_expired");
        }
        BffTokenClient.TokenSet tokenSet;
        try {
            tokenSet = tokens.exchange(pending.get(), state, code);
        } catch (RuntimeException e) {
            audit.warn("event=identity.bff.login_failed reason=token_exchange error={}", e.getClass().getSimpleName());
            return failure("token_exchange_failed");
        }
        Jwt idToken = tokenSet.idToken();
        Optional<TenantMembershipRecord> membership = tenants.findMembershipByContextId(idToken.getClaimAsString("cid"))
                .filter(record -> String.valueOf(record.identityId()).equals(idToken.getSubject()));
        String target = pending.get().targetContextId();
        if (membership.isEmpty() || (target != null && !target.equals(membership.get().contextId()))) {
            revokeQuietly(tokenSet.refreshToken());
            return failure("context_unavailable");
        }
        // The new session replaces this browser's previous one; its token family ends with it.
        Optional<String> previousId = BffCookies.read(request, BffCookies.SESSION);
        previousId.flatMap(sessions::find).ifPresent(previous -> revokeQuietly(previous.refreshToken()));
        previousId.ifPresent(sessions::delete);
        Instant now = clock.instant();
        BffSession session = new BffSession(
                Secrets.randomToken(32),
                now.getEpochSecond(),
                now.plus(properties.getSession().getAbsoluteTimeout()).getEpochSecond(),
                now.getEpochSecond(),
                tokenSet.accessToken(),
                tokenSet.accessTokenExpiresAt(),
                tokenSet.refreshToken(),
                idToken.getTokenValue(),
                idToken.getSubject(),
                idToken.getClaimAsString("email"),
                idToken.getClaimAsString("preferred_username"),
                idToken.getClaimAsString("ctx"),
                membership.get().contextId(),
                String.valueOf(membership.get().tenantId()),
                membership.get().tenantName(),
                membership.get().memberType(),
                idToken.getClaimAsString("acr"),
                idToken.getClaimAsStringList("amr"),
                idToken.getClaimAsInstant("auth_time") == null ? now.getEpochSecond()
                        : idToken.getClaimAsInstant("auth_time").getEpochSecond(),
                idToken.getClaimAsString("sid"));
        String sessionId = sessions.create(session);
        BffCookies.write(response, BffCookies.SESSION, sessionId, Duration.ofSeconds(-1), "Strict");
        audit.info("event=identity.bff.session_created identity={} tenant={} acr={} switch={}",
                session.subject(), session.tenantId(), session.acr(), target != null);
        return redirect(issuer + pending.get().returnTo());
    }

    /**
     * Starts a silent re-authorization for another Actor context of the session's identity.
     * The browser follows the returned URI; the callback replaces the session and its tokens,
     * and the console reloads with clean state. The authorization endpoint switches the login
     * session only for the intent recorded here, never for a link alone.
     */
    @PostMapping(path = "/switch", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> switchContext(@RequestBody(required = false) SwitchRequest body,
                                                             HttpServletRequest request,
                                                             HttpServletResponse response) {
        Optional<BffSession> found = BffCookies.read(request, BffCookies.SESSION).flatMap(sessions::find);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "session_required"));
        }
        BffSession session = found.get();
        if (!Secrets.matches(session.csrfToken(), request.getHeader(CSRF_HEADER))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "csrf_invalid"));
        }
        String target = body == null ? null : body.contextId();
        boolean sameIdentityActorContext = target != null && !target.equals(session.contextId())
                && activeMemberships(session.subject()).stream().anyMatch(m -> target.equals(m.contextId()));
        if (!sameIdentityActorContext) {
            audit.warn("event=identity.bff.switch_denied identity={}", session.subject());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "context_not_available"));
        }
        String state = Secrets.randomToken(32);
        String nonce = Secrets.randomToken(32);
        String codeVerifier = Secrets.randomToken(48);
        String binding = Secrets.randomToken(32);
        sessions.savePending(state, new PendingLogin(codeVerifier, nonce, safeReturnTo(body.returnTo()),
                Secrets.sha256(binding), clock.instant().getEpochSecond(), target));
        BffCookies.write(response, BffCookies.LOGIN_BINDING, binding, LOGIN_BINDING_TTL, "Lax");
        switchIntents.expect(session.sid(), target);
        String redirect = tokens.authorizationRequest(state, nonce, codeVerifier,
                Map.of("prompt", "none", HostedLoginSessionFilter.CONTEXT_PARAMETER, target))
                .getAuthorizationRequestUri();
        return ResponseEntity.ok(Map.of("redirect", redirect));
    }

    @GetMapping(path = "/session", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> session(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> id = BffCookies.read(request, BffCookies.SESSION);
        Optional<BffSession> found = id.flatMap(sessions::find)
                .flatMap(stored -> refresher.currentTokens(id.get(), stored));
        if (found.isEmpty()) {
            id.ifPresent(stale -> BffCookies.clear(response, BffCookies.SESSION, "Strict"));
            return Map.of("authenticated", false);
        }
        BffSession session = found.get();
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("id", session.subject());
        identity.put("email", session.email());
        identity.put("username", session.username());
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("kind", session.contextKind());
        context.put("tenantId", session.tenantId());
        context.put("tenantName", session.tenantName());
        context.put("contextId", session.contextId());
        context.put("memberType", session.memberType());
        Map<String, Object> authentication = new LinkedHashMap<>();
        authentication.put("acr", session.acr());
        authentication.put("amr", session.amr() == null ? List.of() : session.amr());
        authentication.put("authTime", Instant.ofEpochSecond(session.authTime()).toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", true);
        body.put("csrfToken", session.csrfToken());
        body.put("identity", identity);
        body.put("context", context);
        body.put("permissions", effectivePermissions(session));
        body.put("contexts", activeMemberships(session.subject()).stream().map(membership -> {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("contextId", membership.contextId());
            option.put("memberId", String.valueOf(membership.memberId()));
            option.put("tenantId", String.valueOf(membership.tenantId()));
            option.put("tenantCode", membership.tenantCode());
            option.put("tenantName", membership.tenantName());
            option.put("memberType", membership.memberType());
            option.put("current", membership.contextId().equals(session.contextId()));
            return option;
        }).toList());
        body.put("authentication", authentication);
        body.put("expiresAt", Instant.ofEpochSecond(session.absoluteExpiresAt()).toString());
        return body;
    }

    /**
     * The effective permissions of the session's context as the Dispatcher computes them from
     * the Runtime grant table (multi-tenant 5.1 BRX-TENANT-AUTHZ-009). When they cannot be
     * read, the console gets none: hiding entries is never authorization, so failing closed
     * only costs convenience.
     */
    private List<String> effectivePermissions(BffSession session) {
        try {
            Object permissions = tokens.callApi(properties.getTenantConsole().getContextPath(), session.accessToken())
                    .get("permissions");
            if (permissions instanceof List<?> values) {
                return values.stream().filter(String.class::isInstance).map(String.class::cast).sorted().toList();
            }
        } catch (RuntimeException e) {
            audit.warn("event=identity.bff.permissions_unavailable identity={} error={}",
                    session.subject(), e.getClass().getSimpleName());
        }
        return List.of();
    }

    @PostMapping(path = "/logout", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> id = BffCookies.read(request, BffCookies.SESSION);
        Optional<BffSession> found = id.flatMap(sessions::find);
        if (found.isPresent()) {
            if (!Secrets.matches(found.get().csrfToken(), request.getHeader(CSRF_HEADER))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "csrf_invalid"));
            }
            revokeQuietly(found.get().refreshToken());
            revocations.revoke(found.get().sid());
            sessions.delete(id.get());
            audit.info("event=identity.bff.logout identity={}", found.get().subject());
        }
        HttpSession loginSession = request.getSession(false);
        if (loginSession != null) {
            loginSession.invalidate();
        }
        id.ifPresent(value -> BffCookies.clear(response, BffCookies.SESSION, "Strict"));
        return ResponseEntity.ok(Map.of("loggedOut", true));
    }

    /** The identity's active Actor contexts, the only ones a switch may target (multi-tenant T-26). */
    private List<TenantMembershipRecord> activeMemberships(String subject) {
        try {
            return tenants.getActiveMemberships(Long.valueOf(subject)).stream()
                    .filter(membership -> "ACTIVE".equals(membership.status()) && membership.contextId() != null)
                    .toList();
        } catch (NumberFormatException e) {
            return List.of();
        }
    }

    /** Revocation is best effort: the server-side session is deleted either way. */
    private void revokeQuietly(String refreshToken) {
        if (refreshToken == null) {
            return;
        }
        try {
            tokens.revoke(refreshToken);
        } catch (RuntimeException e) {
            audit.warn("event=identity.bff.revoke_failed error={}", e.getClass().getSimpleName());
        }
    }

    /** Return paths stay on this origin, under the console path (no open redirect). */
    String safeReturnTo(String returnTo) {
        String appPath = properties.getTenantConsole().getAppPath();
        if (returnTo == null || returnTo.isBlank() || returnTo.length() > 512 || returnTo.startsWith("//")
                || returnTo.contains("\\") || !SAFE_PATH.matcher(returnTo).matches()) {
            return appPath;
        }
        String path = returnTo.contains("?") ? returnTo.substring(0, returnTo.indexOf('?')) : returnTo;
        if (path.contains("/../") || path.endsWith("/..") || !(path.equals(appPath) || path.startsWith(appPath + "/"))) {
            return appPath;
        }
        return returnTo;
    }

    private ResponseEntity<Void> failure(String reason) {
        return redirect(issuer + "/auth/error?reason=" + reason);
    }

    private static String errorCode(String error) {
        return ERROR_CODE.matcher(error).matches() ? error : "authorization_failed";
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    /** Target of a context switch and the console page to return to. */
    public record SwitchRequest(String contextId, String returnTo) {
    }
}
