/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.runtime.orchestrator.endpoint;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.runtime.sdk.capability.AuthCapability;
import io.runtime.sdk.capability.AuthenticatedPrincipal;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointResponse;

/**
 * Spring Web adapter for Runtime Shell plugin endpoints.
 *
 * <p>This controller is part of L2B Runtime. It owns only protocol adaptation:
 * HTTP data is converted to framework-neutral invocation data, dispatched
 * through the current immutable route snapshot, and translated back to a
 * protocol response.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
@RestController
public class RuntimeShellEndpointController {

    private static final String ACTOR_HEADER = "x-actor-id";
    private static final String TENANT_HEADER = "x-tenant-id";
    private static final String IDENTITY_EMAIL_HEADER = "x-auth-identity-email";
    private static final String TOKEN_ROLE_HEADER = "x-auth-token-role";
    private static final String TOKEN_TYPE_HEADER = "x-auth-token-type";
    private static final String ALLOWED_ACTION_HEADER = "x-auth-allowed-action";

    private final PluginEndpointDispatcher dispatcher;
    private final Supplier<AuthCapability> authCapabilitySupplier;

    /**
     * Creates a controller.
     *
     * @param dispatcher Runtime Shell endpoint dispatcher
     */
    public RuntimeShellEndpointController(PluginEndpointDispatcher dispatcher) {
        this(dispatcher, () -> null);
    }

    /**
     * Creates a controller with a Runtime authentication capability.
     *
     * @param dispatcher Runtime Shell endpoint dispatcher
     * @param authCapabilityProvider current request authentication context provider
     */
    @Autowired
    public RuntimeShellEndpointController(
            PluginEndpointDispatcher dispatcher,
            ObjectProvider<AuthCapability> authCapabilityProvider) {
        this(dispatcher, authCapabilityProvider::getIfAvailable);
    }

    RuntimeShellEndpointController(
            PluginEndpointDispatcher dispatcher,
            Supplier<AuthCapability> authCapabilitySupplier) {
        this.dispatcher = dispatcher;
        this.authCapabilitySupplier = authCapabilitySupplier;
    }

    /**
     * Dispatches HTTP requests to manifest-published plugin endpoints.
     *
     * @param body request body
     * @param request servlet request boundary object
     * @return protocol response
     */
    @RequestMapping(path = "/**", method = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.PATCH,
        RequestMethod.DELETE
    })
    public ResponseEntity<Object> dispatch(
            @RequestBody(required = false) Object body,
            HttpServletRequest request) {
        try {
            Object result = dispatcher.invoke(
                request.getMethod(),
                requestPath(request),
                body,
                queryParameters(request),
                trustedInvocationHeaders(headers(request)));
            if (result instanceof EndpointResponse response) {
                return ResponseEntity.status(HttpStatusCode.valueOf(response.status())).body(response.body());
            }
            return ResponseEntity.ok(result);
        } catch (EndpointDispatchException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (EndpointHandlingException e) {
            return ResponseEntity.status(HttpStatusCode.valueOf(e.status()))
                .body(Map.of(
                    "errorCode", e.errorCode(),
                    "message", e.getMessage()));
        }
    }

    private Map<String, List<String>> trustedInvocationHeaders(Map<String, List<String>> requestHeaders) {
        Map<String, List<String>> result = new LinkedHashMap<>(requestHeaders);
        result.remove(ACTOR_HEADER);
        result.remove(TENANT_HEADER);
        result.remove(IDENTITY_EMAIL_HEADER);
        result.remove(TOKEN_ROLE_HEADER);
        result.remove(TOKEN_TYPE_HEADER);
        result.remove(ALLOWED_ACTION_HEADER);

        AuthCapability auth = authCapabilitySupplier.get();
        authenticatedActorId(auth).ifPresent(actorId -> result.put(ACTOR_HEADER, List.of(actorId)));
        authenticatedTenantId(auth).ifPresent(tenantId -> result.put(TENANT_HEADER, List.of(tenantId)));
        Optional<AuthenticatedPrincipal> principal = authenticatedPrincipal(auth);
        principal.flatMap(RuntimeShellEndpointController::authenticatedEmail)
            .ifPresent(email -> result.put(IDENTITY_EMAIL_HEADER, List.of(email)));
        principal.flatMap(RuntimeShellEndpointController::authenticatedTokenRole)
            .ifPresent(role -> result.put(TOKEN_ROLE_HEADER, List.of(role)));
        principal.flatMap(RuntimeShellEndpointController::authenticatedTokenType)
            .ifPresent(type -> result.put(TOKEN_TYPE_HEADER, List.of(type)));
        principal.map(AuthenticatedPrincipal::getAllowedActions)
            .filter(actions -> !actions.isEmpty())
            .ifPresent(actions -> result.put(ALLOWED_ACTION_HEADER, List.copyOf(actions)));
        return Map.copyOf(result);
    }

    private static Optional<String> authenticatedActorId(AuthCapability auth) {
        if (auth == null || auth.getCurrentPrincipal() == null) {
            return Optional.empty();
        }
        String name = auth.getCurrentPrincipal().getName();
        return name == null || name.isBlank() ? Optional.empty() : Optional.of(name.trim());
    }

    private static Optional<String> authenticatedTenantId(AuthCapability auth) {
        if (auth == null) {
            return Optional.empty();
        }
        String tenantId = auth.getTenantId();
        return tenantId == null || tenantId.isBlank() ? Optional.empty() : Optional.of(tenantId.trim());
    }

    private static Optional<AuthenticatedPrincipal> authenticatedPrincipal(AuthCapability auth) {
        if (auth == null || !(auth.getCurrentPrincipal() instanceof AuthenticatedPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    private static Optional<String> authenticatedEmail(AuthenticatedPrincipal principal) {
        String email = principal.getEmail();
        return email == null || email.isBlank() ? Optional.empty() : Optional.of(email.trim());
    }

    private static Optional<String> authenticatedTokenRole(AuthenticatedPrincipal principal) {
        String role = principal.getTokenRole();
        return role == null || role.isBlank() ? Optional.empty() : Optional.of(role.trim());
    }

    private static Optional<String> authenticatedTokenType(AuthenticatedPrincipal principal) {
        String type = principal.getTokenType();
        return type == null || type.isBlank() ? Optional.empty() : Optional.of(type.trim());
    }

    private static String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri == null || uri.isBlank() ? "/" : uri;
    }

    private static Map<String, List<String>> queryParameters(HttpServletRequest request) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            result.put(entry.getKey(), List.of(entry.getValue()));
        }
        return result;
    }

    private static Map<String, List<String>> headers(HttpServletRequest request) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            List<String> values = new ArrayList<>();
            Enumeration<String> headerValues = request.getHeaders(name);
            while (headerValues != null && headerValues.hasMoreElements()) {
                values.add(headerValues.nextElement());
            }
            result.put(name.toLowerCase(Locale.ROOT), List.copyOf(values));
        }
        return result;
    }
}
