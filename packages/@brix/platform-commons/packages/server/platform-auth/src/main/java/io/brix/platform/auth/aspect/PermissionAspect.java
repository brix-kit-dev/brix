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
 * 权限检查切
 * <p>
 * 拦截带有 @RequirePermission @RequireRole 注解的方法，
 * 在执行前进行权限校验
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
     * 切入点：方法上标注了 @RequirePermission
     */
    @Pointcut("@annotation(io.brix.platform.auth.annotation.RequirePermission)")
    public void methodRequirePermission() {}

    /**
     * 切入点：类上标注@RequirePermission
     */
    @Pointcut("@within(io.brix.platform.auth.annotation.RequirePermission)")
    public void classRequirePermission() {}

    /**
     * 切入点：方法上标注了 @RequireRole
     */
    @Pointcut("@annotation(io.brix.platform.auth.annotation.RequireRole)")
    public void methodRequireRole() {}

    /**
     * 切入点：类上标注@RequireRole
     */
    @Pointcut("@within(io.brix.platform.auth.annotation.RequireRole)")
    public void classRequireRole() {}

    /**
     * 检查权- 方法级别或类级别
     */
    @Before("methodRequirePermission() || classRequirePermission()")
    public void checkPermission(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // 优先取方法上的注解，其次取类上的注解
        RequirePermission requirePermission = method.getAnnotation(RequirePermission.class);
        if (requirePermission == null) {
            requirePermission = method.getDeclaringClass().getAnnotation(RequirePermission.class);
        }

        if (requirePermission == null) {
            return;
        }

        AuthenticatedUser user = securityContextHolder.getCurrentUser()
                .orElseThrow(() -> new PermissionDeniedException("User not authenticated"));

        // 超级管理员跳过权限检
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
     * 检查角- 方法级别或类级别
     */
    @Before("methodRequireRole() || classRequireRole()")
    public void checkRole(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // 优先取方法上的注解，其次取类上的注解
        RequireRole requireRole = method.getAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = method.getDeclaringClass().getAnnotation(RequireRole.class);
        }

        if (requireRole == null) {
            return;
        }

        AuthenticatedUser user = securityContextHolder.getCurrentUser()
                .orElseThrow(() -> new PermissionDeniedException("User not authenticated"));

        // 超级管理员跳过角色检
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
     * 权限拒绝异常
     */
    public static class PermissionDeniedException extends RuntimeException {
        
        public PermissionDeniedException(String message) {
            super(message);
        }
    }
}
