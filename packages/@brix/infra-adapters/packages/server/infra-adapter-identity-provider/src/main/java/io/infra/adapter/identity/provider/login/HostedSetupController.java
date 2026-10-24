package io.infra.adapter.identity.provider.login;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.infra.adapter.identity.provider.IdentityProviderProperties;
import io.infra.adapter.identity.provider.support.Secrets;
import io.infra.adapter.identity.provider.support.SourceRateLimiter;
import io.runtime.sdk.capability.IdentityAccountCapability;
import io.runtime.sdk.capability.IdentityAccountCapability.CredentialSetupOutcome;
import io.runtime.sdk.capability.IdentityAccountCapability.CredentialSetupRecord;
import io.runtime.sdk.capability.IdentityAccountCapability.PasswordRejection;
import io.runtime.sdk.capability.PasswordCapability;

/**
 * Hosted setup API (identity-auth {@code BRX-IA-ONBOARD-002}/{@code -003}): a FIRST_OWNER invitee
 * whose identity is still being set up opens the setup link, sets a password
 * ({@code BRX-IA-CRED-001}) and binds TOTP; staff always use a second factor
 * ({@code BRX-IA-MFA-002}).
 *
 * <p>The page sends the token it read from the link's fragment once; afterwards it lives only in
 * the login session on the server, which the HttpOnly session cookie names. Every check and write
 * happens in the identity Owner through its Layer 2A contract ({@code BRX-IA-LAYER-001}); the
 * password is hashed here with the policy-driven password capability and only the hash is kept
 * until the setup completes. When it completes, the identity Owner tells the tenant Owner, which
 * sends the invitation link.</p>
 *
 * @since 3.2.0
 */
@RestController
@RequestMapping(path = "/auth/api/setup", produces = MediaType.APPLICATION_JSON_VALUE)
public class HostedSetupController {

    static final String SETUP = HostedSetupController.class.getName() + ".SETUP";
    static final String STEP_START = "setup-start";
    static final String STEP_PASSWORD = "setup-password";
    static final String STEP_TOTP = "setup-totp";
    static final String STEP_DONE = "setup-done";
    static final String STEP_INVALID = "setup-invalid";
    private static final Logger audit = LoggerFactory.getLogger("brix.identity.audit");
    private static final int MAX_TOKEN_LENGTH = 512;
    private static final int MAX_PASSWORD_LENGTH = 1024;

    private final IdentityAccountCapability identities;
    private final PasswordCapability passwords;
    private final IdentityProviderProperties properties;
    private final SourceRateLimiter starts;
    private final SourceRateLimiter codes;
    private final Clock clock;

