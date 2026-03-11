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
package io.brix.platform.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway Route Configuration Properties
 * <p>
 * Supports customizing Redis key prefix and other parameters via configuration file.
 * </p>
 * 
 * <p><b>[v3.1 Brand Name Isolation]</b></p>
 * <p>
 * Changed hardcoded `brix:gateway:routes` to configurable prefix,
 * supporting different Redis key namespaces for different deployment environments.
 * </p>
 * 
 * <p><b>Configuration Example:</b></p>
 * <pre>
 * brix:
 *   gateway:
 *     routes:
 *       key-prefix: "myapp:gateway:routes"
 *       event-channel: "myapp:gateway:routes:event"
 * </pre>
 * 
 * @author Brix Team
 * @version 3.1.0
 * @see GatewayRouteConfig
 * @see RedisRouteRefreshSubscriber
 */
@ConfigurationProperties(prefix = "brix.gateway.routes")
public class GatewayRoutesProperties {

    /**
     * Redis Hash Key for storing dynamic routes
     * <p>
     * Default: brix:gateway:routes
     * </p>
     */
    private String keyPrefix = "brix:gateway:routes";

    /**
     * Redis Pub/Sub channel for route refresh events
     * <p>
     * Default: brix:gateway:routes:event
     * </p>
     */
    private String eventChannel = "brix:gateway:routes:event";

    /**
     * Log prefix identifier
     * <p>
     * Default: [brix]
     * </p>
     */
    private String logPrefix = "[brix]";

    // ========================================================================
    // Getters & Setters
    // ========================================================================

    /**
     * Get Redis Hash Key prefix
     * 
     * @return Redis key prefix
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * Set Redis Hash Key prefix
     * 
     * @param keyPrefix Redis key prefix
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /**
     * Get Redis event channel name
     * 
     * @return event channel name
     */
    public String getEventChannel() {
        return eventChannel;
    }

    /**
     * Set Redis event channel name
     * 
     * @param eventChannel event channel name
     */
    public void setEventChannel(String eventChannel) {
        this.eventChannel = eventChannel;
    }

    /**
     * Get log prefix
     * 
     * @return log prefix
     */
    public String getLogPrefix() {
        return logPrefix;
    }

    /**
     * Set log prefix
     * 
     * @param logPrefix log prefix
     */
    public void setLogPrefix(String logPrefix) {
        this.logPrefix = logPrefix;
    }
}
