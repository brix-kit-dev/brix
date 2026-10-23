package io.infra.adapter.identity.provider.authorization;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import io.infra.adapter.identity.provider.support.ContextSwitchIntents;
import io.infra.adapter.identity.provider.support.LoginSessionRevocations;
import io.runtime.sdk.capability.TenantAccessCapability;
import io.runtime.sdk.capability.TenantAccessCapability.TenantMembershipRecord;

/**
 * Keeps the login session of the identity protocol surface honest before any endpoint uses it.
 *
 * <ul>
 *   <li>A login session that was revoked (logout, refresh token replay) or outlived the absolute
 *       session lifetime ends here, so the next authorization asks for credentials again
 *       (identity-auth {@code BRX-IA-SESSION-005}, multi-tenant §9.3 Phase 5).</li>
 *   <li>An authorization request naming a target context ({@value #CONTEXT_PARAMETER}) switches
 *       the login to that context when the BFF asked for exactly that switch for this login
 *       session ({@link ContextSwitchIntents}) and it is an active Actor context of the same
 *       identity (multi-tenant T-26, identity-auth {@code BRX-IA-FLOW-004}). Otherwise the login
 *       keeps its context and {@link ClientPolicyValidator} refuses the request.</li>
 * </ul>
 *
 * @since 3.2.0
 */
public final class HostedLoginSessionFilter extends OncePerRequestFilter {

    /** Authorization request parameter naming the Actor context to switch to. */
    public static final String CONTEXT_PARAMETER = "brix_context";

    private static final Logger audit = LoggerFactory.getLogger("brix.identity.audit");
    private static final String AUTHORIZATION_ENDPOINT = "/oauth2/authorize";
    private static final String ACTIVE = "ACTIVE";

    private final LoginSessionRevocations revocations;
    private final ContextSwitchIntents intents;
    private final TenantAccessCapability tenants;
    private final Duration absoluteLifetime;
    private final Clock clock;
    private final SecurityContextRepository repository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    public HostedLoginSessionFilter(LoginSessionRevocations revocations, ContextSwitchIntents intents,
                                    TenantAccessCapability tenants, Duration absoluteLifetime, Clock clock) {
        this.revocations = Objects.requireNonNull(revocations, "revocations must not be null");
        this.intents = Objects.requireNonNull(intents, "intents must not be null");
        this.tenants = Objects.requireNonNull(tenants, "tenants must not be null");
        this.absoluteLifetime = Objects.requireNonNull(absoluteLifetime, "absoluteLifetime must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication current = holder.getContext().getAuthentication();
        if (current instanceof HostedLoginAuthentication login) {
            if (ended(login)) {
                endLoginSession(request, login);
            } else if (isAuthorizationRequest(request)) {
                switchContextIfRequested(request, response, login);
            }
        }
        chain.doFilter(request, response);
    }

    private boolean ended(HostedLoginAuthentication login) {
        return revocations.isRevoked(login.getSid())
                || !login.getAuthTime().plus(absoluteLifetime).isAfter(clock.instant());
    }

    private void endLoginSession(HttpServletRequest request, HostedLoginAuthentication login) {
        holder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        audit.info("event=identity.login_session.ended identity={}", login.getIdentityId());
    }

    private void switchContextIfRequested(HttpServletRequest request, HttpServletResponse response,
                                          HostedLoginAuthentication login) {
        String target = request.getParameter(CONTEXT_PARAMETER);
        if (target == null || target.equals(login.getContextId())) {
            return;
        }
        boolean requestedByBff = intents.consume(login.getSid(), target);
        Optional<TenantMembershipRecord> membership = requestedByBff
                ? tenants.findMembershipByContextId(target)
                        .filter(record -> ACTIVE.equals(record.status()))
                        .filter(record -> login.getIdentityId().equals(record.identityId()))
                : Optional.empty();
        if (membership.isEmpty()) {
            audit.warn("event=identity.context.switch_denied identity={} requested_by_bff={}",
                    login.getIdentityId(), requestedByBff);
            return;
        }
        HostedLoginAuthentication switched = login.withContext(membership.get().contextId(), membership.get().tenantId());
        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(switched);
        holder.setContext(context);
        repository.saveContext(context, request, response);
        audit.info("event=identity.context.switched identity={} tenant={}", login.getIdentityId(),
                membership.get().tenantId());
    }

    private static boolean isAuthorizationRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return AUTHORIZATION_ENDPOINT.equals(path);
    }
}
