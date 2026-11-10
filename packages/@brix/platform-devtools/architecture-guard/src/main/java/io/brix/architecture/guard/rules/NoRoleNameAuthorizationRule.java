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

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.lang.ArchRule;

import java.util.Set;

/**
 * Authorization checks permissions, not role names: the super-admin 4.0.1 permission model lets a request through on
 * a specific permission and never on a role-name string (BRX-SA-AUTH-006), and a tenant endpoint decides on
 * permission codes only (multi-tenant T-29).
 *
 * <p>The rule refuses a call to, or a method reference of, {@code hasRole} or {@code hasAnyRole} in the analyzed code,
 * whatever declares the method: the SDK's {@code AuthCapability.hasRole}, deprecated since 3.3.0 for removal in
 * 4.0.0, or any other role check. Declaring such a method, as an implementation of the SDK interface does, is not a
 * call. The commons profile runs it on platform code, the plugin profile on plugins.</p>
 *
 * <p>It replaces {@code NoStringRoleLiteralRule}, which never checked anything: {@code noClasses()} inverted its
 * condition, and it looked for literals in {@code getSourceCodeLocation().toString()}, which is only "File.java:line".
 * A role literal cannot be told from a {@code RoleCode} constant in bytecode anyway, since javac inlines constants.</p>
 *
 * @author Brix Architecture Team
 * @since 3.3.0
 */
public final class NoRoleNameAuthorizationRule {

    private static final Set<String> ROLE_CHECKS = Set.of("hasRole", "hasAnyRole");

    private NoRoleNameAuthorizationRule() {
    }

    /**
     * Refuses calls to, and method references of, {@code hasRole} and {@code hasAnyRole}.
     *
     * @return the rule
     */
    public static ArchRule noRoleNameChecks() {
        return noClasses()
                .should().accessTargetWhere(DescribedPredicate.<JavaAccess<?>>describe(
                        "the target is a role-name check (hasRole, hasAnyRole), called or referenced",
                        access -> (access instanceof JavaMethodCall || access instanceof JavaMethodReference)
                                && ROLE_CHECKS.contains(access.getName())))
                .because("authorization checks a specific permission, never a role name (super-admin "
                        + "BRX-SA-AUTH-006, multi-tenant T-29); use hasPermission or hasAnyPermission");
    }
}
