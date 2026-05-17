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

import org.slf4j.MDC;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * HTTP Webhook-based Event Bus Implementation
 * 
 * <p>Pushes events to configured Webhook endpoints via HTTP POST requests.
 * Suitable for embedded deployment scenarios without requiring message middleware like Kafka.</p>
 * 
 * <h2>Core Features</h2>
 * <ul>
 *   <li>HTTP POST push: Serializes events to JSON and sends to endpoints</li>
 *   <li>Signature verification: Uses HMAC-SHA256 signatures for security</li>
 *   <li>Retry mechanism: Automatic retry on failure with exponential backoff</li>
 *   <li>Endpoint routing: Supports routing to different endpoints by event type</li>
 * </ul>
 * 
 * <h2>Request Format</h2>
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
 * <h2>Architecture Notes</h2>
 * <p>This class implements the EventBusCapability interface defined in Layer 1,
 * and belongs to Layer 2 Adapter layer for embedded deployment.</p>
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
    description = "HTTP Webhook-based event bus implementation",
    level = CapabilityLevel.STANDARD,
    aliases = {"webhookEventBus"}
)
public class HttpWebhookEventBus implements EventBusCapability, AutoCloseable {

    // ── Metric name constants (R3.3 / R3.7 compliance) ──
    private static final String METRIC_PUBLISHED_TOTAL = "eventbus.webhook.published.total";
    private static final String METRIC_PUBLISH_ERROR   = "eventbus.webhook.publish.errors";
    private static final String METRIC_PUBLISH_DURATION = "eventbus.webhook.publish.duration";

    /**
     * HTTP client
     */
    private final HttpClient httpClient;
    
    /**
     * JSON serializer
     */
    private final ObjectMapper objectMapper;
    
    /**
     * Webhook configuration
     */
    private final WebhookConfig config;
    
    /**
     * Signature verifier
     */
    private final WebhookSignatureVerifier signatureVerifier;
    
    /**
     * Retry handler
     */
    private final WebhookRetryHandler retryHandler;
    
    /**
     * Async executor
     */
    private final ExecutorService executor;
    
    /**
     * Whether the executor is internally created
     */
    private final boolean ownExecutor;
    
    /**
     * Sent events counter
     */
    private final AtomicLong sentCount = new AtomicLong(0);
    
    /**
     * Failed events counter
     */
    private final AtomicLong failedCount = new AtomicLong(0);
    
    /**
     * Micrometer meter registry for structured metrics (optional, nullable).
     * @since 3.2.0
     */
    private final MeterRegistry meterRegistry;

    /**
     * Event listeners (for testing and debugging)
     */
    private final Map<String, EventListener> eventListeners = new ConcurrentHashMap<>();
    
    /**
     * Creates an HttpWebhookEventBus instance
     *
     * @param config Webhook configuration
     */
    public HttpWebhookEventBus(WebhookConfig config) {
        this(config, null, null, null);
    }
    
    /**
     * Creates an HttpWebhookEventBus instance (full parameters)
     *
     * <p>Thread pool configuration notes (v3.2 performance optimization):
     * <ul>
     *   <li>Uses bounded thread pool instead of CachedThreadPool to prevent unlimited thread growth</li>
     *   <li>Core pool size defaults to CPU core count, suitable for I/O intensive operations</li>
     *   <li>Maximum pool size defaults to 2x core pool size, reserving capacity for traffic bursts</li>
     *   <li>Task queue capacity defaults to 1000 to prevent memory overflow</li>
     *   <li>Uses CallerRunsPolicy rejection policy - when queue is full, caller thread executes the task for throttling</li>
     * </ul>
     *
     * @param config Webhook configuration
     * @param objectMapper JSON serializer (optional, null uses default)
     * @param executor Executor (optional, null uses built-in bounded thread pool)
     */
    public HttpWebhookEventBus(WebhookConfig config, ObjectMapper objectMapper, ExecutorService executor) {
        this(config, objectMapper, executor, null);
    }

