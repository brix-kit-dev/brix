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
 * {@link FallbackAuthContextCapability} 单元测试
 *
 * <p>验证匿名访问模式的行为：匿名主体、所有权限放行。</p>
 *
 * @author Brix Team
 * @since 3.0.0
 */
@DisplayName("FallbackAuthContextCapability 测试")
class FallbackAuthContextCapabilityTest {

    private FallbackAuthContextCapability authContext;

    @BeforeEach
    void setUp() {
        authContext = new FallbackAuthContextCapability();
    }

    @Test
    @DisplayName("getCurrentPrincipal - 应返回匿名主体")
    void getCurrentPrincipal_shouldReturnAnonymousPrincipal() {
        Principal principal = authContext.getCurrentPrincipal();

        assertThat(principal).isNotNull();
        assertThat(principal.getName()).isEqualTo("anonymous");
    }

    @Test
    @DisplayName("getCurrentPrincipal - 多次调用应返回同一实例")
    void getCurrentPrincipal_shouldReturnSameInstance() {
        Principal p1 = authContext.getCurrentPrincipal();
        Principal p2 = authContext.getCurrentPrincipal();

        assertThat(p1).isSameAs(p2);
    }

    @Test
    @DisplayName("hasPermission - 任何权限均应返回 true")
    void hasPermission_shouldAlwaysReturnTrue() {
        assertThat(authContext.hasPermission("admin")).isTrue();
        assertThat(authContext.hasPermission("read")).isTrue();
        assertThat(authContext.hasPermission("write")).isTrue();
        assertThat(authContext.hasPermission(null)).isTrue();
    }

    @Test
    @DisplayName("hasRole - 任何角色均应返回 true")
    void hasRole_shouldAlwaysReturnTrue() {
        assertThat(authContext.hasRole("ADMIN")).isTrue();
        assertThat(authContext.hasRole("USER")).isTrue();
        assertThat(authContext.hasRole(null)).isTrue();
    }

    @Test
    @DisplayName("getAuthorizedScopes - 应返回包含 all 范围的集合")
    void getAuthorizedScopes_shouldReturnAllScope() {
        Set<DataScope> scopes = authContext.getAuthorizedScopes();

        assertThat(scopes)
            .isNotNull()
            .hasSize(1);
        assertThat(scopes.iterator().next()).isEqualTo(DataScope.all());
    }
}
