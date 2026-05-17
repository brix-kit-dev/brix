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
package io.brix.platform.starter.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the platform cache layer.
 *
 * <p>These properties control the behavior of the {@link StateStoreCacheManager},
 * which bridges Spring Cache abstraction to the Runtime Shell's
 * {@link io.runtime.sdk.capability.StateStoreCapability}.</p>
 *
 * <h3>Configuration Example</h3>
 * <pre>{@code
 * # application.yml
 * brix:
 *   cache:
 *     enabled: true
 *     default-ttl: PT30M    # 30 minutes (ISO-8601 duration format)
 * }</pre>
 *
 * <h3>TTL Guidelines</h3>
 * <ul>
 *   <li>Read-heavy, slowly-changing data (users, tenants): 30–60 minutes</li>
 *   <li>Reference data (products, categories): 15–30 minutes</li>
 *   <li>Frequently-changing data: shorter TTL or explicit @CacheEvict</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.0.9
 * @see StateStoreCacheManager
 */
@ConfigurationProperties(prefix = "brix.cache")
public class PlatformCacheProperties {

    /**
     * Whether Spring Cache integration is enabled.
     *
     * <p>When disabled, {@code @Cacheable} annotations become no-ops and no
     * {@link org.springframework.cache.CacheManager} bean is registered.</p>
     */
    private boolean enabled = true;

    /**
     * Default time-to-live for cache entries (ISO-8601 duration format).
     *
     * <p>Applied uniformly to all cache regions managed by
     * {@link StateStoreCacheManager}. Defaults to 30 minutes.</p>
     *
     * <p>Examples: {@code PT5M} (5 min), {@code PT30M} (30 min), {@code PT1H} (1 hour)</p>
     */
    private Duration defaultTtl = Duration.ofMinutes(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }
}
