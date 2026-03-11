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
package io.infra.adapter.fallback;

import java.security.Principal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.runtime.sdk.capability.DataScope;

/**
 * Unit tests for {@link FallbackAuthContextCapability}
 *
 * <p>Validates anonymous access mode behavior: anonymous principal, all permissions granted.</p>
 *
 * @author Brix Team
 * @since 3.0.0
 */
@DisplayName("FallbackAuthContextCapability Tests")
class FallbackAuthContextCapabilityTest {

    private FallbackAuthContextCapability authContext;

    @BeforeEach
    void setUp() {
        authContext = new FallbackAuthContextCapability();
    }

    @Test
    @DisplayName("getCurrentPrincipal - should return anonymous principal")
    void getCurrentPrincipal_shouldReturnAnonymousPrincipal() {
        Principal principal = authContext.getCurrentPrincipal();

        assertThat(principal).isNotNull();
        assertThat(principal.getName()).isEqualTo("anonymous");
    }

    @Test
    @DisplayName("getCurrentPrincipal - multiple calls should return the same instance")
    void getCurrentPrincipal_shouldReturnSameInstance() {
        Principal p1 = authContext.getCurrentPrincipal();
        Principal p2 = authContext.getCurrentPrincipal();

        assertThat(p1).isSameAs(p2);
    }

    @Test
    @DisplayName("hasPermission - should always return true for any permission")
    void hasPermission_shouldAlwaysReturnTrue() {
        assertThat(authContext.hasPermission("admin")).isTrue();
        assertThat(authContext.hasPermission("read")).isTrue();
        assertThat(authContext.hasPermission("write")).isTrue();
        assertThat(authContext.hasPermission(null)).isTrue();
    }

    @Test
    @DisplayName("hasRole - should always return true for any role")
    void hasRole_shouldAlwaysReturnTrue() {
        assertThat(authContext.hasRole("ADMIN")).isTrue();
        assertThat(authContext.hasRole("USER")).isTrue();
        assertThat(authContext.hasRole(null)).isTrue();
    }

    @Test
    @DisplayName("getAuthorizedScopes - should return a set containing all scope")
    void getAuthorizedScopes_shouldReturnAllScope() {
        Set<DataScope> scopes = authContext.getAuthorizedScopes();

        assertThat(scopes)
            .isNotNull()
            .hasSize(1);
        assertThat(scopes.iterator().next()).isEqualTo(DataScope.all());
    }
}
