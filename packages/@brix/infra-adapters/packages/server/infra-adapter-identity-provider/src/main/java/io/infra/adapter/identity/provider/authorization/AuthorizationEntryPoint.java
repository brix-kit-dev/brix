package io.infra.adapter.identity.provider.authorization;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Where an authorization request without a login session goes. Normally to the hosted login.
 * With {@code prompt=none} (a silent context switch, identity-auth {@code BRX-IA-FLOW-004}) no
 * page may be shown: the client gets {@code login_required} at its redirect URI (OpenID
 * Connect Core §3.1.2.6).
 *
 * <p>The error only goes to a redirect URI the authorization server has already validated for
 * this request; otherwise the answer is a plain 400, never an unvalidated redirect.</p>
 *
 * @since 3.2.0
 */
public final class AuthorizationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint login;

    public AuthorizationEntryPoint(AuthenticationEntryPoint login) {
        this.login = Objects.requireNonNull(login, "login must not be null");
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, jakarta.servlet.ServletException {
        if (!promptNone(request)) {
            login.commence(request, response, exception);
            return;
        }
        Object validated = request.getAttribute(OAuth2AuthorizationCodeRequestAuthenticationToken.class.getName());
        if (validated instanceof OAuth2AuthorizationCodeRequestAuthenticationToken token && token.getRedirectUri() != null) {
            String location = UriComponentsBuilder.fromUriString(token.getRedirectUri())
                    .queryParam("error", "login_required")
                    .queryParamIfPresent("state", Optional.ofNullable(token.getState()))
                    .encode()
                    .build()
                    .toUriString();
            response.sendRedirect(location);
            return;
        }
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
    }

    private static boolean promptNone(HttpServletRequest request) {
        String prompt = request.getParameter("prompt");
        return prompt != null && Arrays.asList(prompt.split(" ")).contains("none");
    }
}
