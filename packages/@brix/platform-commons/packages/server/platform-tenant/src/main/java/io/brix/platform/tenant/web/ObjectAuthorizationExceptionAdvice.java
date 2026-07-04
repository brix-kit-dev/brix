package io.brix.platform.tenant.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.runtime.sdk.capability.ObjectAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Maps object-level authorization denials to a stable 403 response envelope.
 */
@RestControllerAdvice
@Order(9)
public class ObjectAuthorizationExceptionAdvice {

    @ExceptionHandler(ObjectAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleObjectAccessDenied(
            ObjectAccessDeniedException ex,
            HttpServletRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", ex.getErrorCode());
        body.put("message", ex.getMessage());
        body.put("objectType", ex.getObjectType());
        body.put("objectId", ex.getObjectId());
        body.put("action", ex.getAction());
        body.put("path", request.getRequestURI());
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }
}