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
package io.brix.platform.tenant.aspect;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.brix.platform.tenant.annotation.CrossTenantAccess;
import io.brix.platform.tenant.interceptor.TenantSqlGuardInterceptor;

/**
 * AOP bridge between {@link CrossTenantAccess} annotation and
 * {@link TenantSqlGuardInterceptor} thread-local scope.
 *
 * <p>This aspect activates the cross-tenant scope around any invocation of a
 * method or class annotated with {@link CrossTenantAccess}, so the SQL guard
 * interceptor permits queries that legitimately span tenants (e.g. platform
 * audit log writes triggered from {@code /api/platform/**} endpoints with no
 * tenant context).</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C (platform-tenant) — completes the cross-cutting tenant
 * isolation mechanism whose annotation contract was previously declared but
 * lacked a runtime aspect.</p>
 *
 * <h3>Ordering</h3>
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} so the cross-tenant scope is
 * established <em>before</em> any transactional / repository advice executes
 * SQL through Hibernate.</p>
 *
 * <h3>Reentrancy</h3>
 * <p>If a caller is already in a cross-tenant scope (e.g. nested annotated
 * method), the aspect leaves the existing scope untouched and skips both
 * enter/exit operations to avoid premature scope removal on the inner exit.</p>
 *
 * @see CrossTenantAccess
 * @see TenantSqlGuardInterceptor#enterCrossTenantScope(String, String, boolean)
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CrossTenantAccessAspect {

    private static final Logger log = LoggerFactory.getLogger(CrossTenantAccessAspect.class);

    /**
     * Around-advice for methods or classes annotated with {@link CrossTenantAccess}.
     *
     * <p>The pointcut covers both annotation targets:
     * <ul>
     *   <li>{@code @annotation(...)} — method-level usage</li>
     *   <li>{@code @within(...)} — class-level usage</li>
     * </ul>
     * Method-level annotations take precedence when both are present.
     */
    @Around("@annotation(io.brix.platform.tenant.annotation.CrossTenantAccess) "
            + "|| @within(io.brix.platform.tenant.annotation.CrossTenantAccess)")
    public Object aroundCrossTenantAccess(ProceedingJoinPoint pjp) throws Throwable {
        CrossTenantAccess annotation = resolveAnnotation(pjp);
        String reason = annotation != null ? annotation.reason() : "unspecified";
        String approval = annotation != null ? annotation.approval() : "unspecified";
        boolean readOnly = annotation != null && annotation.readOnly();

        if (reason.isBlank() || approval.isBlank()) {
            throw new IllegalStateException("@CrossTenantAccess requires non-blank reason and approval: "
                    + pjp.getSignature().toShortString());
        }

        boolean alreadyActive = TenantSqlGuardInterceptor.isInCrossTenantScope();
        if (alreadyActive) {
            return pjp.proceed();
        }

        TenantSqlGuardInterceptor.enterCrossTenantScope(reason, approval, readOnly);
        if (log.isDebugEnabled()) {
            log.debug("Entered @CrossTenantAccess scope: target={}.{} reason={} approval={} readOnly={}",
                    pjp.getSignature().getDeclaringTypeName(),
                    pjp.getSignature().getName(),
                    reason,
                    approval,
                    readOnly);
        }
        try {
            return pjp.proceed();
        } finally {
            TenantSqlGuardInterceptor.exitCrossTenantScope();
        }
    }

    /**
     * Resolve the effective {@link CrossTenantAccess} annotation, preferring
     * method-level over class-level when both are present.
     */
    private CrossTenantAccess resolveAnnotation(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        CrossTenantAccess methodAnnotation = method.getAnnotation(CrossTenantAccess.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        Class<?> targetClass = pjp.getTarget() != null ? pjp.getTarget().getClass()
                : signature.getDeclaringType();
        return targetClass.getAnnotation(CrossTenantAccess.class);
    }
}
