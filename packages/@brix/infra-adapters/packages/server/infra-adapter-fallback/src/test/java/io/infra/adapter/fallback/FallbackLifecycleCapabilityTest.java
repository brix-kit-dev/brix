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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.runtime.sdk.capability.HealthStatus;
import io.runtime.sdk.capability.ModuleMetadata;
import io.runtime.sdk.context.RuntimeContext;

/**
 * {@link FallbackLifecycleCapability} 单元测试
 *
 * <p>验证空操作生命周期实现：所有回调不抛异常，
 * healthCheck 始终 UP，metadata 结构正确。</p>
 *
 * @author Brix Team
 * @since 3.0.0
 */
@DisplayName("FallbackLifecycleCapability 测试")
class FallbackLifecycleCapabilityTest {

    private FallbackLifecycleCapability lifecycle;

    @BeforeEach
    void setUp() {
        lifecycle = new FallbackLifecycleCapability();
    }

    // ==================== 生命周期回调 ====================

    @Test
    @DisplayName("onInit - 应正常执行不抛异常")
    void onInit_shouldNotThrow() {
        RuntimeContext context = Mockito.mock(RuntimeContext.class);

        assertThatNoException().isThrownBy(() -> lifecycle.onInit(context));
    }

    @Test
    @DisplayName("onStart - 应正常执行不抛异常")
    void onStart_shouldNotThrow() {
        assertThatNoException().isThrownBy(() -> lifecycle.onStart());
    }

    @Test
    @DisplayName("onStop - 应正常执行不抛异常")
    void onStop_shouldNotThrow() {
        assertThatNoException().isThrownBy(() -> lifecycle.onStop());
    }

    @Test
    @DisplayName("onDestroy - 应正常执行不抛异常")
    void onDestroy_shouldNotThrow() {
        assertThatNoException().isThrownBy(() -> lifecycle.onDestroy());
    }

    // ==================== healthCheck ====================

    @Test
    @DisplayName("healthCheck - 应始终返回 UP")
    void healthCheck_shouldReturnUp() {
        HealthStatus status = lifecycle.healthCheck();

        assertThat(status).isEqualTo(HealthStatus.UP);
        assertThat(status.isHealthy()).isTrue();
    }

    // ==================== getMetadata ====================

    @Test
    @DisplayName("getMetadata - 应返回有效的模块元数据")
    void getMetadata_shouldReturnValidMetadata() {
        ModuleMetadata metadata = lifecycle.getMetadata();

        assertThat(metadata).isNotNull();
        assertThat(metadata.getModuleId()).isEqualTo("fallback-module");
        assertThat(metadata.getModuleName()).isEqualTo("Fallback Module");
        assertThat(metadata.getVersion()).isEqualTo("3.0.0");
        assertThat(metadata.getDescription()).isNotBlank();
    }
}
