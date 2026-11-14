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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.sdk.capability.AuthCapability;
import io.runtime.sdk.capability.AuthenticatedPrincipal;
import io.runtime.sdk.error.ErrorCategory;
import io.runtime.sdk.plugin.EndpointResponse;

/**
 * Spring Web adapter for Runtime Shell plugin endpoints.
 *
 * <p>This controller is part of L2B Runtime. It owns only protocol adaptation:
 * HTTP data is converted to framework-neutral invocation data, dispatched
 * through the current immutable route snapshot, and translated back to a
 * protocol response.</p>
 *
 * <p>It reads no request body (root BRX-RS-MANIFEST-022, 3.4.0; standard-link
 * plan queue item 10, Q10b, D130): the body goes to the dispatcher unread, which
 * reads it only once the call has passed its access checks.</p>
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
    private static final String TOKEN_SCOPE_HEADER = "x-auth-token-scope";
    private static final String PERMISSION_HEADER = "x-auth-permission";
    private static final String TENANT_ROLE_TYPE_HEADER = "x-auth-tenant-role-type";
    private static final String TENANT_REF_HEADER = "x-auth-tenant-ref-id";
    private static final String ALLOWED_ACTION_HEADER = "x-auth-allowed-action";
    private static final String COOKIE_HEADER = "cookie";

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RuntimeShellEndpointController.class);

    private final PluginEndpointDispatcher dispatcher;
    private final Supplier<AuthCapability> authCapabilitySupplier;
    private final ObjectMapper json;

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
    public RuntimeShellEndpointController(
            PluginEndpointDispatcher dispatcher,
            ObjectProvider<AuthCapability> authCapabilityProvider) {
        this(dispatcher, authCapabilityProvider::getIfAvailable);
    }

    /**
     * Creates a controller with a Runtime authentication capability and the host's JSON mapper, which reads request
     * bodies as the host's JSON converter would.
     *
     * @param dispatcher Runtime Shell endpoint dispatcher
     * @param authCapabilityProvider current request authentication context provider
     * @param json the host's mapper
     * @since 3.4.0
     */
    @Autowired
    public RuntimeShellEndpointController(
            PluginEndpointDispatcher dispatcher,
            ObjectProvider<AuthCapability> authCapabilityProvider,
            ObjectMapper json) {
        this(dispatcher, authCapabilityProvider::getIfAvailable, json);
    }

    RuntimeShellEndpointController(
            PluginEndpointDispatcher dispatcher,
            Supplier<AuthCapability> authCapabilitySupplier) {
        this(dispatcher, authCapabilitySupplier, JsonMapper.builder().build());
    }

    RuntimeShellEndpointController(
            PluginEndpointDispatcher dispatcher,
            Supplier<AuthCapability> authCapabilitySupplier,
            ObjectMapper json) {
        this.dispatcher = dispatcher;
        this.authCapabilitySupplier = authCapabilitySupplier;
        this.json = json;
    }

    /**
     * Dispatches HTTP requests to manifest-published plugin endpoints.
     *
     * @param request servlet request boundary object; its body goes to the dispatcher unread
     * @return protocol response
     * @throws HttpMediaTypeNotSupportedException when a call that passed its access checks has a body that is not
     *     JSON by its content type, which the host answers as it answers such a body (D132)
     */
    @RequestMapping(path = "/**", method = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.PATCH,
        RequestMethod.DELETE
    })
    public ResponseEntity<Object> dispatch(HttpServletRequest request) throws HttpMediaTypeNotSupportedException {
        AtomicReference<String> traceId = new AtomicReference<>();
        // Root BRX-RS-MANIFEST-022 (3.4.0); standard-link plan queue item 10, Q10b, D130: the body is read by the
        // dispatcher, after the access checks. A typed endpoint still reads its text with every decimal digit (D123).
        EndpointRequestBody body = new ServletRequestBody(request, json);
        try {
            Object result = dispatcher.invoke(
                request.getMethod(),
                requestPath(request),
                body,
                queryParameters(request),
                trustedInvocationHeaders(headers(request)),
                traceId::set);
            if (result instanceof EndpointResponse response) {
                return ResponseEntity.status(HttpStatusCode.valueOf(response.status())).body(response.body());
            }
            if (result instanceof EndpointReplay.Replayed replayed) {
                return ResponseEntity.status(HttpStatusCode.valueOf(replayed.status()))
                    .header(EndpointReplay.REPLAYED_HEADER, "true").body(replayed.body());
            }
            return ResponseEntity.ok(result);
        } catch (EndpointDispatchException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (EndpointMediaTypeUnsupportedException e) {
            // Q10b, D132: answered as the host answers a body of a content type that it does not read.
            throw ServletRequestBody.unsupported(e);
        } catch (RuntimeException e) {
            EndpointFailures.Failure failure = EndpointFailures.of(e);
            if (failure.unexpected()) {
                // Root BRX-RS-MANIFEST-022, ERROR-012: anything else is platform.unknown, with the cause in the log only.
                log.error("event=endpoint.failed method={} path={} traceId={}", request.getMethod(), requestPath(request),
                    traceId.get(), e);
            }
            return failure(failure, traceId.get());
        }
    }

    /**
     * Writes the error envelope: the code, the message, the violations of an invalid request, and the call's trace id
     * (root BRX-RS-ERROR-013).
     */
    private static ResponseEntity<Object> failure(EndpointFailures.Failure failure, String traceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("errorCode", failure.code());
        body.put("message", failure.message());
        if (EndpointRequestInvalidException.CODE.equals(failure.code())) {
            // Standard-link plan L7.2: the paths and rules the request breaks, never its values.
            body.put("violations", failure.violations());
        }
        if (traceId != null) {
            body.put("traceId", traceId);
        }
        return ResponseEntity.status(HttpStatusCode.valueOf(failure.status())).body(body);
    }

    /** Returns the HTTP status of an error category. */
    static int status(ErrorCategory category) {
        return EndpointFailures.status(category);
    }

    private Map<String, List<String>> trustedInvocationHeaders(Map<String, List<String>> requestHeaders) {
        Map<String, List<String>> result = new LinkedHashMap<>(requestHeaders);
        result.remove(ACTOR_HEADER);
        result.remove(TENANT_HEADER);
        result.remove(IDENTITY_EMAIL_HEADER);
        result.remove(TOKEN_ROLE_HEADER);
        result.remove(TOKEN_TYPE_HEADER);
        result.remove(TOKEN_SCOPE_HEADER);
        result.remove(PERMISSION_HEADER);
        result.remove(TENANT_ROLE_TYPE_HEADER);
        result.remove(TENANT_REF_HEADER);
        result.remove(ALLOWED_ACTION_HEADER);
        // Browser cookies carry the BFF session ID (root BRX-RS-SECURITY-012); handlers get the
        // verified principal instead and never see session credentials.
        result.remove(COOKIE_HEADER);

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
        principal.flatMap(RuntimeShellEndpointController::authenticatedTokenScope)
            .ifPresent(scope -> result.put(TOKEN_SCOPE_HEADER, List.of(scope)));
        principal.map(AuthenticatedPrincipal::getPermissions)
            .filter(permissions -> !permissions.isEmpty())
            .ifPresent(permissions -> result.put(PERMISSION_HEADER, List.copyOf(permissions)));
        principal.flatMap(p -> nonBlank(p.getTenantRoleType()))
            .ifPresent(roleType -> result.put(TENANT_ROLE_TYPE_HEADER, List.of(roleType)));
        principal.flatMap(p -> nonBlank(p.getTenantRefId()))
            .ifPresent(refId -> result.put(TENANT_REF_HEADER, List.of(refId)));
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

    private static Optional<String> authenticatedTokenScope(AuthenticatedPrincipal principal) {
        String scope = principal.getTokenScope();
        return scope == null || scope.isBlank() ? Optional.empty() : Optional.of(scope.trim());
    }

    private static Optional<String> nonBlank(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
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
        // Root BRX-RS-MANIFEST-022: the parameters of a form come from its body, which the container would parse to
        // read them, before the access checks. A form is no JSON: its call ends in 415 once it passed them (D132).
        if (ServletRequestBody.carriesForm(request)) {
            return Map.of();
        }
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
