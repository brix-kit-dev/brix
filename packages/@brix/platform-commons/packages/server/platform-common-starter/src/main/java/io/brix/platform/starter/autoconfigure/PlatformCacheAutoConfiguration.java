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
package io.brix.platform.starter.autoconfigure;

import io.brix.platform.starter.cache.PlatformCacheProperties;
import io.brix.platform.starter.cache.StateStoreCacheManager;
import io.runtime.sdk.capability.StateStoreCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the platform cache layer.
 *
 * <p>Bridges the standard Spring Cache abstraction ({@code @Cacheable},
 * {@code @CacheEvict}, {@code @CachePut}) to the Runtime Shell's
 * {@link StateStoreCapability}, enabling transparent caching for plugin
 * services without direct middleware dependencies.</p>
 *
 * <h3>Activation Conditions</h3>
 * <ol>
 *   <li>{@code brix.cache.enabled=true} (default: true)</li>
 *   <li>A {@link StateStoreCapability} bean must be present in the context
 *       (provided by the Host assembly via infra-adapter-redis or infra-adapter-simple)</li>
 *   <li>No other {@link CacheManager} bean already exists (respects user overrides)</li>
 * </ol>
 *
 * <h3>Architecture Position</h3>
 * <pre>
 * Layer 2C (platform-common-starter) — this auto-configuration
 *   └─ registers StateStoreCacheManager
 *        └─ delegates to StateStoreCapability (Layer 2A contract)
 *             └─ implemented by Redis/InMemory adapter (Layer 2C infra-adapters)
 * </pre>
 *
 * <h3>How Plugins Use Caching</h3>
 * <p>Plugin services simply annotate read-heavy methods:</p>
 * <pre>{@code
 * @Cacheable(cacheNames = "users", key = "#userId")
 * public UserDTO getByUserId(String userId) { ... }
 *
 * @CacheEvict(cacheNames = "users", key = "#userId")
 * public void updateUser(String userId, ...) { ... }
 * }</pre>
 *
 * <p>The Host assembly provides the actual cache implementation via
 * StateStoreCapability. Plugins never import Redis, Caffeine, or any
 * cache library.</p>
 *
 * @author Brix Platform Team
 * @since 3.0.9
 * @see StateStoreCacheManager
 * @see PlatformCacheProperties
 * @see StateStoreCapability
 */
@AutoConfiguration
@EnableCaching
@ConditionalOnProperty(prefix = "brix.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(StateStoreCapability.class)
@EnableConfigurationProperties(PlatformCacheProperties.class)
public class PlatformCacheAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PlatformCacheAutoConfiguration.class);

    /**
     * Registers a {@link CacheManager} backed by the Runtime Shell's
     * {@link StateStoreCapability}.
     *
     * <p><b>Conditional activation:</b> This bean is only created when:</p>
     * <ul>
     *   <li>A {@link StateStoreCapability} bean exists (i.e., an infra-adapter has
     *       provided Redis or InMemory state store)</li>
     *   <li>No other {@link CacheManager} bean has been registered (allows user
     *       overrides in custom configurations)</li>
     * </ul>
     *
     * @param stateStore the state store capability from the Runtime Shell
     * @param properties the cache configuration properties
     * @return the configured StateStoreCacheManager
     */
    @Bean
    @ConditionalOnBean(StateStoreCapability.class)
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager stateStoreCacheManager(
            StateStoreCapability stateStore,
            PlatformCacheProperties properties) {
        log.info("[PlatformCacheAutoConfiguration] Registering StateStoreCacheManager " +
                 "(defaultTtl={})", properties.getDefaultTtl());
        return new StateStoreCacheManager(stateStore, properties.getDefaultTtl());
    }
}
