package io.infra.adapter.identity.provider.bff;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import io.infra.adapter.identity.provider.support.Secrets;

/**
 * Lets the Dispatcher authenticate {@code /api/**} requests of a BFF session (root
 * {@code BRX-RS-SECURITY-012}): the session cookie selects the server-side session, a
 * state-changing request must carry the session's CSRF token in {@code X-Brix-CSRF}, and the
 * session's access token is handed to the Dispatcher's authentication step inside this
 * process, which validates it like any other token ({@code BRX-RS-SECURITY-004}).
 *
 * <p>Requests that already carry an {@code Authorization} header keep it (the exempted
 * legacy platform console, EX-2026-002). Requests without a live session continue without
 * credentials, so protected endpoints answer 401 as before.</p>
 *
 * @since 3.2.0
 */
public final class BffSessionRelayFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final BffSessionStore sessions;
    private final BffTokenRefresher refresher;

    public BffSessionRelayFilter(BffSessionStore sessions, BffTokenRefresher refresher) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.refresher = Objects.requireNonNull(refresher, "refresher must not be null");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            chain.doFilter(request, response);
            return;
        }
        Optional<String> sessionId = BffCookies.read(request, BffCookies.SESSION);
        Optional<BffSession> session = sessionId.flatMap(sessions::find);
        if (session.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        if (!SAFE_METHODS.contains(request.getMethod())
                && !Secrets.matches(session.get().csrfToken(), request.getHeader(BffController.CSRF_HEADER))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(
                    "{\"errorCode\":\"BFF_CSRF_REJECTED\",\"message\":\"CSRF token is missing or invalid\"}"
                            .getBytes(StandardCharsets.UTF_8));
            return;
        }
        Optional<BffSession> current = refresher.currentTokens(sessionId.get(), session.get());
        if (current.isEmpty()) {
            BffCookies.clear(response, BffCookies.SESSION, "Strict");
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new BearerRequest(request, "Bearer " + current.get().accessToken()), response);
    }

    /** The request as the Dispatcher sees it: with the session's access token. */
    private static final class BearerRequest extends HttpServletRequestWrapper {

        private final String authorization;

        BearerRequest(HttpServletRequest request, String authorization) {
            super(request);
            this.authorization = authorization;
        }

        @Override
        public String getHeader(String name) {
            return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name) ? authorization : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)
                    ? Collections.enumeration(List.of(authorization)) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
            if (names.stream().noneMatch(HttpHeaders.AUTHORIZATION::equalsIgnoreCase)) {
                names.add(HttpHeaders.AUTHORIZATION);
            }
            return Collections.enumeration(names);
        }
    }
}
