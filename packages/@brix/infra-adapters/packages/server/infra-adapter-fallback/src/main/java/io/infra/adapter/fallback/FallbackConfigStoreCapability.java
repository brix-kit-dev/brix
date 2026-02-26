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

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.ConfigStoreCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * Fallback 配置存储能力实现
 * 
 * <p>基于环境变量和系统属性的简单配置实现。</p>
 * 
 * <p>配置查找顺序：</p>
 * <ol>
 *   <li>系统属性（System.getProperty）</li>
 *   <li>环境变量（System.getenv），key 转换为 UPPER_SNAKE_CASE</li>
 * </ol>
 * 
 * @author Brix Team
 * @version 3.0.0
 */
@Capability(
    type = ConfigStoreCapability.class,
    name = "fallback-config-store",
    description = "基于系统属性和环境变量的 Fallback 配置存储实现",
    level = CapabilityLevel.EXPERIMENTAL,
    aliases = {"fallbackConfig"}
)
public class FallbackConfigStoreCapability implements ConfigStoreCapability {

    private static final Logger log = LoggerFactory.getLogger(FallbackConfigStoreCapability.class);

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        // 1. 先尝试系统属性
        String value = System.getProperty(key);
        
        // 2. 再尝试环境变量（转换为 UPPER_SNAKE_CASE）
        if (value == null) {
            String envKey = key.replace(".", "_").replace("-", "_").toUpperCase();
            value = System.getenv(envKey);
        }
        
        if (value == null) {
            return Optional.empty();
        }
        
        try {
            if (type == String.class) {
                return Optional.of((T) value);
            }
            if (type == Integer.class || type == int.class) {
                return Optional.of((T) Integer.valueOf(value));
            }
            if (type == Long.class || type == long.class) {
                return Optional.of((T) Long.valueOf(value));
            }
            if (type == Boolean.class || type == boolean.class) {
                return Optional.of((T) Boolean.valueOf(value));
            }
            if (type == Double.class || type == double.class) {
                return Optional.of((T) Double.valueOf(value));
            }
        } catch (NumberFormatException e) {
            log.warn("[Fallback] 无法将配置值 '{}' 转换为类型 {}", value, type.getSimpleName());
        }
        
        return Optional.empty();
    }
}
