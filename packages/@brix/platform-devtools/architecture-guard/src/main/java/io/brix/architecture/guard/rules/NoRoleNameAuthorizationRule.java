/*
 * Copyright 2026 Brix Authors
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
package io.brix.architecture.guard.rules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;

import java.util.Set;

/**
 * Authorization checks permissions, not role names: the super-admin 4.0.1 permission model lets a request through on
 * a specific permission and never on a role-name string.
 *
 * <p>The rule refuses role-name authorization in the analyzed code: a class or method annotated with platform-auth's
 * {@code @RequireRole}, and a call to {@code hasRole} or {@code hasAnyRole}. The classes that implement those checks
 * are not callers: a class that declares {@code hasRole} or {@code hasAnyRole} itself, and platform-auth's
 * {@code PermissionAspect}, which enforces the annotation.</p>
 *
 * <p>It replaces {@code NoStringRoleLiteralRule}, which never checked anything: {@code noClasses()} inverted its
 * condition, and it looked for literals in {@code getSourceCodeLocation().toString()}, which is only "File.java:line".
 * A role literal cannot be told from a {@code RoleCode} constant in bytecode anyway, since javac inlines constants.</p>
 *
 * @author Brix Architecture Team
 * @since 3.3.0
 */
public final class NoRoleNameAuthorizationRule {

    /** platform-auth's role annotation. */
    public static final String REQUIRE_ROLE = "io.brix.platform.auth.annotation.RequireRole";

    /** platform-auth's interceptor that enforces {@link #REQUIRE_ROLE}. */
    private static final String ROLE_ASPECT = "io.brix.platform.auth.aspect.PermissionAspect";

    private static final Set<String> ROLE_CHECKS = Set.of("hasRole", "hasAnyRole");

    private NoRoleNameAuthorizationRule() {
    }

    /**
     * Refuses {@code @RequireRole} on classes and methods.
     *
     * @return the rule
     */
    public static ArchRule noRoleAnnotations() {
        String reason = "authorization checks a specific permission, never a role name (super-admin 4.0.1 permission "
                + "model); use @RequirePermission";
        return CompositeArchRule
                .of(noClasses().should().beAnnotatedWith(REQUIRE_ROLE).because(reason))
                // A class without methods leaves nothing for the method half to check, which is fine.
                .and(noMethods().should().beAnnotatedWith(REQUIRE_ROLE).because(reason).allowEmptyShould(true));
    }

    /**
     * Refuses calls to {@code hasRole} and {@code hasAnyRole} from classes that do not implement them.
     *
     * @return the rule
     */
    public static ArchRule noRoleNameChecks() {
        return noClasses()
                .that(DescribedPredicate.describe("do not implement role checks themselves",
                        type -> !implementsRoleChecks(outermost(type))))
                .should().callMethodWhere(DescribedPredicate.<JavaMethodCall>describe(
                        "the target is a role-name check (hasRole, hasAnyRole)",
                        call -> ROLE_CHECKS.contains(call.getName())))
                .because("authorization checks a specific permission, never a role name (super-admin 4.0.1 "
                        + "permission model)");
    }

    private static boolean implementsRoleChecks(JavaClass type) {
        return type.getName().equals(ROLE_ASPECT)
                || type.getMethods().stream().anyMatch(method -> ROLE_CHECKS.contains(method.getName()));
    }

    private static JavaClass outermost(JavaClass type) {
        JavaClass current = type;
        while (current.getEnclosingClass().isPresent()) {
            current = current.getEnclosingClass().get();
        }
        return current;
    }
}
