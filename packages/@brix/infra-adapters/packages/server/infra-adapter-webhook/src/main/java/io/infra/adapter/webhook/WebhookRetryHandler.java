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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Webhook Retry Handler
 * 
 * <p>Provides exponential backoff retry strategy for handling Webhook delivery failures.</p>
 * 
 * <h2>Retry Strategy</h2>
 * <ul>
 *   <li>Exponential backoff: Delay doubles with each retry</li>
 *   <li>Maximum delay limit: Prevents excessively long delays</li>
 *   <li>Jitter: Random jitter prevents thundering herd problem</li>
 * </ul>
 * 
 * <h2>Delay Calculation</h2>
 * <pre>
 * delay = min(baseDelay * 2^attempt, maxDelay) * (1 + random * jitterFactor)
 * </pre>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * WebhookRetryHandler retryHandler = WebhookRetryHandler.builder()
 *     .maxRetries(3)
 *     .baseDelay(Duration.ofSeconds(1))
 *     .maxDelay(Duration.ofMinutes(1))
 *     .jitterFactor(0.1)
 *     .build();
 * 
 * CompletableFuture<String> result = retryHandler.executeWithRetry(() -> {
 *     return sendWebhook(payload);
 * });
 * }</pre>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
public final class WebhookRetryHandler {
    
    /**
     * Default maximum retry count
     */
    private static final int DEFAULT_MAX_RETRIES = 3;
    
    /**
     * Default base delay
     */
    private static final Duration DEFAULT_BASE_DELAY = Duration.ofSeconds(1);
    
    /**
     * Default maximum delay
     */
    private static final Duration DEFAULT_MAX_DELAY = Duration.ofMinutes(5);
    
    /**
     * Default jitter factor
     */
    private static final double DEFAULT_JITTER_FACTOR = 0.1;
    
    /**
     * Maximum retry count
     */
    private final int maxRetries;
    
    /**
     * Base delay time
     */
    private final Duration baseDelay;
    
    /**
     * Maximum delay time
     */
    private final Duration maxDelay;
    
    /**
     * Jitter factor (0.0 - 1.0)
     */
    private final double jitterFactor;
    
    /**
     * Scheduled executor (for delayed retry execution)
     */
    private final ScheduledExecutorService scheduler;
    
    /**
     * Whether scheduler is internally created (needs shutdown)
     */
    private final boolean ownScheduler;
    
