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
package io.brix.platform.tenant.decorator;

import io.brix.platform.common.tenant.TenantContext;
import org.springframework.core.task.TaskDecorator;

/**
 * Task Decorator for propagating tenant context to async threads.
 * 
 * <p>This decorator ensures that tenant context (tenant ID, user ID, and tenant info)
 * is properly propagated when tasks are executed asynchronously via Spring's
 * {@code @Async} annotation or {@code TaskExecutor}.</p>
 * 
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer (platform-tenant module)</p>
 * 
 * <h3>Design Rationale</h3>
 * <p>In a multi-tenant system, async operations must maintain tenant isolation.
 * Without context propagation, async tasks would lose the tenant context,
 * potentially causing security violations or data access errors.</p>
 * 
 * <h3>Configuration Example</h3>
 * <pre>{@code
 * @Configuration
 * @EnableAsync
 * public class AsyncConfig implements AsyncConfigurer {
 *     
 *     @Override
 *     public Executor getAsyncExecutor() {
 *         ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *         executor.setCorePoolSize(10);
 *         executor.setMaxPoolSize(50);
 *         executor.setQueueCapacity(100);
 *         executor.setThreadNamePrefix("async-tenant-");
 *         
 *         // Apply tenant context decorator
 *         executor.setTaskDecorator(new TenantTaskDecorator());
 *         
 *         executor.initialize();
 *         return executor;
 *     }
 * }
 * }</pre>
 * 
 * <h3>How It Works</h3>
 * <ol>
 *   <li>When a task is submitted, the decorator captures current tenant context</li>
 *   <li>The task is wrapped using {@link TenantContext#wrap(Runnable)}</li>
 *   <li>When the task executes in the async thread, context is automatically restored</li>
 *   <li>After execution, the async thread's context is cleaned up</li>
 * </ol>
 * 
 * <h3>Thread Safety</h3>
 * <p>This decorator is thread-safe and stateless. Each decoration captures
 * the context at decoration time (in the calling thread) and applies it
 * at execution time (in the worker thread).</p>
 * 
 * <h3>Important Notes</h3>
 * <ul>
 *   <li>The decorator captures context at task submission time, not execution time</li>
 *   <li>If no tenant context exists when decorating, the task runs without context</li>
 *   <li>Context changes in the original thread after submission are NOT reflected</li>
 *   <li>Compatible with Spring's @Async and CompletableFuture async operations</li>
 * </ul>
 * 
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantContext#wrap(Runnable)
 * @see org.springframework.core.task.TaskDecorator
 * @see org.springframework.scheduling.annotation.Async
 */
public class TenantTaskDecorator implements TaskDecorator {

    /**
     * Decorates the given runnable with tenant context propagation.
     * 
     * <p>This method wraps the provided runnable using {@link TenantContext#wrap(Runnable)},
     * which captures the current tenant context and ensures it is available when the
     * runnable executes in a different thread.</p>
     * 
     * @param runnable the original runnable task to decorate
     * @return a new runnable that will propagate tenant context
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        /*
         * Implementation Note:
         * 
         * We delegate to TenantContext.wrap() which handles:
         * 1. Capturing current tenant ID, user ID, and tenant info
         * 2. Setting context in the target thread before execution
         * 3. Cleaning up context after execution (success or failure)
         * 4. Restoring any previous context in the target thread
         * 
         * This ensures proper isolation even when thread pools reuse threads.
         */
        return TenantContext.wrap(runnable);
    }
}
