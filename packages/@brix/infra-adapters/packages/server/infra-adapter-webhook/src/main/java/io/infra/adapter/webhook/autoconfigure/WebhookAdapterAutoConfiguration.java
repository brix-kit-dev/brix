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
package io.infra.adapter.webhook.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.infra.adapter.webhook.HttpWebhookEventBus;
import io.infra.adapter.webhook.WebhookConfig;
import io.infra.adapter.webhook.WebhookSignatureVerifier;
import io.runtime.sdk.capability.EventBusCapability;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Webhook Adapter Auto-Configuration
 * 
 * <p>Spring Boot auto-configuration class that automatically configures Webhook event bus based on properties.</p>
 * 
 * <h2>Activation Conditions</h2>
 * <ul>
 *   <li>Configure brix.infra.webhook.enabled=true</li>
 *   <li>HttpWebhookEventBus exists in classpath</li>
 *   <li>No other EventBusCapability Bean exists</li>
 * </ul>
 * 
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * brix:
 *   infra:
 *     webhook:
 *       enabled: true
 *       default-endpoint: https://api.example.com/webhook
 *       secret: your-secret-key
 * }</pre>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
@AutoConfiguration
@ConditionalOnClass(HttpWebhookEventBus.class)
@ConditionalOnProperty(prefix = "brix.infra.webhook", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WebhookAdapterProperties.class)
public class WebhookAdapterAutoConfiguration {
    
    /**
     * Creates WebhookConfig Bean
     *
     * @param properties Configuration properties
     * @return WebhookConfig instance
     */
    @Bean
    @ConditionalOnMissingBean
    public WebhookConfig webhookConfig(WebhookAdapterProperties properties) {
        WebhookConfig.Builder builder = WebhookConfig.builder()
                .defaultEndpoint(properties.getDefaultEndpoint())
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout())
                .maxRetries(properties.getMaxRetries())
                .retryDelay(properties.getRetryDelay())
                .signatureEnabled(properties.isSignatureEnabled())
                .endpointMappings(properties.getEndpointMappings())
                .customHeaders(properties.getCustomHeaders());
        
        if (properties.getSecret() != null && !properties.getSecret().isEmpty()) {
            builder.secret(properties.getSecret());
        }
        
        return builder.build();
    }
    
    /**
     * Creates WebhookSignatureVerifier Bean
     *
     * @param properties Configuration properties
     * @return WebhookSignatureVerifier instance, or null if secret is not configured
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "brix.infra.webhook", name = "secret")
    public WebhookSignatureVerifier webhookSignatureVerifier(WebhookAdapterProperties properties) {
        return new WebhookSignatureVerifier(properties.getSecret(), properties.getTimestampTolerance());
    }
    
    /**
     * Creates HttpWebhookEventBus Bean
     * 
     * <p>Serves as the EventBusCapability implementation for embedded deployment.</p>
     *
     * @param config Webhook configuration
     * @param objectMapper Jackson ObjectMapper (optional)
     * @return HttpWebhookEventBus instance
     */
    @Bean
    @ConditionalOnMissingBean(EventBusCapability.class)
    public EventBusCapability webhookEventBus(
            WebhookConfig config,
            ObjectMapper objectMapper) {
        return new HttpWebhookEventBus(config, objectMapper, null);
    }
}
