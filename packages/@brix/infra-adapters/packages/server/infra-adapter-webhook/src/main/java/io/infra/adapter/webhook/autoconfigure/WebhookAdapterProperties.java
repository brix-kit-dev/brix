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

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Webhook Adapter Configuration Properties
 * 
 * <p>Spring Boot configuration properties class for binding Webhook configuration from application.yml.</p>
 * 
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * brix:
 *   infra:
 *     webhook:
 *       enabled: true
 *       default-endpoint: https://api.example.com/webhook
 *       secret: your-secret-key
 *       signature-enabled: true
 *       connect-timeout: 5s
 *       read-timeout: 30s
 *       max-retries: 3
 *       retry-delay: 1s
 *       endpoint-mappings:
 *         "order.*": https://order-service/webhook
 *         "user.*": https://user-service/webhook
 *       custom-headers:
 *         X-API-Key: your-api-key
 * }</pre>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
@ConfigurationProperties(prefix = "brix.infra.webhook")
public class WebhookAdapterProperties {
    
    /**
     * Whether Webhook adapter is enabled
     */
    private boolean enabled = false;
    
    /**
     * Default Webhook endpoint URL
     */
    private String defaultEndpoint;
    
    /**
     * Signing secret
     */
    private String secret;
    
    /**
     * Whether signature verification is enabled
     */
    private boolean signatureEnabled = true;
    
    /**
     * Connection timeout
     */
    private Duration connectTimeout = Duration.ofSeconds(5);
    
    /**
     * Read timeout
     */
    private Duration readTimeout = Duration.ofSeconds(30);
    
    /**
     * Maximum retry count
     */
    private int maxRetries = 3;
    
    /**
     * Base retry delay
     */
    private Duration retryDelay = Duration.ofSeconds(1);
    
    /**
     * Event type to endpoint mappings
     */
    private Map<String, String> endpointMappings = new HashMap<>();
    
    /**
     * Custom request headers
     */
    private Map<String, String> customHeaders = new HashMap<>();
    
    /**
     * Timestamp tolerance (seconds) for signature verification
     */
    private long timestampTolerance = 300;
    
    // ========== Getter / Setter ==========
    
    /**
     * Whether Webhook adapter is enabled
     *
     * @return Whether enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Sets whether Webhook adapter is enabled
     *
     * @param enabled Whether enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Gets default endpoint URL
     *
     * @return Default endpoint URL
     */
    public String getDefaultEndpoint() {
        return defaultEndpoint;
    }
    
    /**
     * Sets default endpoint URL
     *
     * @param defaultEndpoint Default endpoint URL
     */
    public void setDefaultEndpoint(String defaultEndpoint) {
        this.defaultEndpoint = defaultEndpoint;
    }
    
    /**
     * Gets signing secret
     *
     * @return Signing secret
     */
    public String getSecret() {
        return secret;
    }
    
    /**
     * Sets signing secret
     *
     * @param secret Signing secret
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }
    
    /**
     * Whether signature verification is enabled
     *
     * @return Whether signature is enabled
     */
    public boolean isSignatureEnabled() {
        return signatureEnabled;
    }
    
    /**
     * Sets whether signature verification is enabled
     *
     * @param signatureEnabled Whether signature is enabled
     */
    public void setSignatureEnabled(boolean signatureEnabled) {
        this.signatureEnabled = signatureEnabled;
    }
    
    /**
     * Gets connection timeout
     *
     * @return Connection timeout
     */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }
    
    /**
     * Sets connection timeout
     *
     * @param connectTimeout Connection timeout
     */
    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
    
    /**
     * Gets read timeout
     *
     * @return Read timeout
     */
    public Duration getReadTimeout() {
        return readTimeout;
    }
    
    /**
     * Sets read timeout
     *
     * @param readTimeout Read timeout
     */
    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
    
    /**
     * Gets maximum retry count
     *
     * @return Maximum retry count
     */
    public int getMaxRetries() {
        return maxRetries;
    }
    
    /**
     * Sets maximum retry count
     *
     * @param maxRetries Maximum retry count
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    /**
     * Gets retry delay
     *
     * @return Retry delay
     */
    public Duration getRetryDelay() {
        return retryDelay;
    }
    
    /**
     * Sets retry delay
     *
     * @param retryDelay Retry delay
     */
    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }
    
    /**
     * Gets endpoint mappings
     *
     * @return Endpoint mappings
     */
    public Map<String, String> getEndpointMappings() {
        return endpointMappings;
    }
    
    /**
     * Sets endpoint mappings
     *
     * @param endpointMappings Endpoint mappings
     */
    public void setEndpointMappings(Map<String, String> endpointMappings) {
        this.endpointMappings = endpointMappings;
    }
    
    /**
     * Gets custom request headers
     *
     * @return Custom request headers
     */
    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }
    
    /**
     * Sets custom request headers
     *
     * @param customHeaders Custom request headers
     */
    public void setCustomHeaders(Map<String, String> customHeaders) {
        this.customHeaders = customHeaders;
    }
    
    /**
     * Gets timestamp tolerance
     *
     * @return Timestamp tolerance (seconds)
     */
    public long getTimestampTolerance() {
        return timestampTolerance;
    }
    
    /**
     * Sets timestamp tolerance
     *
     * @param timestampTolerance Timestamp tolerance (seconds)
     */
    public void setTimestampTolerance(long timestampTolerance) {
        this.timestampTolerance = timestampTolerance;
    }
}
