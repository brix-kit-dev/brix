package io.infra.adapter.identity.provider.bff;

import java.time.Duration;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * Cookies of the BFF. Both are {@code __Host-} cookies: {@code Secure}, {@code HttpOnly}, no
 * {@code Domain}, {@code Path=/} (root {@code BRX-RS-SECURITY-012}, RFC 10017 §6.1.3.2).
 *
 * <ul>
 *   <li>{@link #SESSION}: the opaque BFF session ID, {@code SameSite=Strict};</li>
 *   <li>{@link #LOGIN_BINDING}: binds a started login to this browser until the callback,
 *       {@code SameSite=Lax} so the callback redirect still carries it.</li>
 * </ul>
 *
 * @since 3.2.0
 */
public final class BffCookies {

    public static final String SESSION = "__Host-brix-session";
    public static final String LOGIN_BINDING = "__Host-brix-login";

    private BffCookies() {
    }

    public static Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    static void write(HttpServletResponse response, String name, String value, Duration maxAge, String sameSite) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .secure(true)
                .httpOnly(true)
                .path("/")
                .sameSite(sameSite)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    static void clear(HttpServletResponse response, String name, String sameSite) {
        write(response, name, "", Duration.ZERO, sameSite);
    }
}
