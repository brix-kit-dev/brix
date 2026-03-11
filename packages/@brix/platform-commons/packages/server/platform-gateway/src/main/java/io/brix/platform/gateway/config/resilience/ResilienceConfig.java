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
package io.brix.platform.gateway.config.resilience;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import jakarta.annotation.PostConstruct;

/**
 * Gatewayresilienceconfiguration
 * <p>
 * MVP Red Line Requirements
 * <ul>
 *   <li>Redis/HTTP explicit timeouts</li>
 *   <li>haslimitretry（most3 times）</li>
 * </ul>
 * </p>
 *
 * <h3>Configuration Hierarchy</h3>
 * <pre>
 * gateway.resilience.http.*    HTTP downstream call timeout
 * gateway.resilience.retry.*   Retry strategy
 * gateway.resilience.redis.*   Redis operationtimeoutwithre-
 * </pre>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@Configuration
@EnableConfigurationProperties({
    HttpTimeoutProperties.class,
    RetryProperties.class,
    RedisResilienceProperties.class
})
public class ResilienceConfig {

    private static final Logger logger = LoggerFactory.getLogger(ResilienceConfig.class);

    private final HttpTimeoutProperties httpTimeoutProperties;
    private final RetryProperties retryProperties;
    private final RedisResilienceProperties redisProperties;

    public ResilienceConfig(HttpTimeoutProperties httpTimeoutProperties,
                           RetryProperties retryProperties,
                           RedisResilienceProperties redisProperties) {
        this.httpTimeoutProperties = httpTimeoutProperties;
        this.retryProperties = retryProperties;
        this.redisProperties = redisProperties;
    }

    @PostConstruct
    public void logConfiguration() {
        logger.info("[brix] Gateway Resilience Configuration:");
        logger.info("[brix]   HTTP Timeout enabled={}", httpTimeoutProperties.isEnabled());
        if (httpTimeoutProperties.isEnabled()) {
            logger.info("[brix]     connect-timeout={}ms, response-timeout={}ms, global-timeout={}ms",
                httpTimeoutProperties.getConnectTimeoutMs(),
                httpTimeoutProperties.getResponseTimeoutMs(),
                httpTimeoutProperties.getGlobalTimeoutMs());
        }
        
        logger.info("[brix]   Retry enabled={}", retryProperties.isEnabled());
        if (retryProperties.isEnabled()) {
            logger.info("[brix]     max-attempts={}, initial-backoff={}ms, max-backoff={}ms, jitter={}",
                retryProperties.getMaxAttempts(),
                retryProperties.getInitialBackoffMs(),
                retryProperties.getMaxBackoffMs(),
                retryProperties.isJitterEnabled());
        }
        
        logger.info("[brix]   Redis Resilience enabled={}", redisProperties.isEnabled());
        if (redisProperties.isEnabled()) {
            logger.info("[brix]     command-timeout={}ms, connect-timeout={}ms, max-attempts={}",
                redisProperties.getCommandTimeoutMs(),
                redisProperties.getConnectTimeoutMs(),
                redisProperties.getMaxAttempts());
        }
    }

    /**
     * configuration Spring Cloud Gateway HTTP clientproperty
     * <p>
     * willselfdefinitiontimeoutconfigurationapplicationGateway Netty HttpClient
     * </p>
     */
    @Bean("gatewayHttpClientProperties")
    @Primary
    public HttpClientProperties gatewayHttpClientProperties() {
        HttpClientProperties properties = new HttpClientProperties();
        
        if (httpTimeoutProperties.isEnabled()) {
            // Set connection timeout
            HttpClientProperties.Pool pool = new HttpClientProperties.Pool();
            properties.setPool(pool);
            
            // Set response timeout
            properties.setResponseTimeout(
                Duration.ofMillis(httpTimeoutProperties.getResponseTimeoutMs())
            );
            
            // Set connection timeout
            properties.setConnectTimeout(httpTimeoutProperties.getConnectTimeoutMs());
            
            logger.debug("[brix] Configured HttpClient: connectTimeout={}ms, responseTimeout={}ms",
                httpTimeoutProperties.getConnectTimeoutMs(),
                httpTimeoutProperties.getResponseTimeoutMs());
        }
        
        return properties;
    }
}
