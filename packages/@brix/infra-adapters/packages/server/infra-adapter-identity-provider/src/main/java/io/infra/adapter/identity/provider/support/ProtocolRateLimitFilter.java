package io.infra.adapter.identity.provider.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Request limit per source address for the identity protocol surface (root
 * {@code BRX-RS-SECURITY-011} (2)). Behind a reverse proxy the source is the address the
 * servlet container reports, so the Host decides whether forwarded addresses are trusted.
 *
 * @since 3.2.0
 */
public final class ProtocolRateLimitFilter extends OncePerRequestFilter {

    private final SourceRateLimiter limiter;

    public ProtocolRateLimitFilter(int requestsPerMinute) {
        this.limiter = new SourceRateLimiter(requestsPerMinute, Duration.ofMinutes(1));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!limiter.tryAcquire(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write("{\"error\":\"too_many_requests\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }
}
