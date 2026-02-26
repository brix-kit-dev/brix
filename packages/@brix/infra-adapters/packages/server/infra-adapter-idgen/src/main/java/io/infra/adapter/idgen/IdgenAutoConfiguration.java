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
package io.infra.adapter.idgen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.runtime.sdk.capability.IdGeneratorCapability;

/**
 * Auto Configuration for ID Generator Capability.
 *
 * <p>Provides automatic configuration of {@link IdGeneratorCapability} implementations
 * based on application properties. This follows Spring Boot's auto-configuration
 * conventions for seamless integration.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>This module belongs to Layer 2.5 (Adapter Layer) as defined in the
 * v3.0 Runtime Shell Architecture Blueprint. It implements the capability
 * contract defined in Layer 2 (runtime-sdk-api).</p>
 *
 * <h3>Strategy Selection</h3>
 * <p>The ID generation strategy is selected via configuration:</p>
 * <ul>
 *   <li><b>snowflake</b> (default): High-performance ordered IDs for production</li>
 *   <li><b>uuid</b>: Random UUIDs for scenarios without coordination</li>
 * </ul>
 *
 * <h3>Bean Registration Priority</h3>
 * <ol>
 *   <li>Application-defined {@link IdGeneratorCapability} bean (highest priority)</li>
 *   <li>Snowflake generator (when strategy=snowflake)</li>
 *   <li>UUID generator (when strategy=uuid)</li>
 * </ol>
 *
 * <h3>Configuration Example</h3>
 * <pre>
 * # application.yml
 * brix:
 *   idgen:
 *     strategy: snowflake
 *     snowflake:
 *       worker-id: ${WORKER_ID:1}
 *       datacenter-id: ${DATACENTER_ID:1}
 * </pre>
 *
 * @author Brix Team
 * @version 3.1.0
 * @since 3.1.0
 * @see IdGeneratorCapability
 * @see SnowflakeIdGenerator
 * @see UuidIdGenerator
 */
@AutoConfiguration
@EnableConfigurationProperties(IdgenProperties.class)
public class IdgenAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(IdgenAutoConfiguration.class);

    /**
     * Creates a Snowflake ID generator when strategy is set to "snowflake".
     *
     * <p>This is the recommended strategy for production environments requiring
     * high-performance, time-ordered unique IDs across distributed systems.</p>
     *
     * <h4>Production Deployment Considerations</h4>
     * <ul>
     *   <li>Ensure unique worker-id per instance within same datacenter</li>
     *   <li>Configure datacenter-id based on geographic region</li>
     *   <li>Use environment variables for dynamic assignment in orchestrated environments</li>
     * </ul>
     *
     * @param properties ID generator configuration properties
     * @return Snowflake-based IdGeneratorCapability implementation
     */
    @Bean
    @ConditionalOnMissingBean(IdGeneratorCapability.class)
    @ConditionalOnProperty(
        prefix = "brix.idgen",
        name = "strategy",
        havingValue = "snowflake",
        matchIfMissing = true
    )
    public IdGeneratorCapability snowflakeIdGenerator(IdgenProperties properties) {
        IdgenProperties.SnowflakeConfig config = properties.getSnowflake();
        
        log.info("[IdGen] Creating Snowflake ID Generator with workerId={}, datacenterId={}, epoch={}",
            config.getWorkerId(), config.getDatacenterId(), config.getEpoch());
        
        return new SnowflakeIdGenerator(
            config.getWorkerId(),
            config.getDatacenterId(),
            config.getEpoch()
        );
    }

    /**
     * Creates a UUID ID generator when strategy is set to "uuid".
     *
     * <p>This strategy is suitable for:</p>
     * <ul>
     *   <li>Embedded mode deployments where Snowflake coordination is not available</li>
     *   <li>Development and testing environments</li>
     *   <li>Scenarios where ID ordering is not required</li>
     * </ul>
     *
     * @param properties ID generator configuration properties
     * @return UUID-based IdGeneratorCapability implementation
     */
    @Bean
    @ConditionalOnMissingBean(IdGeneratorCapability.class)
    @ConditionalOnProperty(
        prefix = "brix.idgen",
        name = "strategy",
        havingValue = "uuid"
    )
    public IdGeneratorCapability uuidIdGenerator(IdgenProperties properties) {
        IdgenProperties.UuidConfig config = properties.getUuid();
        
        log.info("[IdGen] Creating UUID ID Generator with compactMode={}",
            config.isCompact());
        
        return new UuidIdGenerator(config.isCompact());
    }
}
