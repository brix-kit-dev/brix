package io.infra.adapter.identity.provider.login;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.infra.adapter.identity.provider.IdentityProviderProperties;
import io.infra.adapter.identity.provider.authorization.HostedLoginAuthentication;
import io.infra.adapter.identity.provider.support.EncryptedStateStore;
import io.infra.adapter.identity.provider.support.IdentityStates;
import io.infra.adapter.identity.provider.support.LoginSessionRevocations;
import io.infra.adapter.identity.provider.support.Secrets;
import io.infra.adapter.identity.provider.support.SourceRateLimiter;
import io.runtime.sdk.capability.IdentityAccountCapability;
import io.runtime.sdk.capability.IdentityAccountCapability.IdentityRecord;
import io.runtime.sdk.capability.PasswordCapability;
import io.runtime.sdk.capability.TenantAccessCapability;
import io.runtime.sdk.capability.TenantAccessCapability.TenantMembershipRecord;

/**
 * Hosted login API for staff (identity-auth §5): identifier first, then password, then TOTP;
 * afterwards the Actor context is discovered through the tenant Owner's Layer 2A contract.
 *
 * <p>Responses never reveal whether an account exists (IA-7): an unknown identifier passes the
 * identifier step like a known one and fails the password step with the same error after the
 * same password-hash work. Failed attempts are throttled per account and source only
 * (root {@code BRX-RS-SECURITY-014}); they never lock the identity elsewhere, and the temporary
 * lock another entry set does not keep staff out here (an expired one is cleared after both
 * factors, as the legacy logins do). Staff always need
 * the second factor ({@code BRX-IA-MFA-002}). One Actor context is selected automatically;
 * with several, the page chooses with one-time selection tickets (multi-tenant 5.1 §9.3); the
 * stable contextId never reaches the page before tokens exist.</p>
 *
 * <p>The login session cookie is the only client state; no token is returned here
 * ({@code BRX-IA-FLOW-003}). After the last step the browser continues the saved authorization
 * request, which returns a code to the BFF.</p>
 *
 * @since 3.2.0
 */
