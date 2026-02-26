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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * 基于 HTTP Webhook 的事件总线实现
 * 
 * <p>通过 HTTP POST 请求将事件推送到配置的 Webhook 端点，
 * 适用于嵌入模式部署场景，无需依赖 Kafka 等消息中间件。</p>
 * 
 * <h2>核心特性</h2>
 * <ul>
 *   <li>HTTP POST 推送：将事件序列化为 JSON 发送到端点</li>
 *   <li>签名验证：使用 HMAC-SHA256 签名确保安全性</li>
 *   <li>重试机制：失败时自动重试，支持指数退避</li>
 *   <li>端点路由：支持按事件类型路由到不同端点</li>
 * </ul>
 * 
 * <h2>请求格式</h2>
 * <pre>
 * POST /webhook HTTP/1.1
 * Content-Type: application/json
 * X-Webhook-Signature: t=1234567890,v1=abc123...
 * X-Webhook-Timestamp: 1234567890
 * X-Event-Type: order.created
 * X-Event-Id: 550e8400-e29b-41d4-a716-446655440000
 * 
 * {
 *   "eventId": "550e8400-e29b-41d4-a716-446655440000",
 *   "eventType": "order.created",
 *   "timestamp": "2024-01-01T12:00:00Z",
 *   "payload": { ... }
 * }
 * </pre>
 * 
 * <h2>架构说明</h2>
 * <p>本类实现 Layer 1 定义的 EventBusCapability 接口，
 * 属于 Layer 2 Adapter 层，用于嵌入模式部署。</p>
 * 
 * @author Brix Team
 * @since 3.0.0
 * @see EventBusCapability
 * @see WebhookConfig
 * @see WebhookSignatureVerifier
 * @see WebhookRetryHandler
 */
@Capability(
    type = EventBusCapability.class,
    name = "webhook-event-bus",
    description = "基于 HTTP Webhook 的事件总线实现",
    level = CapabilityLevel.STANDARD,
    aliases = {"webhookEventBus"}
)
public class HttpWebhookEventBus implements EventBusCapability, AutoCloseable {
    
    /**
     * HTTP 客户端
     */
    private final HttpClient httpClient;
    
    /**
     * JSON 序列化器
     */
    private final ObjectMapper objectMapper;
    
    /**
     * Webhook 配置
     */
    private final WebhookConfig config;
    
    /**
     * 签名验证器
     */
    private final WebhookSignatureVerifier signatureVerifier;
    
    /**
     * 重试处理器
     */
    private final WebhookRetryHandler retryHandler;
    
    /**
     * 异步执行器
     */
    private final ExecutorService executor;
    
    /**
     * 是否为内部创建的执行器
     */
    private final boolean ownExecutor;
    
    /**
     * 发送统计计数器
     */
    private final AtomicLong sentCount = new AtomicLong(0);
    
    /**
     * 失败统计计数器
     */
    private final AtomicLong failedCount = new AtomicLong(0);
    
    /**
     * 事件监听器（用于测试和调试）
     */
    private final Map<String, EventListener> eventListeners = new ConcurrentHashMap<>();
    
    /**
     * 创建 HttpWebhookEventBus 实例
     *
     * @param config Webhook 配置
     */
    public HttpWebhookEventBus(WebhookConfig config) {
        this(config, null, null);
    }
    
