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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.brix.platform.fixture.roles.CallerIdentity;
import io.brix.platform.fixture.roles.ChecksPermission;
import io.brix.platform.fixture.roles.ChecksRoleName;
import io.brix.platform.fixture.roles.RoleGuardedOperation;
import io.brix.platform.fixture.roles.RoleGuardedType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the role-name authorization rule refuses role checks and role annotations, and lets permission checks
 * and the classes that implement role checks through.
 */
class NoRoleNameAuthorizationRuleTest {

    @Test
    @DisplayName("A class that authorizes on a role name fails")
    void roleNameCheckFails() {
        JavaClasses classes = new ClassFileImporter().importClasses(ChecksRoleName.class, CallerIdentity.class);

        AssertionError error = assertThrows(AssertionError.class,
            () -> NoRoleNameAuthorizationRule.noRoleNameChecks().check(classes));
        assertTrue(error.getMessage().contains(ChecksRoleName.class.getName()), error.getMessage());
    }

    @Test
    @DisplayName("A class that authorizes on a permission, and the class that implements the checks, pass")
    void permissionCheckAndImplementationPass() {
        JavaClasses classes = new ClassFileImporter().importClasses(ChecksPermission.class, CallerIdentity.class);

        assertDoesNotThrow(() -> NoRoleNameAuthorizationRule.noRoleNameChecks().check(classes));
    }

    @Test
    @DisplayName("A method annotated with @RequireRole fails")
    void roleAnnotatedMethodFails() {
        JavaClasses classes = new ClassFileImporter().importClasses(RoleGuardedOperation.class);

        AssertionError error = assertThrows(AssertionError.class,
            () -> NoRoleNameAuthorizationRule.noRoleAnnotations().check(classes));
        assertTrue(error.getMessage().contains(RoleGuardedOperation.class.getName() + ".run()"), error.getMessage());
    }

    @Test
    @DisplayName("A class annotated with @RequireRole fails")
    void roleAnnotatedClassFails() {
        JavaClasses classes = new ClassFileImporter().importClasses(RoleGuardedType.class);

        AssertionError error = assertThrows(AssertionError.class,
            () -> NoRoleNameAuthorizationRule.noRoleAnnotations().check(classes));
        assertTrue(error.getMessage().contains(RoleGuardedType.class.getName()), error.getMessage());
    }

    @Test
    @DisplayName("Classes without role annotations pass")
    void unannotatedClassesPass() {
        JavaClasses classes = new ClassFileImporter().importClasses(ChecksPermission.class, CallerIdentity.class);

        assertDoesNotThrow(() -> NoRoleNameAuthorizationRule.noRoleAnnotations().check(classes));
    }
}
