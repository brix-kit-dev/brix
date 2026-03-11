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
package io.infra.adapter.webhook;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Webhook Endpoint Configuration
 * 
 * <p>Encapsulates Webhook target endpoints, authentication information, and retry policy configuration.
 * Supports configuring different target endpoints by event type.</p>
 * 
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * WebhookConfig config = WebhookConfig.builder()
 *     .defaultEndpoint("https://api.example.com/webhook")
 *     .secret("your-secret-key")
 *     .connectTimeout(Duration.ofSeconds(5))
 *     .readTimeout(Duration.ofSeconds(30))
 *     .maxRetries(3)
 *     .retryDelay(Duration.ofSeconds(1))
 *     .addEndpointMapping("order.*", "https://order-service/webhook")
 *     .addEndpointMapping("user.*", "https://user-service/webhook")
 *     .build();
 * }</pre>
 * 
 * <h2>Endpoint Routing</h2>
 * <p>Supports endpoint routing based on event type:</p>
 * <ul>
 *   <li>Exact match: Event type matches exactly</li>
 *   <li>Wildcard match: Use * to match any characters</li>
 *   <li>Default endpoint: Uses default endpoint when no match found</li>
 * </ul>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
public final class WebhookConfig {
    
    /**
     * Default connection timeout
     */
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    
    /**
     * Default read timeout
     */
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);
    
    /**
     * Default maximum retry count
     */
    private static final int DEFAULT_MAX_RETRIES = 3;
    
    /**
     * Default retry delay
     */
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(1);

    /**
     * Default core pool size
     * <p>Based on CPU core count, suitable for I/O intensive operations</p>
     */
    private static final int DEFAULT_CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    /**
     * Default maximum pool size
     * <p>2x core pool size, reserving capacity for traffic bursts</p>
     */
    private static final int DEFAULT_MAX_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;

    /**
     * Default task queue capacity
     * <p>Prevents unbounded queue from causing memory overflow</p>
     */
    private static final int DEFAULT_QUEUE_CAPACITY = 1000;
    
    /**
     * Default target endpoint URL
     */
    private final String defaultEndpoint;
    
    /**
     * Signing secret (for HMAC-SHA256 signatures)
     */
    private final String secret;
    
    /**
     * Connection timeout
     */
    private final Duration connectTimeout;
    
    /**
     * Read timeout
     */
    private final Duration readTimeout;
    
    /**
     * Maximum retry count
     */
    private final int maxRetries;
    
    /**
     * Base retry delay (base for exponential backoff)
     */
    private final Duration retryDelay;
    
    /**
     * Event type to endpoint mappings
     * <p>Key is event type pattern (supports wildcards), Value is target endpoint URL</p>
     */
    private final Map<String, String> endpointMappings;
    
    /**
     * Whether signature verification is enabled
     */
    private final boolean signatureEnabled;
    
    /**
     * Custom request headers
     */
    private final Map<String, String> customHeaders;

    /**
     * HTTP client thread pool core size
     * <p>Thread pool maintains this number of threads</p>
     */
    private final int corePoolSize;

    /**
     * HTTP client thread pool maximum size
     * <p>Maximum threads allowed during peak traffic</p>
     */
    private final int maxPoolSize;

    /**
     * HTTP client thread pool task queue capacity
     * <p>When exceeding core pool size, tasks enter queue first</p>
     */
    private final int queueCapacity;
    
    /**
     * Private constructor, instances created via Builder
     *
     * @param builder Builder instance
     */
    private WebhookConfig(Builder builder) {
        this.defaultEndpoint = Objects.requireNonNull(builder.defaultEndpoint, "defaultEndpoint cannot be null");
        this.secret = builder.secret;
        this.connectTimeout = builder.connectTimeout != null ? builder.connectTimeout : DEFAULT_CONNECT_TIMEOUT;
        this.readTimeout = builder.readTimeout != null ? builder.readTimeout : DEFAULT_READ_TIMEOUT;
        this.maxRetries = builder.maxRetries > 0 ? builder.maxRetries : DEFAULT_MAX_RETRIES;
        this.retryDelay = builder.retryDelay != null ? builder.retryDelay : DEFAULT_RETRY_DELAY;
        this.endpointMappings = Collections.unmodifiableMap(new ConcurrentHashMap<>(builder.endpointMappings));
        this.signatureEnabled = builder.signatureEnabled;
        this.customHeaders = Collections.unmodifiableMap(new ConcurrentHashMap<>(builder.customHeaders));
        this.corePoolSize = builder.corePoolSize > 0 ? builder.corePoolSize : DEFAULT_CORE_POOL_SIZE;
        this.maxPoolSize = builder.maxPoolSize > 0 ? builder.maxPoolSize : DEFAULT_MAX_POOL_SIZE;
        this.queueCapacity = builder.queueCapacity > 0 ? builder.queueCapacity : DEFAULT_QUEUE_CAPACITY;
    }
    
    /**
     * Creates a new Builder instance
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Gets target endpoint URL by event type
     * 
     * <p>Matching order:</p>
     * <ol>
     *   <li>Exact match on event type</li>
     *   <li>Wildcard pattern match</li>
     *   <li>Return default endpoint</li>
     * </ol>
     *
     * @param eventType Event type
     * @return Target endpoint URL
     */
    public String getEndpointForEventType(String eventType) {
        if (eventType == null || eventType.isEmpty()) {
            return defaultEndpoint;
        }
        
        // 1. Exact match
        String endpoint = endpointMappings.get(eventType);
        if (endpoint != null) {
            return endpoint;
        }
        
        // 2. Wildcard match
        for (Map.Entry<String, String> entry : endpointMappings.entrySet()) {
            String pattern = entry.getKey();
            if (matchesPattern(eventType, pattern)) {
                return entry.getValue();
            }
        }
        
        // 3. Default endpoint
        return defaultEndpoint;
    }
    
    /**
     * Checks if event type matches wildcard pattern
     *
     * @param eventType Event type
     * @param pattern Wildcard pattern (* matches any characters)
     * @return Whether it matches
     */
    private boolean matchesPattern(String eventType, String pattern) {
        if (!pattern.contains("*")) {
            return false;
        }
        
        // Convert wildcard pattern to regex
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*");
        
        return eventType.matches(regex);
    }
    
    // ========== Getter methods ==========
    
    /**
     * Gets default endpoint URL
     *
     * @return Default endpoint URL
     */
    public String getDefaultEndpoint() {
        return defaultEndpoint;
    }
    
    /**
     * Gets signing secret
     *
     * @return Optional wrapper of signing secret
     */
    public Optional<String> getSecret() {
        return Optional.ofNullable(secret);
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
     * Gets read timeout
     *
     * @return Read timeout
     */
    public Duration getReadTimeout() {
        return readTimeout;
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
     * Gets base retry delay
     *
     * @return Retry delay
     */
    public Duration getRetryDelay() {
        return retryDelay;
    }
    
    /**
     * Gets all endpoint mappings (read-only)
     *
     * @return Immutable view of endpoint mappings
     */
    public Map<String, String> getEndpointMappings() {
        return endpointMappings;
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
     * Gets custom request headers
     *
     * @return Immutable view of custom headers
     */
    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }

    /**
     * Gets HTTP client thread pool core size
     *
     * <p>This parameter is used to create a fixed thread pool to avoid
     * unlimited thread growth causing resource exhaustion.
     * Default value is calculated based on CPU core count, suitable for I/O intensive HTTP calls.</p>
     *
     * @return Core pool size
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Gets HTTP client thread pool maximum size
     *
     * <p>When all core threads are busy and queue is full,
     * thread pool can expand to this size.
     * Default is 2x core pool size.</p>
     *
     * @return Maximum pool size
     */
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    /**
     * Gets HTTP client thread pool task queue capacity
     *
     * <p>Task queue buffers waiting tasks. When queue is full,
     * triggers thread pool expansion (up to maxPoolSize) or rejection policy.</p>
     *
     * @return Queue capacity
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }
    
    @Override
    public String toString() {
        return "WebhookConfig{" +
                "defaultEndpoint='" + defaultEndpoint + '\'' +
                ", signatureEnabled=" + signatureEnabled +
                ", connectTimeout=" + connectTimeout +
                ", readTimeout=" + readTimeout +
                ", maxRetries=" + maxRetries +
                ", retryDelay=" + retryDelay +
                ", endpointMappings=" + endpointMappings.keySet() +
                '}';
    }
    
    /**
     * WebhookConfig Builder
     * 
     * <p>Uses Builder pattern to create WebhookConfig instances,
     * ensuring required parameters are properly set.</p>
     */
    public static final class Builder {
        
        private String defaultEndpoint;
        private String secret;
        private Duration connectTimeout;
        private Duration readTimeout;
        private int maxRetries;
        private Duration retryDelay;
        private final Map<String, String> endpointMappings = new ConcurrentHashMap<>();
        private boolean signatureEnabled = true;
        private final Map<String, String> customHeaders = new ConcurrentHashMap<>();
        private int corePoolSize;
        private int maxPoolSize;
        private int queueCapacity;
        
        private Builder() {
        }
        
        /**
         * Sets default endpoint URL
         *
         * @param defaultEndpoint Default endpoint URL (required)
         * @return Builder instance
         */
        public Builder defaultEndpoint(String defaultEndpoint) {
            this.defaultEndpoint = defaultEndpoint;
            return this;
        }
        
        /**
         * Sets signing secret
         *
         * @param secret Signing secret
         * @return Builder instance
         */
        public Builder secret(String secret) {
            this.secret = secret;
            return this;
        }
        
        /**
         * Sets connection timeout
         *
         * @param connectTimeout Connection timeout
         * @return Builder instance
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }
        
        /**
         * Sets read timeout
         *
         * @param readTimeout Read timeout
         * @return Builder instance
         */
        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }
        
        /**
         * Sets maximum retry count
         *
         * @param maxRetries Maximum retry count
         * @return Builder instance
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        /**
         * Sets base retry delay
         *
         * @param retryDelay Retry delay
         * @return Builder instance
         */
        public Builder retryDelay(Duration retryDelay) {
            this.retryDelay = retryDelay;
            return this;
        }
        
        /**
         * Adds endpoint mapping
         *
         * @param eventTypePattern Event type pattern (supports * wildcard)
         * @param endpoint Target endpoint URL
         * @return Builder instance
         */
        public Builder addEndpointMapping(String eventTypePattern, String endpoint) {
            this.endpointMappings.put(eventTypePattern, endpoint);
            return this;
        }
        
        /**
         * Sets endpoint mappings in batch
         *
         * @param mappings Endpoint mappings
         * @return Builder instance
         */
        public Builder endpointMappings(Map<String, String> mappings) {
            if (mappings != null) {
                this.endpointMappings.putAll(mappings);
            }
            return this;
        }
        
        /**
         * Sets whether signature verification is enabled
         *
         * @param signatureEnabled Whether to enable signature
         * @return Builder instance
         */
        public Builder signatureEnabled(boolean signatureEnabled) {
            this.signatureEnabled = signatureEnabled;
            return this;
        }
        
        /**
         * Adds custom request header
         *
         * @param name Header name
         * @param value Header value
         * @return Builder instance
         */
        public Builder addCustomHeader(String name, String value) {
            this.customHeaders.put(name, value);
            return this;
        }
        
        /**
         * Sets custom headers in batch
         *
         * @param headers Custom headers
         * @return Builder instance
         */
        public Builder customHeaders(Map<String, String> headers) {
            if (headers != null) {
                this.customHeaders.putAll(headers);
            }
            return this;
        }

        /**
         * Sets thread pool core size
         *
         * <p>Core threads remain active even when idle.
         * Recommended to set based on CPU core count and I/O intensity.</p>
         *
         * @param corePoolSize Core pool size, must be greater than 0
         * @return Builder instance
         */
        public Builder corePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        /**
         * Sets thread pool maximum size
         *
         * <p>When queue is full, thread pool expands up to this limit.
         * Tasks exceeding this limit trigger rejection policy.</p>
         *
         * @param maxPoolSize Maximum pool size, must be >= core pool size
         * @return Builder instance
         */
        public Builder maxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return this;
        }

        /**
         * Sets thread pool task queue capacity
         *
         * <p>Queue buffers waiting tasks.
         * Setting reasonable capacity prevents memory overflow.</p>
         *
         * @param queueCapacity Queue capacity, must be greater than 0
         * @return Builder instance
         */
        public Builder queueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
            return this;
        }
        
        /**
         * Builds WebhookConfig instance
         *
         * @return WebhookConfig instance
         * @throws NullPointerException If defaultEndpoint is null
         */
        public WebhookConfig build() {
            return new WebhookConfig(this);
        }
    }
}
