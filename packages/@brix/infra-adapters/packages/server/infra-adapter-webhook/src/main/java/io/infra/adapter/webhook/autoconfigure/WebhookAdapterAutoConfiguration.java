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
 * Webhook 适配器自动配置
 * 
 * <p>Spring Boot 自动配置类，根据配置属性自动装配 Webhook 事件总线。</p>
 * 
 * <h2>激活条件</h2>
 * <ul>
 *   <li>配置 brix.infra.webhook.enabled=true</li>
 *   <li>classpath 中存在 HttpWebhookEventBus</li>
 *   <li>没有其他 EventBusCapability Bean</li>
 * </ul>
 * 
 * <h2>配置示例</h2>
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
     * 创建 WebhookConfig Bean
     *
     * @param properties 配置属性
     * @return WebhookConfig 实例
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
     * 创建 WebhookSignatureVerifier Bean
     *
     * @param properties 配置属性
     * @return WebhookSignatureVerifier 实例，如果未配置密钥则返回 null
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "brix.infra.webhook", name = "secret")
    public WebhookSignatureVerifier webhookSignatureVerifier(WebhookAdapterProperties properties) {
        return new WebhookSignatureVerifier(properties.getSecret(), properties.getTimestampTolerance());
    }
    
    /**
     * 创建 HttpWebhookEventBus Bean
     * 
     * <p>作为 EventBusCapability 的实现，用于嵌入模式部署。</p>
     *
     * @param config Webhook 配置
     * @param objectMapper Jackson ObjectMapper（可选）
     * @return HttpWebhookEventBus 实例
     */
    @Bean
    @ConditionalOnMissingBean(EventBusCapability.class)
    public EventBusCapability webhookEventBus(
            WebhookConfig config,
            ObjectMapper objectMapper) {
        return new HttpWebhookEventBus(config, objectMapper, null);
    }
}