    /**
     * 创建 HttpWebhookEventBus 实例（完整参数）
     *
     * <p>线程池配置说明（v3.2 性能优化）：
     * <ul>
     *   <li>使用有界线程池替代 CachedThreadPool，防止线程数无限增长</li>
     *   <li>核心线程数默认为 CPU 核心数，适合 I/O 密集型操作</li>
     *   <li>最大线程数默认为核心线程数的 2 倍，为突发流量预留余量</li>
     *   <li>任务队列容量默认为 1000，防止内存溢出</li>
     *   <li>采用 CallerRunsPolicy 拒绝策略，当队列满时由调用线程执行，起到限流作用</li>
     * </ul>
     *
     * @param config Webhook 配置
     * @param objectMapper JSON 序列化器（可选，null 使用默认）
     * @param executor 执行器（可选，null 使用内置有界线程池）
     */
    public HttpWebhookEventBus(WebhookConfig config, ObjectMapper objectMapper, ExecutorService executor) {
        this.config = Objects.requireNonNull(config, "WebhookConfig 不能为空");
        
        // 初始化 ObjectMapper
        this.objectMapper = objectMapper != null ? objectMapper : createDefaultObjectMapper();
        
        /*
         * 创建 HTTP 客户端使用的有界线程池
         * 
         * 性能风险修复（v3.2）：
         * - 原实现使用 Executors.newCachedThreadPool()，在高并发场景下会导致：
         *   1. 线程数无限增长，耗尽系统资源
         *   2. 每个任务都可能创建新线程，增加上下文切换开销
         *   3. 线程创建和销毁频繁，影响性能
         * 
         * - 新实现使用 ThreadPoolExecutor 有界线程池：
         *   1. 核心线程数：保持常驻的线程数，减少线程创建开销
         *   2. 最大线程数：限制线程上限，防止资源耗尽
         *   3. 有界队列：缓冲等待执行的任务，队列满时触发拒绝策略
         *   4. CallerRunsPolicy：调用者线程执行被拒绝的任务，起到反压限流作用
         */
        ExecutorService httpClientExecutor = new ThreadPoolExecutor(
                config.getCorePoolSize(),       // 核心线程数
                config.getMaxPoolSize(),        // 最大线程数
                60L, TimeUnit.SECONDS,          // 空闲线程存活时间
                new LinkedBlockingQueue<>(config.getQueueCapacity()),  // 有界任务队列
                r -> {
                    Thread t = new Thread(r, "webhook-http-client");
                    t.setDaemon(true);  // 守护线程，不阻止 JVM 退出
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略：调用者运行
        );
        
        // 初始化 HTTP 客户端
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.getConnectTimeout())
                .executor(httpClientExecutor)
                .build();
        
        // 初始化签名验证器
        this.signatureVerifier = config.getSecret()
                .map(WebhookSignatureVerifier::new)
                .orElse(null);
        
        // 初始化重试处理器
        this.retryHandler = WebhookRetryHandler.builder()
                .maxRetries(config.getMaxRetries())
                .baseDelay(config.getRetryDelay())
                .build();
        
        // 初始化事件处理执行器
        if (executor != null) {
            this.executor = executor;
            this.ownExecutor = false;
        } else {
            // 使用有界线程池处理事件发布
            this.executor = new ThreadPoolExecutor(
                    config.getCorePoolSize(),
                    config.getMaxPoolSize(),
                    60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(config.getQueueCapacity()),
                    r -> {
                        Thread t = new Thread(r, "webhook-event-bus");
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );
            this.ownExecutor = true;
        }
    }
    
    /**
     * 创建默认的 ObjectMapper
     */
    private ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        return mapper;
    }
    
