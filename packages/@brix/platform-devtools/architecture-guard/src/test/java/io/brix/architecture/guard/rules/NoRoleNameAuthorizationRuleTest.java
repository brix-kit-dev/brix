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
import io.brix.platform.fixture.roles.ChecksRoleThroughHelper;
import io.brix.platform.fixture.roles.ReferencesRoleCheck;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the role-name authorization rule refuses role checks, called or referenced, also in a class that
 * declares its own, and lets permission checks and the classes that implement role checks through.
 */
class NoRoleNameAuthorizationRuleTest {

    @Test
    @DisplayName("A class that authorizes on a role name fails")
    void roleNameCheckFails() {
        assertRejects(ChecksRoleName.class);
    }

    @Test
    @DisplayName("A class that authorizes on role names through a method reference fails")
    void roleNameCheckReferenceFails() {
        assertRejects(ReferencesRoleCheck.class);
    }

    @Test
    @DisplayName("A class that declares its own role check and authorizes with it fails")
    void roleNameCheckThroughOwnHelperFails() {
        assertRejects(ChecksRoleThroughHelper.class);
    }

    @Test
    @DisplayName("A class that authorizes on a permission, and the class that implements the checks, pass")
    void permissionCheckAndImplementationPass() {
        JavaClasses classes = new ClassFileImporter().importClasses(ChecksPermission.class, CallerIdentity.class);

        assertDoesNotThrow(() -> NoRoleNameAuthorizationRule.noRoleNameChecks().check(classes));
    }

    private static void assertRejects(Class<?> violating) {
        JavaClasses classes = new ClassFileImporter().importClasses(violating, CallerIdentity.class);

        AssertionError error = assertThrows(AssertionError.class,
            () -> NoRoleNameAuthorizationRule.noRoleNameChecks().check(classes));
        assertTrue(error.getMessage().contains(violating.getName()), error.getMessage());
    }
}