    public HostedSetupController(IdentityAccountCapability identities, PasswordCapability passwords,
                                 IdentityProviderProperties properties, Clock clock) {
        this.identities = Objects.requireNonNull(identities, "identities must not be null");
        this.passwords = Objects.requireNonNull(passwords, "passwords must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.starts = new SourceRateLimiter(20, properties.getLogin().getThrottleWindow(), clock);
        this.codes = new SourceRateLimiter(properties.getLogin().getMaxFailuresPerAccountAndSource(),
                properties.getLogin().getThrottleWindow(), clock);
    }

    /** Current step and the CSRF token the page sends with every step. */
    @GetMapping("/state")
    public SetupStep state(HttpServletRequest request) {
        Optional<SetupTransaction> tx = transaction(request);
        if (tx.isEmpty()) {
            return new SetupStep(STEP_START, csrf(request), null, null, null, null);
        }
        SetupTransaction setup = tx.get();
        return new SetupStep(setup.step(), csrf(request), null, setup.email(),
                STEP_TOTP.equals(setup.step()) ? setup.otpauthUri() : null, setup.purpose());
    }

    /** Opens the setup of a setup link; a token that no longer opens one ends here. */
    @PostMapping("/start")
    public ResponseEntity<SetupStep> start(@RequestBody TokenRequest body, HttpServletRequest request) {
        String token = body == null || body.token() == null ? null : body.token().trim();
        if (token == null || token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) {
            return reply(HttpStatus.BAD_REQUEST, STEP_INVALID, "setup_invalid");
        }
        if (!starts.tryAcquire("setup|" + request.getRemoteAddr())) {
            return reply(HttpStatus.TOO_MANY_REQUESTS, STEP_START, "too_many_attempts");
        }
        Optional<CredentialSetupRecord> found = identities.findCredentialSetup(token);
        if (found.isEmpty()) {
            audit.info("event=identity.setup.invalid source={}", request.getRemoteAddr());
            return reply(HttpStatus.NOT_FOUND, STEP_INVALID, "setup_invalid");
        }
        CredentialSetupRecord setup = found.get();
        request.getSession().setAttribute(SETUP, new SetupTransaction(token, setup.identityId(), setup.email(),
                setup.purpose(), clock.instant(), STEP_PASSWORD));
        audit.info("event=identity.setup.started identity={} purpose={}", setup.identityId(), setup.purpose());
        return ResponseEntity.ok(new SetupStep(STEP_PASSWORD, null, null, setup.email(), null, setup.purpose()));
    }

    /**
     * Accepts the new password when the security profile does, keeps only its hash and starts the
     * TOTP enrollment.
     */
    @PostMapping("/password")
    public ResponseEntity<SetupStep> password(@RequestBody PasswordRequest body, HttpServletRequest request) {
        Optional<SetupTransaction> found = transaction(request).filter(tx -> STEP_PASSWORD.equals(tx.step()));
        if (found.isEmpty()) {
            return reply(HttpStatus.CONFLICT, currentStep(request), "step_expired");
        }
        SetupTransaction tx = found.get();
        String password = body == null ? null : body.password();
        if (password != null && password.length() > MAX_PASSWORD_LENGTH) {
            return reply(HttpStatus.BAD_REQUEST, STEP_PASSWORD, "password_too_long");
        }
        Optional<PasswordRejection> rejection = identities.rejectNewPassword(tx.identityId(), password);
        if (rejection.isPresent()) {
            String error = rejection.get() == PasswordRejection.UNAVAILABLE
                    ? "unavailable" : "password_" + rejection.get().name().toLowerCase(java.util.Locale.ROOT);
            return reply(rejection.get() == PasswordRejection.UNAVAILABLE ? HttpStatus.SERVICE_UNAVAILABLE
                    : HttpStatus.BAD_REQUEST, STEP_PASSWORD, error);
        }
        Optional<String> enrollment = identities.beginTotpEnrollment(tx.token());
        if (enrollment.isEmpty()) {
            request.getSession().removeAttribute(SETUP);
            return reply(HttpStatus.GONE, STEP_INVALID, "setup_invalid");
        }
        tx.passwordAccepted(passwords.hash(password), enrollment.get(), STEP_TOTP);
        request.getSession().setAttribute(SETUP, tx);
        return ResponseEntity.ok(new SetupStep(STEP_TOTP, null, null, tx.email(), enrollment.get(), tx.purpose()));
    }

    /** Completes the setup with a code of the enrolled secret. */
    @PostMapping("/complete")
    public ResponseEntity<SetupStep> complete(@RequestBody CodeRequest body, HttpServletRequest request) {
        Optional<SetupTransaction> found = transaction(request).filter(tx -> STEP_TOTP.equals(tx.step()));
        if (found.isEmpty()) {
            return reply(HttpStatus.CONFLICT, currentStep(request), "step_expired");
        }
        SetupTransaction tx = found.get();
        String throttleKey = Secrets.sha256(tx.token());
        if (codes.isExhausted(throttleKey)) {
            return reply(HttpStatus.TOO_MANY_REQUESTS, STEP_TOTP, "too_many_attempts");
        }
        String code = body == null || body.code() == null ? "" : body.code().replaceAll("\\s+", "");
        CredentialSetupOutcome outcome = code.length() > 8 ? CredentialSetupOutcome.INVALID_CODE
                : identities.completeCredentialSetup(tx.token(), tx.passwordHash(), code);
        switch (outcome) {
            case COMPLETED -> {
                codes.reset(throttleKey);
                request.getSession().removeAttribute(SETUP);
                audit.info("event=identity.setup.completed identity={} purpose={}", tx.identityId(), tx.purpose());
                return ResponseEntity.ok(new SetupStep(STEP_DONE, null, null, tx.email(), null, tx.purpose()));
            }
            case INVALID_CODE -> {
                codes.tryAcquire(throttleKey);
                audit.info("event=identity.setup.failed step=totp identity={} source={}",
                        tx.identityId(), request.getRemoteAddr());
                return reply(HttpStatus.UNAUTHORIZED, STEP_TOTP, "invalid_code");
            }
            case INVALID_TOKEN -> {
                request.getSession().removeAttribute(SETUP);
                return reply(HttpStatus.GONE, STEP_INVALID, "setup_invalid");
            }
            default -> {
                return reply(HttpStatus.SERVICE_UNAVAILABLE, STEP_TOTP, "unavailable");
            }
        }
    }

    private Optional<SetupTransaction> transaction(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(SETUP) instanceof SetupTransaction tx)) {
            return Optional.empty();
        }
        if (tx.startedAt().plus(properties.getLogin().getSetupTimeout()).isBefore(clock.instant())) {
            session.removeAttribute(SETUP);
            return Optional.empty();
        }
        return Optional.of(tx);
    }

    private String currentStep(HttpServletRequest request) {
        return transaction(request).map(SetupTransaction::step).orElse(STEP_START);
    }

    private static String csrf(HttpServletRequest request) {
        return request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token ? token.getToken() : null;
    }

    private static ResponseEntity<SetupStep> reply(HttpStatus status, String step, String error) {
        return ResponseEntity.status(status).body(new SetupStep(step, null, error, null, null, null));
    }

    /**
     * One step of the hosted setup as seen by the page. The {@code otpauth} URI carries the new
     * secret for the authenticator app; it is only answered during the enrollment step.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SetupStep(String step, String csrfToken, String error, String email, String otpauthUri,
                            String purpose) {
        @Override
        public String toString() {
            return "SetupStep[step=" + step + ", error=" + error + ", otpauthUri=" + (otpauthUri == null ? null : "<redacted>") + "]";
        }
    }

    public record TokenRequest(String token) {
        @Override
        public String toString() {
            return "TokenRequest[token=<redacted>]";
        }
    }

    public record PasswordRequest(String password) {
        @Override
        public String toString() {
            return "PasswordRequest[password=<redacted>]";
        }
    }

    public record CodeRequest(String code) {
        @Override
        public String toString() {
            return "CodeRequest[code=<redacted>]";
        }
    }
}