    /**
     * Creates an HttpWebhookEventBus instance (full parameters with optional MeterRegistry)
     *
     * @param config         Webhook configuration
     * @param objectMapper   JSON serializer (optional, null uses default)
     * @param executor       Executor (optional, null uses built-in bounded thread pool)
     * @param meterRegistry  Micrometer meter registry (optional, null disables structured metrics)
     * @since 3.2.0
     */
    public HttpWebhookEventBus(WebhookConfig config, ObjectMapper objectMapper, ExecutorService executor, MeterRegistry meterRegistry) {
        this.config = Objects.requireNonNull(config, "WebhookConfig cannot be null");
        this.meterRegistry = meterRegistry;
        
        // Initialize ObjectMapper
        this.objectMapper = objectMapper != null ? objectMapper : createDefaultObjectMapper();
        
        /*
         * Create bounded thread pool for HTTP client
         * 
         * Performance risk fix (v3.2):
         * - Original implementation used Executors.newCachedThreadPool(), which under high concurrency:
         *   1. Causes unlimited thread growth, exhausting system resources
         *   2. May create new threads for each task, increasing context switching overhead
         *   3. Frequent thread creation and destruction impacts performance
         * 
         * - New implementation uses ThreadPoolExecutor with bounded thread pool:
         *   1. Core pool size: Number of threads kept alive to reduce thread creation overhead
         *   2. Maximum pool size: Limits thread count to prevent resource exhaustion
         *   3. Bounded queue: Buffers waiting tasks, triggers rejection policy when full
         *   4. CallerRunsPolicy: Rejected tasks run on caller thread for backpressure throttling
         */
        ExecutorService httpClientExecutor = new ThreadPoolExecutor(
                config.getCorePoolSize(),       // Core pool size
                config.getMaxPoolSize(),        // Maximum pool size
                60L, TimeUnit.SECONDS,          // Idle thread keep-alive time
                new LinkedBlockingQueue<>(config.getQueueCapacity()),  // Bounded task queue
                r -> {
                    Thread t = new Thread(r, "webhook-http-client");
                    t.setDaemon(true);  // Daemon thread, won't prevent JVM exit
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()  // Rejection policy: caller runs
        );
        
        // Initialize HTTP client
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.getConnectTimeout())
                .executor(httpClientExecutor)
                .build();
        
        // Initialize signature verifier
        this.signatureVerifier = config.getSecret()
                .map(WebhookSignatureVerifier::new)
                .orElse(null);
        
        // Initialize retry handler
        this.retryHandler = WebhookRetryHandler.builder()
                .maxRetries(config.getMaxRetries())
                .baseDelay(config.getRetryDelay())
                .build();
        
        // Initialize event processing executor
        if (executor != null) {
            this.executor = executor;
            this.ownExecutor = false;
        } else {
            // Use bounded thread pool for event publishing
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
     * Creates default ObjectMapper
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
     * <p>Sends domain events to configured Webhook endpoints via HTTP POST.
     * Sending is asynchronous, but the method waits for completion.</p>
     *
     * @param event The domain event to publish
     * @throws IllegalArgumentException If event is null
     * @throws RuntimeException If sending fails after all retries
     */
    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "DomainEvent cannot be null");
        
        String eventType = event.getClass().getSimpleName();
        String eventId = event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString();
        Timer.Sample sample = startTimerSample();
        
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
            recordPublishSuccess(eventType, sample);
            
            // Notify listeners
            notifyListeners(eventType, event);
            
        } catch (Exception e) {
            failedCount.incrementAndGet();
            recordPublishError(eventType);
            throw new RuntimeException("Failed to publish domain event: " + eventType, e);
        }
    }
    
    /**
     * {@inheritDoc}
     * 
     * <p>Sends integration events to configured Webhook endpoints via HTTP POST.
     * Sending is asynchronous, but the method waits for completion.</p>
     *
     * @param event The integration event to publish
     * @throws IllegalArgumentException If event is null
     * @throws RuntimeException If sending fails after all retries
     */
    @Override
    public void publishIntegration(IntegrationEvent event) {
        Objects.requireNonNull(event, "IntegrationEvent cannot be null");
        ensureTraceId(event);
        
        String eventType = event.getEventType() != null ? event.getEventType() : event.getClass().getSimpleName();
        String eventId = event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString();
        Timer.Sample sample = startTimerSample();
        
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
            recordPublishSuccess(eventType, sample);
            
            // Notify listeners
            notifyListeners(eventType, event);
            
        } catch (Exception e) {
            failedCount.incrementAndGet();
            recordPublishError(eventType);
            throw new RuntimeException("Failed to publish integration event: " + eventType, e);
        }
    }
    
    /**
     * Asynchronously publishes a domain event
     *
     * @param event The domain event to publish
     * @return Async result
     */
    public CompletableFuture<Void> publishAsync(DomainEvent event) {
        return CompletableFuture.runAsync(() -> publish(event), executor);
    }
    
    /**
     * Asynchronously publishes an integration event
     *
     * @param event The integration event to publish
     * @return Async result
     */
    public CompletableFuture<Void> publishIntegrationAsync(IntegrationEvent event) {
        return CompletableFuture.runAsync(() -> publishIntegration(event), executor);
    }
    
    /**
     * Sends Webhook request
     *
     * @param eventType Event type
     * @param payload Webhook payload
     */
    private void sendWebhook(String eventType, WebhookPayload payload) {
        String endpoint = config.getEndpointForEventType(eventType);
        
        try {
            String jsonBody = objectMapper.writeValueAsString(payload);
            long timestamp = Instant.now().getEpochSecond();
            
            // Build request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(config.getReadTimeout())
                    .header("Content-Type", "application/json")
                    .header("X-Event-Type", eventType)
                    .header("X-Event-Id", payload.eventId)
                    .header(WebhookSignatureVerifier.TIMESTAMP_HEADER, String.valueOf(timestamp))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            
            // Add signature
            if (config.isSignatureEnabled() && signatureVerifier != null) {
                String signature = signatureVerifier.sign(jsonBody, timestamp);
                requestBuilder.header(WebhookSignatureVerifier.SIGNATURE_HEADER, signature);
            }
            
            // Add custom headers
            config.getCustomHeaders().forEach(requestBuilder::header);
            
            HttpRequest request = requestBuilder.build();
            
            // Execute with retry
            retryHandler.executeWithRetry(() -> {
                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return response;
                    } else {
                        throw new RuntimeException(String.format(
                                "Webhook request failed: status=%d, body=%s",
                                response.statusCode(),
                                response.body()
                        ));
                    }
                } catch (IOException | InterruptedException e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    throw new RuntimeException("Webhook request exception", e);
                }
            }).join(); // Wait for completion
            
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
    }
    
    /**
     * Registers an event listener (for testing)
     *
     * @param eventType Event type (supports * wildcard)
     * @param listener The listener
     */
    public void addListener(String eventType, EventListener listener) {
        eventListeners.put(eventType, listener);
    }
    
    /**
     * Removes an event listener
     *
     * @param eventType Event type
     */
    public void removeListener(String eventType) {
        eventListeners.remove(eventType);
    }
    
    /**
     * Notifies listeners
     */
    private void notifyListeners(String eventType, Object event) {
        for (Map.Entry<String, EventListener> entry : eventListeners.entrySet()) {
            String pattern = entry.getKey();
            EventListener listener = entry.getValue();
            
            if ("*".equals(pattern) || pattern.equals(eventType)) {
                try {
                    listener.onEvent(eventType, event);
                } catch (Exception e) {
                    // Ignore listener exceptions
                }
            }
        }
    }
    
    /**
     * Gets sent events count
     *
     * @return Number of sent events
     */
    public long getSentCount() {
        return sentCount.get();
    }
    
    /**
     * Gets failed events count
     *
     * @return Number of failed events
     */
    public long getFailedCount() {
        return failedCount.get();
    }
    
    /**
     * Resets statistics counters
     */
    public void resetStats() {
        sentCount.set(0);
        failedCount.set(0);
    }
    
    // ── TraceId injection (P0-1 compliance) ──

    /**
     * Ensures the integration event carries a traceId.
     * Resolution order: existing value → MDC traceId → random UUID.
     */
    private void ensureTraceId(IntegrationEvent event) {
        if (event.getTraceId() != null && !event.getTraceId().isEmpty()) {
            return;
        }
        String mdcTraceId = MDC.get("traceId");
        event.setTraceId(mdcTraceId != null ? mdcTraceId : UUID.randomUUID().toString());
    }

    // ── Micrometer helpers (R3.3 / R3.7 compliance) ──

    private Timer.Sample startTimerSample() {
        return meterRegistry != null ? Timer.start(meterRegistry) : null;
    }

    private void recordPublishSuccess(String eventType, Timer.Sample sample) {
        if (meterRegistry == null) return;
        Counter.builder(METRIC_PUBLISHED_TOTAL)
                .tag("eventType", eventType)
                .tag("transport", "webhook")
                .register(meterRegistry)
                .increment();
        if (sample != null) {
            sample.stop(Timer.builder(METRIC_PUBLISH_DURATION)
                    .tag("eventType", eventType)
                    .tag("transport", "webhook")
                    .register(meterRegistry));
        }
    }

    private void recordPublishError(String eventType) {
        if (meterRegistry == null) return;
        Counter.builder(METRIC_PUBLISH_ERROR)
                .tag("eventType", eventType)
                .tag("transport", "webhook")
                .register(meterRegistry)
                .increment();
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
     * Webhook payload data class
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
     * Event listener interface (for testing)
     */
    @FunctionalInterface
    public interface EventListener {
        /**
         * Event callback
         *
         * @param eventType Event type
         * @param event Event object
         */
        void onEvent(String eventType, Object event);
    }
}
