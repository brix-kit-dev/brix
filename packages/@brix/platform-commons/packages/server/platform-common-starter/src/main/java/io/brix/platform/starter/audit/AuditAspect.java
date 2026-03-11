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
package io.brix.platform.starter.audit;

import java.time.Instant;
import java.util.UUID;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * v2.1 Audit Logging Aspect
 * 
 * <p>Records audit logs for all REST API calls, including:</p>
 * <ul>
 *   <li>Request information (method, path, parameters)</li>
 *   <li>User information (user ID, IP address)</li>
 *   <li>Response information (duration, status)</li>
 * </ul>
 * 
 * <p>Enable Condition:</p>
 * <pre>
 * brix:
 *   audit:
 *     enabled: true  # Enabled by default
 * </pre>
 * 
 * <p>Log Format:</p>
 * <pre>
 * [AUDIT] requestId=xxx, method=POST, path=/api/v1/users, 
 *         userId=xxx, ip=127.0.0.1, duration=123ms, status=SUCCESS
 * </pre>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
@Aspect
@Component
@Order(1)  // Highest priority, ensure first execution
@ConditionalOnProperty(
    prefix = "brix.audit",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class AuditAspect {
    
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    
    /** Request ID header name */
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    
    /** User ID header name */
    private static final String USER_ID_HEADER = "X-User-ID";
    
    /**
     * Intercept request handling methods in all @RestController classes
     *
     * @param joinPoint the join point
     * @return method return value
     * @throws Throwable if method execution fails
     */
    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object auditRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        // Get request context
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            // Not a web request, proceed directly
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();

        // Get or generate request ID
        String requestId = getOrGenerateRequestId(request);

        // Record start time
        long startTime = System.currentTimeMillis();
        Instant startInstant = Instant.now();

        // Extract request information
        String httpMethod = request.getMethod();
        String path = request.getRequestURI();
        String userId = request.getHeader(USER_ID_HEADER);
        String clientIp = getClientIp(request);
        String methodName = getMethodName(joinPoint);

        // Execution status
        String status = "SUCCESS";
        String errorMessage = null;

        try {
            // Execute target method
            Object result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            status = "FAILURE";
            errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            throw e;
        } finally {
            // Calculate duration
            long duration = System.currentTimeMillis() - startTime;
            
            // Record audit log
            logAudit(requestId, httpMethod, path, methodName, userId, clientIp, 
                    duration, status, errorMessage, startInstant);
        }
    }
    
    /**
     * Get or generate request ID
     * 
     * @param request HTTP request
     * @return Request ID
     */
    private String getOrGenerateRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        return requestId;
    }
    
    /**
     * Get client IP address
     * 
     * @param request HTTP request
     * @return IP address
     */
    private String getClientIp(HttpServletRequest request) {
        // Check proxy headers in priority order
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For may contain multiple IPs, take the first one
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Get method name
     * 
     * @param joinPoint Join point
     * @return ClassName.methodName
     */
    private String getMethodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }
    
    /**
     * Record audit log
     */
    private void logAudit(String requestId, String httpMethod, String path, 
                          String methodName, String userId, String clientIp,
                          long duration, String status, String errorMessage,
                          Instant timestamp) {
        
        StringBuilder sb = new StringBuilder();
        sb.append("[AUDIT] ");
        sb.append("requestId=").append(requestId);
        sb.append(", method=").append(httpMethod);
        sb.append(", path=").append(path);
        sb.append(", handler=").append(methodName);
        
        if (userId != null) {
            sb.append(", userId=").append(userId);
        }
        
        sb.append(", ip=").append(clientIp);
        sb.append(", duration=").append(duration).append("ms");
        sb.append(", status=").append(status);
        
        if (errorMessage != null) {
            sb.append(", error=").append(errorMessage);
        }
        
        sb.append(", timestamp=").append(timestamp);
        
        if ("SUCCESS".equals(status)) {
            auditLog.info(sb.toString());
        } else {
            auditLog.warn(sb.toString());
        }
    }
}
