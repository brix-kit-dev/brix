/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.auth.servlet;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.brix.platform.auth.aspect.PermissionAspect;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Maps {@link PermissionAspect.PermissionDeniedException} to the correct HTTP status.
 *
 * <ul>
 *   <li>401 UNAUTHORIZED — when the security context is empty (no valid Bearer token).</li>
 *   <li>403 FORBIDDEN    — when the user is authenticated but lacks the required
 *       permission or role.</li>
 * </ul>
 *
 * <p>Registered as a {@code @Bean} by {@link SecurityServletAutoConfiguration} so it
 * activates automatically in any Servlet host that includes {@code platform-auth-servlet}
 * on the classpath. The {@link Order} ensures it runs before the generic {@code Exception}
 * catch-all in {@code GlobalExceptionHandler} (which returns 500).</p>
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
@RestControllerAdvice
@Order(10)
public class AuthorizationExceptionAdvice {

    /**
     * Translates a permission-denied exception to a structured 401 or 403 response.
     *
     * <p>The distinction is based on the exception message:
     * <ul>
     *   <li>{@code "User not authenticated"} → 401 (no identity present in context)</li>
     *   <li>any other message → 403 (authenticated but insufficient privilege)</li>
     * </ul>
     *
     * @param ex      the permission-denied exception thrown by the permission aspect
     * @param request the incoming servlet request (used for path in response)
     * @return 401 or 403 response with a consistent error envelope
     */
    @ExceptionHandler(PermissionAspect.PermissionDeniedException.class)
    public ResponseEntity<Map<String, Object>> handlePermissionDenied(
            PermissionAspect.PermissionDeniedException ex,
            HttpServletRequest request) {

        boolean unauthenticated = "User not authenticated".equals(ex.getMessage());
        HttpStatus status = unauthenticated ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
        String code = unauthenticated ? "AUTH-A-001" : "AUTH-A-006";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", code);
        body.put("message", ex.getMessage());
        body.put("path", request.getRequestURI());
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(status).body(body);
    }
}