    /**
     * {@inheritDoc}
     * 
     * <p>将领域事件通过 HTTP POST 发送到配置的 Webhook 端点。
     * 发送是异步的，但方法会等待发送完成。</p>
     *
     * @param event 要发布的领域事件
     * @throws IllegalArgumentException 如果事件为 null
     * @throws RuntimeException 如果发送失败且重试次数用尽
     */
    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "DomainEvent 不能为空");
        
        String eventType = event.getClass().getSimpleName();
        String eventId = event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString();
        
        try {
            WebhookPayload payload = new WebhookPayload(
                    eventId,
                    eventType,
                    "domain",
                    Instant.now(),
                    event
            );
            
            sendWebhook(eventType, payload);
            sentCount.incrementAndGet();
            
            // 通知监听器
            notifyListeners(eventType, event);
            
        } catch (Exception e) {
            failedCount.incrementAndGet();
            throw new RuntimeException("发布领域事件失败: " + eventType, e);
        }
    }
    
    /**
     * {@inheritDoc}
     * 
     * <p>将集成事件通过 HTTP POST 发送到配置的 Webhook 端点。
     * 发送是异步的，但方法会等待发送完成。</p>
     *
     * @param event 要发布的集成事件
     * @throws IllegalArgumentException 如果事件为 null
     * @throws RuntimeException 如果发送失败且重试次数用尽
     */
    @Override
    public void publishIntegration(IntegrationEvent event) {
        Objects.requireNonNull(event, "IntegrationEvent 不能为空");
        
        String eventType = event.getEventType() != null ? event.getEventType() : event.getClass().getSimpleName();
        String eventId = event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString();
        
        try {
            WebhookPayload payload = new WebhookPayload(
                    eventId,
                    eventType,
                    "integration",
                    Instant.now(),
                    event
            );
            
            sendWebhook(eventType, payload);
            sentCount.incrementAndGet();
            
            // 通知监听器
            notifyListeners(eventType, event);
            
        } catch (Exception e) {
            failedCount.incrementAndGet();
            throw new RuntimeException("发布集成事件失败: " + eventType, e);
        }
    }
    
    /**
     * 异步发布领域事件
     *
     * @param event 要发布的领域事件
     * @return 异步结果
     */
    public CompletableFuture<Void> publishAsync(DomainEvent event) {
        return CompletableFuture.runAsync(() -> publish(event), executor);
    }
    
    /**
     * 异步发布集成事件
     *
     * @param event 要发布的集成事件
     * @return 异步结果
     */
    public CompletableFuture<Void> publishIntegrationAsync(IntegrationEvent event) {
        return CompletableFuture.runAsync(() -> publishIntegration(event), executor);
    }
    
    /**
     * 发送 Webhook 请求
     *
     * @param eventType 事件类型
     * @param payload Webhook 负载
     */
    private void sendWebhook(String eventType, WebhookPayload payload) {
        String endpoint = config.getEndpointForEventType(eventType);
        
        try {
            String jsonBody = objectMapper.writeValueAsString(payload);
            long timestamp = Instant.now().getEpochSecond();
            
            // 构建请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(config.getReadTimeout())
                    .header("Content-Type", "application/json")
                    .header("X-Event-Type", eventType)
                    .header("X-Event-Id", payload.eventId)
                    .header(WebhookSignatureVerifier.TIMESTAMP_HEADER, String.valueOf(timestamp))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            
            // 添加签名
            if (config.isSignatureEnabled() && signatureVerifier != null) {
                String signature = signatureVerifier.sign(jsonBody, timestamp);
                requestBuilder.header(WebhookSignatureVerifier.SIGNATURE_HEADER, signature);
            }
            
            // 添加自定义请求头
            config.getCustomHeaders().forEach(requestBuilder::header);
            
            HttpRequest request = requestBuilder.build();
            
            // 带重试执行
            retryHandler.executeWithRetry(() -> {
                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return response;
                    } else {
                        throw new RuntimeException(String.format(
                                "Webhook 请求失败: status=%d, body=%s",
                                response.statusCode(),
                                response.body()
                        ));
                    }
                } catch (IOException | InterruptedException e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    throw new RuntimeException("Webhook 请求异常", e);
                }
            }).join(); // 等待完成
            
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化事件负载失败", e);
        }
    }
    
    /**
     * 注册事件监听器（用于测试）
     *
     * @param eventType 事件类型（支持 * 通配符）
     * @param listener 监听器
     */
    public void addListener(String eventType, EventListener listener) {
        eventListeners.put(eventType, listener);
    }
    
    /**
     * 移除事件监听器
     *
     * @param eventType 事件类型
     */
    public void removeListener(String eventType) {
        eventListeners.remove(eventType);
    }
    
    /**
     * 通知监听器
     */
    private void notifyListeners(String eventType, Object event) {
        for (Map.Entry<String, EventListener> entry : eventListeners.entrySet()) {
            String pattern = entry.getKey();
            EventListener listener = entry.getValue();
            
            if ("*".equals(pattern) || pattern.equals(eventType)) {
                try {
                    listener.onEvent(eventType, event);
                } catch (Exception e) {
                    // 忽略监听器异常
                }
            }
        }
    }
    
    /**
     * 获取发送统计
     *
     * @return 已发送事件数
     */
    public long getSentCount() {
        return sentCount.get();
    }
    
    /**
     * 获取失败统计
     *
     * @return 发送失败事件数
     */
    public long getFailedCount() {
        return failedCount.get();
    }
    
    /**
     * 重置统计计数器
     */
    public void resetStats() {
        sentCount.set(0);
        failedCount.set(0);
    }
    
    @Override
    public void close() {
        retryHandler.shutdown();
        
        if (ownExecutor && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Webhook 负载数据类
     */
    public static final class WebhookPayload {
        
        public final String eventId;
        public final String eventType;
        public final String eventCategory;
        public final Instant timestamp;
        public final Object data;
        
        public WebhookPayload(String eventId, String eventType, String eventCategory, 
                             Instant timestamp, Object data) {
            this.eventId = eventId;
            this.eventType = eventType;
            this.eventCategory = eventCategory;
            this.timestamp = timestamp;
            this.data = data;
        }
    }
    
    /**
     * 事件监听器接口（用于测试）
     */
    @FunctionalInterface
    public interface EventListener {
        /**
         * 事件回调
         *
         * @param eventType 事件类型
         * @param event 事件对象
         */
        void onEvent(String eventType, Object event);
    }
}
