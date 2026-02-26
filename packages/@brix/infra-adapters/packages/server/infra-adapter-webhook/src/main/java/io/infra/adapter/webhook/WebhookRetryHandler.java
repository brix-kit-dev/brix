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
 * Webhook 重试处理器
 * 
 * <p>提供指数退避重试策略，用于处理 Webhook 发送失败的情况。</p>
 * 
 * <h2>重试策略</h2>
 * <ul>
 *   <li>指数退避：每次重试延迟翻倍</li>
 *   <li>最大延迟限制：防止延迟过长</li>
 *   <li>抖动：随机抖动防止雷鸣群问题</li>
 * </ul>
 * 
 * <h2>延迟计算</h2>
 * <pre>
 * delay = min(baseDelay * 2^attempt, maxDelay) * (1 + random * jitterFactor)
 * </pre>
 * 
 * <h2>使用示例</h2>
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
     * 默认最大重试次数
     */
    private static final int DEFAULT_MAX_RETRIES = 3;
    
    /**
     * 默认基础延迟
     */
    private static final Duration DEFAULT_BASE_DELAY = Duration.ofSeconds(1);
    
    /**
     * 默认最大延迟
     */
    private static final Duration DEFAULT_MAX_DELAY = Duration.ofMinutes(5);
    
    /**
     * 默认抖动因子
     */
    private static final double DEFAULT_JITTER_FACTOR = 0.1;
    
    /**
     * 最大重试次数
     */
    private final int maxRetries;
    
    /**
     * 基础延迟时间
     */
    private final Duration baseDelay;
    
    /**
     * 最大延迟时间
     */
    private final Duration maxDelay;
    
    /**
     * 抖动因子（0.0 - 1.0）
     */
    private final double jitterFactor;
    
    /**
     * 调度执行器（用于延迟执行重试）
     */
    private final ScheduledExecutorService scheduler;
    
    /**
     * 是否为内部创建的调度器（需要关闭）
     */
    private final boolean ownScheduler;
    
    /**
     * 私有构造函数，通过 Builder 创建实例
     *
     * @param builder 构建器实例
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
     * 创建新的构建器实例
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 创建默认配置的重试处理器
     *
     * @return 默认配置的重试处理器
     */
    public static WebhookRetryHandler createDefault() {
        return builder().build();
    }
    
    /**
     * 带重试执行操作
     * 
     * <p>如果操作失败，将按照配置的重试策略进行重试。</p>
     *
     * @param <T> 返回值类型
     * @param operation 要执行的操作
     * @return 异步结果
     */
    public <T> CompletableFuture<T> executeWithRetry(Supplier<T> operation) {
        return executeWithRetry(operation, 0, null);
    }
    
    /**
     * 带重试执行异步操作
     *
     * @param <T> 返回值类型
     * @param asyncOperation 要执行的异步操作
     * @return 异步结果
     */
    public <T> CompletableFuture<T> executeAsyncWithRetry(Supplier<CompletableFuture<T>> asyncOperation) {
        return executeAsyncWithRetryInternal(asyncOperation, 0, null);
    }
    
    /**
     * 内部重试执行方法
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
                // 达到最大重试次数，返回失败
                RuntimeException finalException = new RuntimeException(
                        String.format("重试 %d 次后仍然失败", maxRetries),
                        e
                );
                future.completeExceptionally(finalException);
            } else {
                // 计算延迟并重试
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
     * 内部异步重试执行方法
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
                            String.format("重试 %d 次后仍然失败", maxRetries),
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
                        String.format("重试 %d 次后仍然失败", maxRetries), e));
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
     * 计算重试延迟（带指数退避和抖动）
     *
     * @param attempt 当前重试次数（从 0 开始）
     * @return 延迟毫秒数
     */
    long calculateDelay(int attempt) {
        // 指数退避：baseDelay * 2^attempt
        long exponentialDelay = baseDelay.toMillis() * (1L << attempt);
        
        // 限制最大延迟
        long cappedDelay = Math.min(exponentialDelay, maxDelay.toMillis());
        
        // 添加抖动
        double jitter = 1.0 + (Math.random() * jitterFactor * 2 - jitterFactor);
        
        return (long) (cappedDelay * jitter);
    }
    
    /**
     * 关闭重试处理器
     * 
     * <p>如果使用内部创建的调度器，将关闭调度器。</p>
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
    
    // ========== Getter 方法 ==========
    
    /**
     * 获取最大重试次数
     *
     * @return 最大重试次数
     */
    public int getMaxRetries() {
        return maxRetries;
    }
    
    /**
     * 获取基础延迟
     *
     * @return 基础延迟
     */
    public Duration getBaseDelay() {
        return baseDelay;
    }
    
    /**
     * 获取最大延迟
     *
     * @return 最大延迟
     */
    public Duration getMaxDelay() {
        return maxDelay;
    }
    
    /**
     * 获取抖动因子
     *
     * @return 抖动因子
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
     * WebhookRetryHandler 构建器
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
         * 设置基础延迟
         *
         * @param baseDelay 基础延迟
         * @return Builder 实例
         */
        public Builder baseDelay(Duration baseDelay) {
            this.baseDelay = baseDelay;
            return this;
        }
        
        /**
         * 设置最大延迟
         *
         * @param maxDelay 最大延迟
         * @return Builder 实例
         */
        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }
        
        /**
         * 设置抖动因子
         *
         * @param jitterFactor 抖动因子（0.0 - 1.0）
         * @return Builder 实例
         */
        public Builder jitterFactor(double jitterFactor) {
            this.jitterFactor = jitterFactor;
            return this;
        }
        
        /**
         * 设置自定义调度器
         *
         * @param scheduler 调度执行器
         * @return Builder 实例
         */
        public Builder scheduler(ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
            return this;
        }
        
        /**
         * 构建 WebhookRetryHandler 实例
         *
         * @return WebhookRetryHandler 实例
         */
        public WebhookRetryHandler build() {
            return new WebhookRetryHandler(this);
        }
    }
    
    /**
     * 重试上下文
     * 
     * <p>用于在重试过程中传递上下文信息</p>
     */
    public static final class RetryContext {
        
        private final int attempt;
        private final int maxAttempts;
        private final Throwable lastException;
        private final long nextDelayMs;
        
        /**
         * 创建重试上下文
         *
         * @param attempt 当前重试次数
         * @param maxAttempts 最大重试次数
         * @param lastException 上次异常
         * @param nextDelayMs 下次延迟毫秒数
         */
        public RetryContext(int attempt, int maxAttempts, Throwable lastException, long nextDelayMs) {
            this.attempt = attempt;
            this.maxAttempts = maxAttempts;
            this.lastException = lastException;
            this.nextDelayMs = nextDelayMs;
        }
        
        /**
         * 获取当前重试次数
         *
         * @return 当前重试次数
         */
        public int getAttempt() {
            return attempt;
        }
        
        /**
         * 获取最大重试次数
         *
         * @return 最大重试次数
         */
        public int getMaxAttempts() {
            return maxAttempts;
        }
        
        /**
         * 获取上次异常
         *
         * @return 上次异常
         */
        public Throwable getLastException() {
            return lastException;
        }
        
        /**
         * 获取下次延迟毫秒数
         *
         * @return 下次延迟毫秒数
         */
        public long getNextDelayMs() {
            return nextDelayMs;
        }
        
        /**
         * 是否还有重试机会
         *
         * @return 是否可以继续重试
         */
        public boolean hasMoreAttempts() {
            return attempt < maxAttempts;
        }
    }
}
