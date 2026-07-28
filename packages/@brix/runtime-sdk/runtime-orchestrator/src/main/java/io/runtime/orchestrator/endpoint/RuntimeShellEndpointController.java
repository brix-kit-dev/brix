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

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    private final PluginEndpointDispatcher dispatcher;

    /**
     * Creates a controller.
     *
     * @param dispatcher Runtime Shell endpoint dispatcher
     */
    public RuntimeShellEndpointController(PluginEndpointDispatcher dispatcher) {
        this.dispatcher = dispatcher;
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
                headers(request));
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
