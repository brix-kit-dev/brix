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

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
 * Enhanced Audit Aspect
 * 
 * <p>v2.1 Phase 4 - Audit Logging Enhancement Implementation</p>
 * 
 * <p>Functionality</p>
 * <p>Intercepts methods annotated with @Auditable to record detailed audit logs.</p>
 * 
 * <p>Difference from AuditAspect:</p>
 * <ul>
 *   <li>AuditAspect: Records basic audit for all REST interfaces</li>
 *   <li>AuditableAspect: Records detailed audit for methods annotated with @Auditable</li>
 * </ul>
 * 
 * <p>Audit Log Contains:</p>
 * <ul>
 *   <li>Action type and resource type</li>
 *   <li>Request parameters (configurable masking)</li>
 *   <li>Operation result (success/failure)</li>
 *   <li>Execution duration</li>
 *   <li>User information and client information</li>
 * </ul>
 * 
 * <p>Configuration:</p>
 * <pre>
 * brix:
 *   audit:
 *     enhanced:
 *       enabled: true  # Whether to enable enhanced audit
 * </pre>
 * 
 * @author Brix Platform Authors Platform Team
 * @since v2.1
 * @see Auditable
 */
@Aspect
@Component
@Order(2)  // Execute after basic audit aspect
@ConditionalOnProperty(
    prefix = "brix.audit.enhanced",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class AuditableAspect {
    
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT.ENHANCED");
    private static final Logger log = LoggerFactory.getLogger(AuditableAspect.class);
    
    /** User ID header name */
    private static final String USER_ID_HEADER = "X-User-ID";
    /** Tenant ID header name */
    private static final String TENANT_ID_HEADER = "X-Tenant-ID";
    /** Request ID header name */
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    
    /** Masking placeholder */
    private static final String MASKED_VALUE = "****";
    
    /** Maximum parameter value length */
    private static final int MAX_PARAM_VALUE_LENGTH = 200;
    
    /**
     * Constructor
     */
    public AuditableAspect() {
        log.info("[AuditableAspect] Enhanced audit aspect initialized");
    }
    
    /**
     * Intercept methods annotated with @Auditable
     * 
     * @param joinPoint Join point
     * @param auditable Audit annotation
     * @return Method return value
     * @throws Throwable Method execution exception
     */
    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        long startTime = System.currentTimeMillis();
        Instant timestamp = Instant.now();
        
        // Extract context information
        AuditContext context = buildAuditContext(joinPoint, auditable);
        
        String status = "SUCCESS";
        String errorMessage = null;
        Object result = null;
        
        try {
            // Execute target method
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            status = "FAILURE";
            errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            // Record audit log
            logEnhancedAudit(context, status, errorMessage, duration, timestamp, 
                auditable.recordResult() ? result : null);
        }
    }
    
    /**
     * Build audit context
     */
    private AuditContext buildAuditContext(ProceedingJoinPoint joinPoint, Auditable auditable) {
        AuditContext context = new AuditContext();
        context.action = auditable.action();
        context.resource = auditable.resource();
        context.description = auditable.description();
        
        // Extract method information
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        context.methodName = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        
        // Extract request information
        ServletRequestAttributes attributes = 
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            context.userId = request.getHeader(USER_ID_HEADER);
            context.tenantId = request.getHeader(TENANT_ID_HEADER);
            context.requestId = request.getHeader(REQUEST_ID_HEADER);
            context.clientIp = getClientIp(request);
            context.path = request.getRequestURI();
            context.httpMethod = request.getMethod();
        }
        
        // Extract parameters (if configured to record parameters)
        if (auditable.recordParams()) {
            context.params = extractParams(joinPoint, auditable.sensitiveParams());
        }
        
        return context;
    }
    
    /**
     * Extract method parameters
     */
    private Map<String, String> extractParams(ProceedingJoinPoint joinPoint, String[] sensitiveParams) {
        Map<String, String> params = new LinkedHashMap<>();
        Set<String> sensitiveSet = new HashSet<>(Arrays.asList(sensitiveParams));
        
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();
        
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            String paramName = parameters[i].getName();
            Object paramValue = args[i];
            
            // Check if parameter is sensitive
            if (sensitiveSet.contains(paramName.toLowerCase())) {
                params.put(paramName, MASKED_VALUE);
                continue;
            }
            
            // Convert parameter value
            String valueStr = convertParamValue(paramValue);
            params.put(paramName, valueStr);
        }
        
        return params;
    }
    
    /**
     * Convert parameter value to string
     */
    private String convertParamValue(Object value) {
        if (value == null) {
            return "null";
        }
        
        String str;
        if (value instanceof String) {
            str = (String) value;
        } else if (value.getClass().isPrimitive() || 
                   value instanceof Number || 
                   value instanceof Boolean) {
            str = value.toString();
        } else if (value.getClass().isArray()) {
            str = "[array:" + java.lang.reflect.Array.getLength(value) + "]";
        } else if (value instanceof Collection) {
            str = "[collection:" + ((Collection<?>) value).size() + "]";
        } else {
            str = "[" + value.getClass().getSimpleName() + "]";
        }
        
        // Truncate if too long
        if (str.length() > MAX_PARAM_VALUE_LENGTH) {
            str = str.substring(0, MAX_PARAM_VALUE_LENGTH) + "...";
        }
        
        return str;
    }
    
    /**
     * Get client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP"};
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Record enhanced audit log
     */
    private void logEnhancedAudit(AuditContext context, String status, String errorMessage,
                                   long duration, Instant timestamp, Object result) {
        StringBuilder sb = new StringBuilder();
        sb.append("[AUDIT] ");
        sb.append("action=").append(context.action);
        sb.append(", resource=").append(context.resource);
        
        if (context.requestId != null) {
            sb.append(", requestId=").append(context.requestId);
        }
        
        if (context.tenantId != null) {
            sb.append(", tenantId=").append(context.tenantId);
        }
        
        if (context.userId != null) {
            sb.append(", userId=").append(context.userId);
        }
        
        sb.append(", method=").append(context.httpMethod);
        sb.append(", path=").append(context.path);
        sb.append(", handler=").append(context.methodName);
        
        if (context.params != null && !context.params.isEmpty()) {
            sb.append(", params=").append(context.params);
        }
        
        sb.append(", ip=").append(context.clientIp);
        sb.append(", status=").append(status);
        sb.append(", duration=").append(duration).append("ms");
        
        if (errorMessage != null) {
            sb.append(", error=").append(errorMessage);
        }
        
        if (result != null) {
            sb.append(", result=").append(convertParamValue(result));
        }
        
        if (context.description != null && !context.description.isEmpty()) {
            sb.append(", desc=").append(context.description);
        }
        
        sb.append(", timestamp=").append(timestamp);
        
        if ("SUCCESS".equals(status)) {
            auditLog.info(sb.toString());
        } else {
            auditLog.warn(sb.toString());
        }
    }
    
    /**
     * Audit context
     */
    private static class AuditContext {
        String action;
        String resource;
        String description;
        String methodName;
        String userId;
        String tenantId;
        String requestId;
        String clientIp;
        String path;
        String httpMethod;
        Map<String, String> params;
    }
}