    /**
     * Private constructor, instances created via Builder
     *
     * @param builder Builder instance
     */
    private WebhookRetryHandler(Builder builder) {
        this.maxRetries = builder.maxRetries > 0 ? builder.maxRetries : DEFAULT_MAX_RETRIES;
        this.baseDelay = builder.baseDelay != null ? builder.baseDelay : DEFAULT_BASE_DELAY;
        this.maxDelay = builder.maxDelay != null ? builder.maxDelay : DEFAULT_MAX_DELAY;
        this.jitterFactor = builder.jitterFactor >= 0 && builder.jitterFactor <= 1 
                ? builder.jitterFactor : DEFAULT_JITTER_FACTOR;
        
        if (builder.scheduler != null) {
            this.scheduler = builder.scheduler;
            this.ownScheduler = false;
        } else {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "webhook-retry-scheduler");
                t.setDaemon(true);
                return t;
            });
            this.ownScheduler = true;
        }
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
     * Creates a retry handler with default configuration
     *
     * @return Default configured retry handler
     */
    public static WebhookRetryHandler createDefault() {
        return builder().build();
    }
    
    /**
     * Executes operation with retry
     * 
     * <p>If operation fails, will retry according to configured retry policy.</p>
     *
     * @param <T> Return type
     * @param operation Operation to execute
     * @return Async result
     */
    public <T> CompletableFuture<T> executeWithRetry(Supplier<T> operation) {
        return executeWithRetry(operation, 0, null);
    }
    
    /**
     * Executes async operation with retry
     *
     * @param <T> Return type
     * @param asyncOperation Async operation to execute
     * @return Async result
     */
    public <T> CompletableFuture<T> executeAsyncWithRetry(Supplier<CompletableFuture<T>> asyncOperation) {
        return executeAsyncWithRetryInternal(asyncOperation, 0, null);
    }
    
    /**
     * Internal retry execution method
     */
    private <T> CompletableFuture<T> executeWithRetry(
            Supplier<T> operation, 
            int currentAttempt, 
            Throwable lastException) {
        
        CompletableFuture<T> future = new CompletableFuture<>();
        
        try {
            T result = operation.get();
            future.complete(result);
        } catch (Exception e) {
            if (currentAttempt >= maxRetries) {
                // Max retries reached, return failure
                RuntimeException finalException = new RuntimeException(
                        String.format("Still failed after %d retries", maxRetries),
                        e
                );
                future.completeExceptionally(finalException);
            } else {
                // Calculate delay and retry
                long delayMs = calculateDelay(currentAttempt);
                scheduler.schedule(
                        () -> executeWithRetry(operation, currentAttempt + 1, e)
                                .whenComplete((result, ex) -> {
                                    if (ex != null) {
                                        future.completeExceptionally(ex);
                                    } else {
                                        future.complete(result);
                                    }
                                }),
                        delayMs,
                        TimeUnit.MILLISECONDS
                );
            }
        }
        
        return future;
    }
    
    /**
     * Internal async retry execution method
     */
    private <T> CompletableFuture<T> executeAsyncWithRetryInternal(
            Supplier<CompletableFuture<T>> asyncOperation,
            int currentAttempt,
            Throwable lastException) {
        
        CompletableFuture<T> result = new CompletableFuture<>();
        
        try {
            asyncOperation.get().whenComplete((value, ex) -> {
                if (ex == null) {
                    result.complete(value);
                } else if (currentAttempt >= maxRetries) {
                    RuntimeException finalException = new RuntimeException(
                            String.format("Still failed after %d retries", maxRetries),
                            ex
                    );
                    result.completeExceptionally(finalException);
                } else {
                    long delayMs = calculateDelay(currentAttempt);
                    scheduler.schedule(
                            () -> executeAsyncWithRetryInternal(asyncOperation, currentAttempt + 1, ex)
                                    .whenComplete((v, e) -> {
                                        if (e != null) {
                                            result.completeExceptionally(e);
                                        } else {
                                            result.complete(v);
                                        }
                                    }),
                            delayMs,
                            TimeUnit.MILLISECONDS
                    );
                }
            });
        } catch (Exception e) {
            if (currentAttempt >= maxRetries) {
                result.completeExceptionally(new RuntimeException(
                        String.format("Still failed after %d retries", maxRetries), e));
            } else {
                long delayMs = calculateDelay(currentAttempt);
                scheduler.schedule(
                        () -> executeAsyncWithRetryInternal(asyncOperation, currentAttempt + 1, e)
                                .whenComplete((v, ex) -> {
                                    if (ex != null) {
                                        result.completeExceptionally(ex);
                                    } else {
                                        result.complete(v);
                                    }
                                }),
                        delayMs,
                        TimeUnit.MILLISECONDS
                );
            }
        }
        
        return result;
    }
    
    /**
     * Calculates retry delay (with exponential backoff and jitter)
     *
     * @param attempt Current retry count (starting from 0)
     * @return Delay in milliseconds
     */
    long calculateDelay(int attempt) {
        // Exponential backoff: baseDelay * 2^attempt
        long exponentialDelay = baseDelay.toMillis() * (1L << attempt);
        
        // Cap maximum delay
        long cappedDelay = Math.min(exponentialDelay, maxDelay.toMillis());
        
        // Add jitter
        double jitter = 1.0 + (Math.random() * jitterFactor * 2 - jitterFactor);
        
        return (long) (cappedDelay * jitter);
    }
    
    /**
     * Shuts down retry handler
     * 
     * <p>If using internally created scheduler, will shut it down.</p>
     */
    public void shutdown() {
        if (ownScheduler && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    // ========== Getter methods ==========
    
    /**
     * Gets maximum retry count
     *
     * @return Maximum retry count
     */
    public int getMaxRetries() {
        return maxRetries;
    }
    
    /**
     * Gets base delay
     *
     * @return Base delay
     */
    public Duration getBaseDelay() {
        return baseDelay;
    }
    
    /**
     * Gets maximum delay
     *
     * @return Maximum delay
     */
    public Duration getMaxDelay() {
        return maxDelay;
    }
    
    /**
     * Gets jitter factor
     *
     * @return Jitter factor
     */
    public double getJitterFactor() {
        return jitterFactor;
    }
    
    @Override
    public String toString() {
        return "WebhookRetryHandler{" +
                "maxRetries=" + maxRetries +
                ", baseDelay=" + baseDelay +
                ", maxDelay=" + maxDelay +
                ", jitterFactor=" + jitterFactor +
                '}';
    }
    
    /**
     * WebhookRetryHandler Builder
     */
    public static final class Builder {
        
        private int maxRetries;
        private Duration baseDelay;
        private Duration maxDelay;
        private double jitterFactor = DEFAULT_JITTER_FACTOR;
        private ScheduledExecutorService scheduler;
        
        private Builder() {
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
         * Sets base delay
         *
         * @param baseDelay Base delay
         * @return Builder instance
         */
        public Builder baseDelay(Duration baseDelay) {
            this.baseDelay = baseDelay;
            return this;
        }
        
        /**
         * Sets maximum delay
         *
         * @param maxDelay Maximum delay
         * @return Builder instance
         */
        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }
        
        /**
         * Sets jitter factor
         *
         * @param jitterFactor Jitter factor (0.0 - 1.0)
         * @return Builder instance
         */
        public Builder jitterFactor(double jitterFactor) {
            this.jitterFactor = jitterFactor;
            return this;
        }
        
        /**
         * Sets custom scheduler
         *
         * @param scheduler Scheduled executor service
         * @return Builder instance
         */
        public Builder scheduler(ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
            return this;
        }
        
        /**
         * Builds WebhookRetryHandler instance
         *
         * @return WebhookRetryHandler instance
         */
        public WebhookRetryHandler build() {
            return new WebhookRetryHandler(this);
        }
    }
    
    /**
     * Retry Context
     * 
     * <p>Used for passing context information during retry process</p>
     */
    public static final class RetryContext {
        
        private final int attempt;
        private final int maxAttempts;
        private final Throwable lastException;
        private final long nextDelayMs;
        
        /**
         * Creates retry context
         *
         * @param attempt Current retry count
         * @param maxAttempts Maximum retry count
         * @param lastException Last exception
         * @param nextDelayMs Next delay in milliseconds
         */
        public RetryContext(int attempt, int maxAttempts, Throwable lastException, long nextDelayMs) {
            this.attempt = attempt;
            this.maxAttempts = maxAttempts;
            this.lastException = lastException;
            this.nextDelayMs = nextDelayMs;
        }
        
        /**
         * Gets current retry count
         *
         * @return Current retry count
         */
        public int getAttempt() {
            return attempt;
        }
        
        /**
         * Gets maximum retry count
         *
         * @return Maximum retry count
         */
        public int getMaxAttempts() {
            return maxAttempts;
        }
        
        /**
         * Gets last exception
         *
         * @return Last exception
         */
        public Throwable getLastException() {
            return lastException;
        }
        
        /**
         * Gets next delay in milliseconds
         *
         * @return Next delay in milliseconds
         */
        public long getNextDelayMs() {
            return nextDelayMs;
        }
        
        /**
         * Whether there are more retry attempts remaining
         *
         * @return Whether can continue retrying
         */
        public boolean hasMoreAttempts() {
            return attempt < maxAttempts;
        }
    }
}
