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
package io.brix.platform.auth.aspect;

import java.lang.reflect.Method;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

import io.brix.platform.auth.annotation.RequirePermission;
import io.brix.platform.auth.annotation.RequireRole;
import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.context.SecurityContextHolder;

/**
 * Permission Check Aspect
 * <p>
 * Intercepts methods annotated with @RequirePermission or @RequireRole,
 * performs permission validation before execution
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
@Aspect
@Order(100)
public class PermissionAspect {

    private static final Logger logger = LoggerFactory.getLogger(PermissionAspect.class);

    private final SecurityContextHolder securityContextHolder;

    public PermissionAspect(SecurityContextHolder securityContextHolder) {
        this.securityContextHolder = securityContextHolder;
    }

    /**
     * Pointcut: method annotated with @RequirePermission
     */
    @Pointcut("@annotation(io.brix.platform.auth.annotation.RequirePermission)")
    public void methodRequirePermission() {}

    /**
     * Pointcut: class annotated with @RequirePermission
     */
    @Pointcut("@within(io.brix.platform.auth.annotation.RequirePermission)")
    public void classRequirePermission() {}

    /**
     * Pointcut: method annotated with @RequireRole
     */
    @Pointcut("@annotation(io.brix.platform.auth.annotation.RequireRole)")
    public void methodRequireRole() {}

    /**
     * Pointcut: class annotated with @RequireRole
     */
    @Pointcut("@within(io.brix.platform.auth.annotation.RequireRole)")
    public void classRequireRole() {}

    /**
     * Check permission - method level or class level
     */
    @Before("methodRequirePermission() || classRequirePermission()")
    public void checkPermission(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // Method-level annotation takes precedence, fallback to class-level annotation
        RequirePermission requirePermission = method.getAnnotation(RequirePermission.class);
        if (requirePermission == null) {
            requirePermission = method.getDeclaringClass().getAnnotation(RequirePermission.class);
        }

        if (requirePermission == null) {
            return;
        }

        AuthenticatedUser user = securityContextHolder.getCurrentUser()
                .orElseThrow(() -> new PermissionDeniedException("User not authenticated"));

        // Super admin bypasses permission check
        if (user.isSuperAdmin()) {
            logger.debug("Super admin bypassed permission check for {}", joinPoint.getSignature());
            return;
        }

        String[] requiredPermissions = requirePermission.value();
        RequirePermission.Logical logical = requirePermission.logical();

        boolean hasPermission = checkPermissions(user, requiredPermissions, logical);

        if (!hasPermission) {
            logger.warn("Permission denied for user {} on {}, required: {} ({})", 
                    user.getUserId(), joinPoint.getSignature(), 
                    String.join(",", requiredPermissions), logical);
            throw new PermissionDeniedException(
                    "Permission denied, required: " + String.join(",", requiredPermissions));
        }
    }

    /**
     * Check role - method level or class level
     */
    @Before("methodRequireRole() || classRequireRole()")
    public void checkRole(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // Method-level annotation takes precedence, fallback to class-level annotation
        RequireRole requireRole = method.getAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = method.getDeclaringClass().getAnnotation(RequireRole.class);
        }

        if (requireRole == null) {
            return;
        }

        AuthenticatedUser user = securityContextHolder.getCurrentUser()
                .orElseThrow(() -> new PermissionDeniedException("User not authenticated"));

        // Super admin bypasses role check
        if (user.isSuperAdmin()) {
            logger.debug("Super admin bypassed role check for {}", joinPoint.getSignature());
            return;
        }

        String[] requiredRoles = requireRole.value();
        RequirePermission.Logical logical = requireRole.logical();

        boolean hasRole = checkRoles(user, requiredRoles, logical);

        if (!hasRole) {
            logger.warn("Role check failed for user {} on {}, required: {} ({})", 
                    user.getUserId(), joinPoint.getSignature(), 
                    String.join(",", requiredRoles), logical);
            throw new PermissionDeniedException(
                    "Role required: " + String.join(",", requiredRoles));
        }
    }

    private boolean checkPermissions(AuthenticatedUser user, String[] permissions, 
            RequirePermission.Logical logical) {
        if (logical == RequirePermission.Logical.AND) {
            return user.hasAllPermissions(permissions);
        } else {
            return user.hasAnyPermission(permissions);
        }
    }

    private boolean checkRoles(AuthenticatedUser user, String[] roles, 
            RequirePermission.Logical logical) {
        if (logical == RequirePermission.Logical.AND) {
            for (String role : roles) {
                if (!user.hasRole(role)) {
                    return false;
                }
            }
            return true;
        } else {
            return user.hasAnyRole(roles);
        }
    }

    /**
     * Permission Denied Exception
     */
    public static class PermissionDeniedException extends RuntimeException {
        
        public PermissionDeniedException(String message) {
            super(message);
        }
    }
}
