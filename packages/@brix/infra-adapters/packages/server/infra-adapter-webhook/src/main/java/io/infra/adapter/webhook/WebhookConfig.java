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
 * Webhook 端点配置
 * 
 * <p>封装 Webhook 发送的目标端点、认证信息和重试策略配置。
 * 支持按事件类型配置不同的目标端点。</p>
 * 
 * <h2>配置示例</h2>
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
 * <h2>端点路由</h2>
 * <p>支持基于事件类型的端点路由：</p>
 * <ul>
 *   <li>精确匹配：事件类型完全匹配</li>
 *   <li>通配符匹配：使用 * 匹配任意字符</li>
 *   <li>默认端点：无匹配时使用默认端点</li>
 * </ul>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
public final class WebhookConfig {
    
    /**
     * 默认连接超时时间
     */
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    
    /**
     * 默认读取超时时间
     */
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);
    
    /**
     * 默认最大重试次数
     */
    private static final int DEFAULT_MAX_RETRIES = 3;
    
    /**
     * 默认重试延迟
     */
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(1);

    /**
     * 默认核心线程数
     * <p>基于 CPU 核心数，适合 I/O 密集型操作</p>
     */
    private static final int DEFAULT_CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    /**
     * 默认最大线程数
     * <p>核心线程数的 2 倍，为突发流量预留余量</p>
     */
    private static final int DEFAULT_MAX_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;

    /**
     * 默认任务队列容量
     * <p>防止无限队列导致内存溢出</p>
     */
    private static final int DEFAULT_QUEUE_CAPACITY = 1000;
    
    /**
     * 默认目标端点 URL
     */
    private final String defaultEndpoint;
    
    /**
     * 签名密钥（用于 HMAC-SHA256 签名）
     */
    private final String secret;
    
    /**
     * 连接超时时间
     */
    private final Duration connectTimeout;
    
    /**
     * 读取超时时间
     */
    private final Duration readTimeout;
    
    /**
     * 最大重试次数
     */
    private final int maxRetries;
    
    /**
     * 重试基础延迟（指数退避的基数）
     */
    private final Duration retryDelay;
    
    /**
     * 事件类型到端点的映射
     * <p>Key 为事件类型模式（支持通配符），Value 为目标端点 URL</p>
     */
    private final Map<String, String> endpointMappings;
    
    /**
     * 是否启用签名验证
     */
    private final boolean signatureEnabled;
    
    /**
     * 自定义请求头
     */
    private final Map<String, String> customHeaders;

    /**
     * HTTP 客户端线程池核心线程数
     * <p>线程池会保持这个数量的线程常驻</p>
     */
    private final int corePoolSize;

    /**
     * HTTP 客户端线程池最大线程数
     * <p>流量高峰时允许扩展到的最大线程数</p>
     */
    private final int maxPoolSize;

    /**
     * HTTP 客户端线程池任务队列容量
     * <p>超过核心线程数时，任务先进入队列等待</p>
     */
    private final int queueCapacity;
    
    /**
     * 私有构造函数，通过 Builder 创建实例
     *
     * @param builder 构建器实例
     */
    private WebhookConfig(Builder builder) {
        this.defaultEndpoint = Objects.requireNonNull(builder.defaultEndpoint, "defaultEndpoint 不能为空");
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
     * 创建新的构建器实例
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 根据事件类型获取目标端点 URL
     * 
     * <p>匹配顺序：</p>
     * <ol>
     *   <li>精确匹配事件类型</li>
     *   <li>通配符模式匹配</li>
     *   <li>返回默认端点</li>
     * </ol>
     *
     * @param eventType 事件类型
     * @return 目标端点 URL
     */
    public String getEndpointForEventType(String eventType) {
        if (eventType == null || eventType.isEmpty()) {
            return defaultEndpoint;
        }
        
        // 1. 精确匹配
        String endpoint = endpointMappings.get(eventType);
        if (endpoint != null) {
            return endpoint;
        }
        
        // 2. 通配符匹配
        for (Map.Entry<String, String> entry : endpointMappings.entrySet()) {
            String pattern = entry.getKey();
            if (matchesPattern(eventType, pattern)) {
                return entry.getValue();
            }
        }
        
        // 3. 默认端点
        return defaultEndpoint;
    }
    
    /**
     * 检查事件类型是否匹配通配符模式
     *
     * @param eventType 事件类型
     * @param pattern 通配符模式（* 匹配任意字符）
     * @return 是否匹配
     */
    private boolean matchesPattern(String eventType, String pattern) {
        if (!pattern.contains("*")) {
            return false;
        }
        
        // 将通配符模式转换为正则表达式
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*");
        
        return eventType.matches(regex);
    }
    
    // ========== Getter 方法 ==========
    
    /**
     * 获取默认端点 URL
     *
     * @return 默认端点 URL
     */
    public String getDefaultEndpoint() {
        return defaultEndpoint;
    }
    
    /**
     * 获取签名密钥
     *
     * @return 签名密钥的 Optional 包装
     */
    public Optional<String> getSecret() {
        return Optional.ofNullable(secret);
    }
    
    /**
     * 获取连接超时时间
     *
     * @return 连接超时时间
     */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }
    
    /**
     * 获取读取超时时间
     *
     * @return 读取超时时间
     */
    public Duration getReadTimeout() {
        return readTimeout;
    }
    
    /**
     * 获取最大重试次数
     *
     * @return 最大重试次数
     */
    public int getMaxRetries() {
        return maxRetries;
    }
    
    /**
     * 获取重试基础延迟
     *
     * @return 重试延迟
     */
    public Duration getRetryDelay() {
        return retryDelay;
    }
    
    /**
     * 获取所有端点映射（只读）
     *
     * @return 端点映射的不可变视图
     */
    public Map<String, String> getEndpointMappings() {
        return endpointMappings;
    }
    
    /**
     * 是否启用签名验证
     *
     * @return 是否启用签名
     */
    public boolean isSignatureEnabled() {
        return signatureEnabled;
    }
    
    /**
     * 获取自定义请求头
     *
     * @return 自定义请求头的不可变视图
     */
    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }

    /**
     * 获取 HTTP 客户端线程池核心线程数
     *
     * <p>该参数用于创建固定线程池，避免无限制线程增长导致的资源耗尽。
     * 默认值基于 CPU 核心数计算，适合 I/O 密集型的 HTTP 调用场景。</p>
     *
     * @return 核心线程数
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * 获取 HTTP 客户端线程池最大线程数
     *
     * <p>当核心线程都在忙碌且队列已满时，线程池可以扩展到此数量。
     * 默认值是核心线程数的 2 倍。</p>
     *
     * @return 最大线程数
     */
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    /**
     * 获取 HTTP 客户端线程池任务队列容量
     *
     * <p>任务队列用于缓冲等待执行的任务。当队列满时，
     * 会触发线程池扩展（直到达到 maxPoolSize）或拒绝策略。</p>
     *
     * @return 队列容量
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
     * WebhookConfig 构建器
     * 
     * <p>使用 Builder 模式创建 WebhookConfig 实例，
     * 确保必要参数被正确设置。</p>
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
         * 设置默认端点 URL
         *
         * @param defaultEndpoint 默认端点 URL（必填）
         * @return Builder 实例
         */
        public Builder defaultEndpoint(String defaultEndpoint) {
            this.defaultEndpoint = defaultEndpoint;
            return this;
        }
        
        /**
         * 设置签名密钥
         *
         * @param secret 签名密钥
         * @return Builder 实例
         */
        public Builder secret(String secret) {
            this.secret = secret;
            return this;
        }
        
        /**
         * 设置连接超时时间
         *
         * @param connectTimeout 连接超时时间
         * @return Builder 实例
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }
        
        /**
         * 设置读取超时时间
         *
         * @param readTimeout 读取超时时间
         * @return Builder 实例
         */
        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }
        
        /**
         * 设置最大重试次数
         *
         * @param maxRetries 最大重试次数
         * @return Builder 实例
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        /**
         * 设置重试基础延迟
         *
         * @param retryDelay 重试延迟
         * @return Builder 实例
         */
        public Builder retryDelay(Duration retryDelay) {
            this.retryDelay = retryDelay;
            return this;
        }
        
        /**
         * 添加端点映射
         *
         * @param eventTypePattern 事件类型模式（支持 * 通配符）
         * @param endpoint 目标端点 URL
         * @return Builder 实例
         */
        public Builder addEndpointMapping(String eventTypePattern, String endpoint) {
            this.endpointMappings.put(eventTypePattern, endpoint);
            return this;
        }
        
        /**
         * 批量设置端点映射
         *
         * @param mappings 端点映射
         * @return Builder 实例
         */
        public Builder endpointMappings(Map<String, String> mappings) {
            if (mappings != null) {
                this.endpointMappings.putAll(mappings);
            }
            return this;
        }
        
        /**
         * 设置是否启用签名验证
         *
         * @param signatureEnabled 是否启用签名
         * @return Builder 实例
         */
        public Builder signatureEnabled(boolean signatureEnabled) {
            this.signatureEnabled = signatureEnabled;
            return this;
        }
        
        /**
         * 添加自定义请求头
         *
         * @param name 请求头名称
         * @param value 请求头值
         * @return Builder 实例
         */
        public Builder addCustomHeader(String name, String value) {
            this.customHeaders.put(name, value);
            return this;
        }
        
        /**
         * 批量设置自定义请求头
         *
         * @param headers 自定义请求头
         * @return Builder 实例
         */
        public Builder customHeaders(Map<String, String> headers) {
            if (headers != null) {
                this.customHeaders.putAll(headers);
            }
            return this;
        }

        /**
         * 设置线程池核心线程数
         *
         * <p>核心线程会一直保持活跃，即使没有任务执行。
         * 推荐根据系统 CPU 核心数和 I/O 密集程度设置。</p>
         *
         * @param corePoolSize 核心线程数，必须大于 0
         * @return Builder 实例
         */
        public Builder corePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        /**
         * 设置线程池最大线程数
         *
         * <p>当队列满时，线程池会扩展线程数直到达到此上限。
         * 超过此限制的任务会触发拒绝策略。</p>
         *
         * @param maxPoolSize 最大线程数，必须大于等于核心线程数
         * @return Builder 实例
         */
        public Builder maxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return this;
        }

        /**
         * 设置线程池任务队列容量
         *
         * <p>队列用于缓冲等待执行的任务。
         * 设置合理的队列容量可以防止内存溢出。</p>
         *
         * @param queueCapacity 队列容量，必须大于 0
         * @return Builder 实例
         */
        public Builder queueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
            return this;
        }
        
        /**
         * 构建 WebhookConfig 实例
         *
         * @return WebhookConfig 实例
         * @throws NullPointerException 如果 defaultEndpoint 为空
         */
        public WebhookConfig build() {
            return new WebhookConfig(this);
        }
    }
}
