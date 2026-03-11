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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis Route Refresh Event Subscriber
 * <p>
 * Listens to route refresh events published by Redis, triggering Spring Cloud Gateway to reload route definitions.
 * This solves the problem of gateway not automatically refreshing after dynamic route registration.
 * </p>
 * 
 * <p><b>Workflow:</b></p>
 * <ol>
 *   <li>After Plugin Engine registers/updates routes, it publishes a message to Redis channel</li>
 *   <li>This listener receives the message, publishes Spring Cloud Gateway RefreshRoutesEvent</li>
 *   <li>Gateway CachingRouteLocator receives the event and re-reads RouteDefinitionRepository</li>
 *   <li>Loads the latest route definitions from Redis</li>
 * </ol>
 * 
 * <p><b>[v3.1 Brand Name Isolation]</b></p>
 * <p>
 * Redis channel name now supports customization via configuration file, defaults to &quot;brix:gateway:routes:event&quot;.
 * Configuration: {@code brix.gateway.routes.event-channel}
 * </p>
 * 
 * @author Brix Team
 * @version 3.1.0
 * @see GatewayRoutesProperties
 */
@Configuration
public class RedisRouteRefreshSubscriber {

    private static final Logger logger = LoggerFactory.getLogger(RedisRouteRefreshSubscriber.class);

    private final ApplicationEventPublisher eventPublisher;
    private final GatewayRoutesProperties properties;

    /**
     * Constructor
     * 
     * @param eventPublisher Spring event publisher
     * @param properties gateway route configuration properties
     */
    public RedisRouteRefreshSubscriber(
            ApplicationEventPublisher eventPublisher,
            GatewayRoutesProperties properties) {
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /**
     * Configure Redis message listener container
     * 
     * @param connectionFactory Redis connection factory
     * @return Redis message listener container
     */
    @Bean("brixRouteRefreshListenerContainer")
    public RedisMessageListenerContainer brixRouteRefreshListenerContainer(
            RedisConnectionFactory connectionFactory) {
        
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        String eventChannel = properties.getEventChannel();
        String logPrefix = properties.getLogPrefix();
        
        // Subscribe to route refresh channel
        container.addMessageListener(brixRouteRefreshMessageListener(), new ChannelTopic(eventChannel));
        
        logger.info("{} Redis route refresh listener registered on channel: {}", logPrefix, eventChannel);
        return container;
    }

    /**
     * Route refresh message handler
     * 
     * @return message listener
     */
    @Bean("brixRouteRefreshMessageListener")
    public MessageListener brixRouteRefreshMessageListener() {
        final String logPrefix = properties.getLogPrefix();
        
        return new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                String messageBody = new String(message.getBody());
                logger.info("{} Received route refresh event: {}", logPrefix, messageBody);
                
                // Handle quotes that may come from JSON serialization (e.g. "refresh" -> refresh)
                String normalizedMessage = messageBody.trim();
                if (normalizedMessage.startsWith("\"") && normalizedMessage.endsWith("\"")) {
                    normalizedMessage = normalizedMessage.substring(1, normalizedMessage.length() - 1);
                }
                
                if ("refresh".equals(normalizedMessage)) {
                    // Publish Spring Cloud Gateway refresh event
                    eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                    logger.info("{} Triggered route refresh via RefreshRoutesEvent", logPrefix);
                }
            }
        };
    }
}