@RestController
@RequestMapping(path = "/auth/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class HostedLoginController {

    static final String TRANSACTION = HostedLoginController.class.getName() + ".TRANSACTION";
    private static final Logger audit = LoggerFactory.getLogger("brix.identity.audit");
    private static final String AUTHORIZE_PATH = "/oauth2/authorize";
    private static final String ACTIVE = "ACTIVE";
    private static final int MAX_IDENTIFIER_LENGTH = 254;

    static final String STEP_IDENTIFY = "identify";
    static final String STEP_PASSWORD = "password";
    static final String STEP_TOTP = "totp";
    static final String STEP_DONE = "done";
    static final String STEP_NO_TENANT = "no-tenant";
    static final String STEP_SELECT_TENANT = "select-tenant";
    static final String STEP_PASSWORD_CHANGE_REQUIRED = "password-change-required";
    static final String STEP_MFA_SETUP_REQUIRED = "mfa-setup-required";

    private final IdentityAccountCapability identities;
    private final PasswordCapability passwords;
    private final TenantAccessCapability tenants;
    private final IdentityProviderProperties properties;
    private final String issuer;
    private final SourceRateLimiter failures;
    private final SourceRateLimiter selections;
    private final SelectionTickets tickets;
    private final LoginSessionRevocations revocations;
    private final Clock clock;
    private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final RequestCache requestCache = new HttpSessionRequestCache();
    private volatile String timingHash;

    public HostedLoginController(IdentityAccountCapability identities, PasswordCapability passwords,
                                 TenantAccessCapability tenants, IdentityProviderProperties properties,
                                 EncryptedStateStore store, LoginSessionRevocations revocations, Clock clock) {
        this.identities = Objects.requireNonNull(identities, "identities must not be null");
        this.passwords = Objects.requireNonNull(passwords, "passwords must not be null");
        this.tenants = Objects.requireNonNull(tenants, "tenants must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.issuer = properties.requireIssuerOrigin();
        this.failures = new SourceRateLimiter(properties.getLogin().getMaxFailuresPerAccountAndSource(),
                properties.getLogin().getThrottleWindow(), clock);
        // §9.3 (3): selection is throttled per identity against probing other contexts.
        this.selections = new SourceRateLimiter(10, properties.getLogin().getThrottleWindow(), clock);
        this.tickets = new SelectionTickets(store, clock);
        this.revocations = Objects.requireNonNull(revocations, "revocations must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** Current step and the CSRF token the page sends with every step. */
    @GetMapping("/state")
    public LoginStep state(HttpServletRequest request, HttpServletResponse response) {
        Authentication current = contextHolder.getContext().getAuthentication();
        if (current instanceof HostedLoginAuthentication login && !revocations.isRevoked(login.getSid())) {
            return new LoginStep(STEP_DONE, csrf(request), continueUrl(request, response), null, null);
        }
        Optional<LoginTransaction> tx = transaction(request);
        if (tx.isPresent() && STEP_SELECT_TENANT.equals(tx.get().step())) {
            // A reload gets fresh tickets; the earlier ones stop working.
            return new LoginStep(STEP_SELECT_TENANT, csrf(request), null, null, options(tx.get(), request));
        }
        return new LoginStep(tx.map(LoginTransaction::step).orElse(STEP_IDENTIFY), csrf(request), null, null, null);
    }

    @PostMapping("/identify")
    public ResponseEntity<LoginStep> identify(@RequestBody IdentifyRequest body, HttpServletRequest request) {
        String identifier = normalize(body == null ? null : body.identifier());
        if (identifier == null) {
            return reply(HttpStatus.BAD_REQUEST, STEP_IDENTIFY, "invalid_identifier");
        }
        Long identityId = identities.findIdentityByEmail(identifier).map(IdentityRecord::id).orElse(null);
        request.getSession().setAttribute(TRANSACTION,
                new LoginTransaction(identifier, identityId, clock.instant(), STEP_PASSWORD));
        return reply(HttpStatus.OK, STEP_PASSWORD, null);
    }

    @PostMapping("/password")
    public ResponseEntity<LoginStep> password(@RequestBody PasswordRequest body, HttpServletRequest request) {
        Optional<LoginTransaction> found = transaction(request).filter(tx -> STEP_PASSWORD.equals(tx.step()));
        if (found.isEmpty()) {
            return reply(HttpStatus.CONFLICT, currentStep(request), "step_expired");
        }
        LoginTransaction tx = found.get();
        String throttleKey = throttleKey(tx, request);
        if (failures.isExhausted(throttleKey)) {
            return reply(HttpStatus.TOO_MANY_REQUESTS, STEP_PASSWORD, "too_many_attempts");
        }
        String password = body == null ? null : body.password();
        IdentityRecord identity = tx.identityId() == null ? null
                : identities.findIdentityById(tx.identityId()).orElse(null);
        if (!passwordMatches(identity, password) || !IdentityStates.maySignIn(identity.status())) {
            failures.tryAcquire(throttleKey);
            audit.info("event=identity.login.failed step=password identity={} source={}",
                    tx.identityId(), request.getRemoteAddr());
            return reply(HttpStatus.UNAUTHORIZED, STEP_PASSWORD, "invalid_credentials");
        }
        if (identity.passwordMustChange()) {
            tx.step(STEP_PASSWORD_CHANGE_REQUIRED);
        } else if (!identities.isTotpEnabled(identity.id())) {
            tx.step(STEP_MFA_SETUP_REQUIRED);
        } else {
            tx.step(STEP_TOTP);
        }
        request.getSession().setAttribute(TRANSACTION, tx);
        return reply(HttpStatus.OK, tx.step(), null);
    }

    @PostMapping("/totp")
    public ResponseEntity<LoginStep> totp(@RequestBody TotpRequest body, HttpServletRequest request,
                                          HttpServletResponse response) {
        Optional<LoginTransaction> found = transaction(request).filter(tx -> STEP_TOTP.equals(tx.step()));
        if (found.isEmpty()) {
            return reply(HttpStatus.CONFLICT, currentStep(request), "step_expired");
        }
        LoginTransaction tx = found.get();
        String throttleKey = throttleKey(tx, request);
        if (failures.isExhausted(throttleKey)) {
            return reply(HttpStatus.TOO_MANY_REQUESTS, STEP_TOTP, "too_many_attempts");
        }
        String code = body == null || body.code() == null ? null : body.code().trim();
        if (code == null || !identities.verifyTotp(tx.identityId(), code)) {
            failures.tryAcquire(throttleKey);
            audit.info("event=identity.login.failed step=totp identity={} source={}",
                    tx.identityId(), request.getRemoteAddr());
            return reply(HttpStatus.UNAUTHORIZED, STEP_TOTP, "invalid_code");
        }
        failures.reset(throttleKey);
        IdentityRecord identity = identities.findIdentityById(tx.identityId())
                .filter(record -> IdentityStates.maySignIn(record.status())).orElse(null);
        if (identity == null) {
            request.getSession().removeAttribute(TRANSACTION);
            return reply(HttpStatus.UNAUTHORIZED, STEP_IDENTIFY, "invalid_credentials");
        }
        clearExpiredLock(identity);
        tx.authenticated(clock.instant(), Secrets.randomToken(16));
        List<TenantMembershipRecord> memberships = activeMemberships(identity.id());
        if (memberships.size() == 1) {
            return ResponseEntity.ok(complete(tx, identity, memberships.get(0), request, response));
        }
        if (memberships.isEmpty()) {
            tx.step(STEP_NO_TENANT);
            request.getSession().setAttribute(TRANSACTION, tx);
            audit.info("event=identity.login.no_context identity={}", identity.id());
            return reply(HttpStatus.OK, STEP_NO_TENANT, null);
        }
        tx.step(STEP_SELECT_TENANT);
        List<ContextOption> options = options(tx, request);
        return ResponseEntity.ok(new LoginStep(STEP_SELECT_TENANT, null, null, null, options));
    }

    /**
     * Chooses one of the identity's Actor contexts with a selection ticket of this login
     * (multi-tenant 5.1 §9.3 Phase 2).
     */
    @PostMapping("/select")
    public ResponseEntity<LoginStep> select(@RequestBody SelectRequest body, HttpServletRequest request,
                                            HttpServletResponse response) {
        Optional<LoginTransaction> found = transaction(request).filter(tx -> STEP_SELECT_TENANT.equals(tx.step()));
        if (found.isEmpty()) {
            return reply(HttpStatus.CONFLICT, currentStep(request), "step_expired");
        }
        LoginTransaction tx = found.get();
        if (!selections.tryAcquire("select|" + tx.identityId())) {
            return reply(HttpStatus.TOO_MANY_REQUESTS, STEP_SELECT_TENANT, "too_many_attempts");
        }
        SelectionTickets.Redemption redemption = tickets.redeem(body == null ? null : body.ticket(), tx);
        if (redemption.replayed()) {
            audit.warn("event=identity.login.ticket_replayed identity={} source={}", tx.identityId(),
                    request.getRemoteAddr());
            return reply(HttpStatus.CONFLICT, STEP_SELECT_TENANT, "ticket_used");
        }
        Optional<TenantMembershipRecord> membership = redemption.valid()
                ? tenants.findMembershipByContextId(redemption.contextId())
                        .filter(record -> ACTIVE.equals(record.status()))
                        .filter(record -> tx.identityId().equals(record.identityId()))
                : Optional.empty();
        IdentityRecord identity = identities.findIdentityById(tx.identityId())
                .filter(record -> IdentityStates.maySignIn(record.status())).orElse(null);
        if (membership.isEmpty() || identity == null) {
            return reply(HttpStatus.UNAUTHORIZED, STEP_SELECT_TENANT, "ticket_invalid");
        }
        return ResponseEntity.ok(complete(tx, identity, membership.get(), request, response));
    }

    private LoginStep complete(LoginTransaction tx, IdentityRecord identity, TenantMembershipRecord membership,
                               HttpServletRequest request, HttpServletResponse response) {
        HostedLoginAuthentication authentication = new HostedLoginAuthentication(identity.id(),
                identity.username(), identity.email(), "brix:aal2", List.of("pwd", "otp"), tx.authTime(),
                tx.sid(), HostedLoginAuthentication.CONTEXT_ACTOR, membership.contextId(), membership.tenantId());
        request.getSession().removeAttribute(TRANSACTION);
        // A new session ID for the authenticated session prevents session fixation.
        request.changeSessionId();
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        audit.info("event=identity.login.succeeded identity={} tenant={} acr={} source={}",
                identity.id(), membership.tenantId(), authentication.getAcr(), request.getRemoteAddr());
        return new LoginStep(STEP_DONE, null, continueUrl(request, response), null, null);
    }

    /**
     * Clears the temporary lock of another entry once it has run out, as the legacy logins do;
     * only after both factors, so an unauthenticated request never changes the identity.
     */
    private void clearExpiredLock(IdentityRecord identity) {
        if (IdentityStates.isLocked(identity.status())
                && identities.unlockExpiredLoginLock(identity.id(), clock.instant())) {
            audit.info("event=identity.lock.expired_cleared identity={}", identity.id());
        }
    }

    private List<TenantMembershipRecord> activeMemberships(Long identityId) {
        return tenants.getActiveMemberships(identityId).stream()
                .filter(membership -> ACTIVE.equals(membership.status()) && membership.contextId() != null)
                .sorted(Comparator.comparing(TenantMembershipRecord::tenantName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    /** Fresh one-time tickets for every active Actor context; no stable contextId leaves the server. */
    private List<ContextOption> options(LoginTransaction tx, HttpServletRequest request) {
        tx.nextTicketGeneration();
        request.getSession().setAttribute(TRANSACTION, tx);
        return activeMemberships(tx.identityId()).stream()
                .map(membership -> new ContextOption(tickets.issue(tx, membership.contextId()),
                        membership.tenantName(), membership.tenantCode(), membership.memberType()))
                .toList();
    }

    /** Starts over with another identifier. */
    @PostMapping("/restart")
    public LoginStep restart(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(TRANSACTION);
        }
        return new LoginStep(STEP_IDENTIFY, null, null, null, null);
    }

    /** Ends the login session of the identity protocol surface. */
    @PostMapping("/sign-out")
    public LoginStep signOut(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        contextHolder.clearContext();
        return new LoginStep(STEP_IDENTIFY, null, null, null, null);
    }

    private boolean passwordMatches(IdentityRecord identity, String password) {
        if (password == null || password.isEmpty() || password.length() > 1024) {
            return false;
        }
        if (identity == null || identity.passwordHash() == null) {
            // Same hashing work as for a known account, so timing does not reveal existence.
            passwords.verify(password, timingHash());
            return false;
        }
        return passwords.verify(password, identity.passwordHash());
    }

    private String timingHash() {
        String hash = timingHash;
        if (hash == null) {
            hash = passwords.hash(Secrets.randomToken(24));
            timingHash = hash;
        }
        return hash;
    }

    private Optional<LoginTransaction> transaction(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(TRANSACTION) instanceof LoginTransaction tx)) {
            return Optional.empty();
        }
        if (tx.startedAt().plus(properties.getLogin().getTransactionTimeout()).isBefore(clock.instant())) {
            session.removeAttribute(TRANSACTION);
            return Optional.empty();
        }
        return Optional.of(tx);
    }

    private String currentStep(HttpServletRequest request) {
        return transaction(request).map(LoginTransaction::step).orElse(STEP_IDENTIFY);
    }

    private static String throttleKey(LoginTransaction tx, HttpServletRequest request) {
        return Secrets.sha256(tx.identifier()) + "|" + request.getRemoteAddr();
    }

    /** The saved authorization request to continue, or the console when there is none. */
    private String continueUrl(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved instanceof DefaultSavedRequest savedRequest
                && AUTHORIZE_PATH.equals(savedRequest.getRequestURI())
                && savedRequest.getQueryString() != null) {
            requestCache.removeRequest(request, response);
            return issuer + AUTHORIZE_PATH + "?" + savedRequest.getQueryString();
        }
        return issuer + properties.getTenantConsole().getAppPath();
    }

    private static String csrf(HttpServletRequest request) {
        return request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token ? token.getToken() : null;
    }

    static String normalize(String identifier) {
        if (identifier == null) {
            return null;
        }
        String value = identifier.trim().toLowerCase(Locale.ROOT);
        int at = value.indexOf('@');
        if (value.length() < 3 || value.length() > MAX_IDENTIFIER_LENGTH || at < 1 || at != value.lastIndexOf('@')
                || at == value.length() - 1 || value.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        return value;
    }

    private static ResponseEntity<LoginStep> reply(HttpStatus status, String step, String error) {
        return ResponseEntity.status(status).body(new LoginStep(step, null, null, error, null));
    }

    /** One step of the hosted login as seen by the page. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginStep(String step, String csrfToken, String continueUrl, String error,
                            List<ContextOption> options) {
    }

    /** A tenant the identity may enter, offered with a one-time selection ticket. */
    public record ContextOption(String ticket, String tenantName, String tenantCode, String memberType) {
        @Override
        public String toString() {
            return "ContextOption[tenantCode=" + tenantCode + ", memberType=" + memberType + "]";
        }
    }

    public record SelectRequest(String ticket) {
        @Override
        public String toString() {
            return "SelectRequest[ticket=<redacted>]";
        }
    }

    public record IdentifyRequest(String identifier) {
    }

    public record PasswordRequest(String password) {
        @Override
        public String toString() {
            return "PasswordRequest[password=<redacted>]";
        }
    }

    public record TotpRequest(String code) {
        @Override
        public String toString() {
            return "TotpRequest[code=<redacted>]";
        }
    }
}
