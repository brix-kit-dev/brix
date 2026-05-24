package io.brix.platform.auth.aspect;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.brix.platform.auth.PlatformPermissions;
import io.brix.platform.auth.RoleCode;
import io.brix.platform.auth.annotation.RequirePermission;
import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.context.SecurityContextHolder;
import io.brix.platform.auth.enums.TokenRole;
import io.brix.platform.auth.enums.TokenType;

class PermissionAspectBootstrapTest {

    private final SecurityContextHolder securityContextHolder = new SecurityContextHolder();
    private final PermissionAspect aspect = new PermissionAspect(securityContextHolder);

    @AfterEach
    void clearContext() {
        securityContextHolder.clear();
    }

    @Test
    void bootstrapSetupTokenCanOnlySatisfyBootstrapPermissions() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser();
        user.setUserId("1");
        user.setTokenType(TokenType.BOOTSTRAP_SETUP);
        user.setTokenRole(TokenRole.BOOTSTRAP);
        user.setPlatformRole(RoleCode.BOOTSTRAP);
        user.setScope(RoleCode.BOOTSTRAP);
        user.setPermissions(PlatformPermissions.defaultPermissionsFor(RoleCode.BOOTSTRAP));
        securityContextHolder.setCurrentUser(user);

        assertDoesNotThrow(() -> aspect.checkPermission(joinPointFor("bootstrapEndpoint")));
        assertThrows(PermissionAspect.PermissionDeniedException.class,
                () -> aspect.checkPermission(joinPointFor("platformAdminEndpoint")));
    }

    private static JoinPoint joinPointFor(String methodName) throws NoSuchMethodException {
        Method method = ProtectedEndpoints.class.getDeclaredMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        JoinPoint joinPoint = mock(JoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        return joinPoint;
    }

    static class ProtectedEndpoints {
        @RequirePermission(PlatformPermissions.BOOTSTRAP_CREATE_FIRST_ADMIN)
        void bootstrapEndpoint() {
        }

        @RequirePermission(PlatformPermissions.ADMIN_READ)
        void platformAdminEndpoint() {
        }
    }
}