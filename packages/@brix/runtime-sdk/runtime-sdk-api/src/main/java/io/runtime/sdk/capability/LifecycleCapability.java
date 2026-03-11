/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.capability;

import io.runtime.sdk.context.RuntimeContext;

/**
 * Lifecycle Capability Contract
 * 
 * <p>Defines the complete lifecycle callback interface for modules, called by Runtime Shell at appropriate times.
 * Modules implement this interface to respond to lifecycle events and perform initialization, startup, stop, and destroy operations.</p>
 * 
 * <h3>Lifecycle State Machine</h3>
 * <pre>{@code
 * REGISTERED -> INITIALIZING -> STARTING -> RUNNING -> STOPPING -> DESTROYED
 *                   |                          |
 *                   v                          v
 *               (Failure)                   DEGRADED
 * }</pre>
 * 
 * <h3>Callback Order</h3>
 * <ol>
 *   <li>{@link #onInit(RuntimeContext)} - Called after dependency injection completes</li>
 *   <li>{@link #onStart()} - Called after all dependent modules have started</li>
 *   <li>{@link #healthCheck()} - Called periodically during runtime</li>
 *   <li>{@link #onStop()} - Called when stop signal is received</li>
 *   <li>{@link #onDestroy()} - Called when removed from runtime</li>
 * </ol>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * public class BookingModule implements LifecycleCapability {
 *     private RuntimeContext context;
 *     private ScheduledExecutorService scheduler;
 *     
 *     @Override
 *     public void onInit(RuntimeContext context) {
 *         this.context = context;
 *         // Load configuration
 *         int poolSize = context.getConfigStore().getInt("booking.thread-pool-size", 10);
 *         this.scheduler = Executors.newScheduledThreadPool(poolSize);
 *     }
 *     
 *     @Override
 *     public void onStart() {
 *         // Start background tasks
 *         scheduler.scheduleAtFixedRate(this::cleanExpiredBookings, 0, 1, TimeUnit.HOURS);
 *         
 *         // Publish module ready event
 *         context.getEventBus().publishIntegration(new ModuleReadyEvent("booking"));
 *     }
 *     
 *     @Override
 *     public HealthStatus healthCheck() {
 *         // Check database connections, etc.
 *         return HealthStatus.UP;
 *     }
 *     
 *     @Override
 *     public void onStop() {
 *         // Graceful shutdown
 *         scheduler.shutdown();
 *     }
 *     
 *     @Override
 *     public void onDestroy() {
 *         // Clean up resources
 *         scheduler.shutdownNow();
 *     }
 * }
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see RuntimeContext
 * @see HealthStatus
 * @see ModuleMetadata
 */
public interface LifecycleCapability {

    /**
     * Module initialization callback
     * 
     * <p>Called after dependency injection completes, before module startup. Used for:</p>
     * <ul>
     *   <li>Loading configuration</li>
     *   <li>Initializing resource pools (thread pools, connection pools)</li>
     *   <li>Establishing external connections</li>
     *   <li>Registering internal components</li>
     * </ul>
     * 
     * <p><b>Note</b>: Other modules may not have completed initialization at this point. Do not call services from other modules.</p>
     * 
     * @param context the runtime context providing access to all capabilities
     * @throws ModuleInitializationException if initialization fails
     */
    void onInit(RuntimeContext context);

    /**
     * Module startup callback
     * 
     * <p>Called after all dependent modules have started. Used for:</p>
     * <ul>
     *   <li>Registering API routes</li>
     *   <li>Starting background tasks</li>
     *   <li>Subscribing to events</li>
     *   <li>Publishing "module ready" events</li>
     * </ul>
     * 
     * <p>At this point, it is safe to interact with other modules.</p>
     * 
     * @throws ModuleStartupException if startup fails
     */
    void onStart();

    /**
     * Module stop callback
     * 
     * <p>Called when stop signal is received for graceful shutdown. Should:</p>
     * <ul>
     *   <li>Stop accepting new requests</li>
     *   <li>Wait for in-progress requests to complete</li>
     *   <li>Save necessary state</li>
     *   <li>Release resources</li>
     * </ul>
     * 
     * <p><b>Timeout handling</b>: Should complete within graceful-shutdown.timeout configured in manifest</p>
     */
    void onStop();

    /**
     * Module destroy callback
     * 
     * <p>Called when module is completely removed from runtime. Used for:</p>
     * <ul>
     *   <li>Cleaning up temporary files</li>
     *   <li>Unregistering external registrations (service discovery, etc.)</li>
     *   <li>Closing all connections</li>
     * </ul>
     * 
     * <p>After this method is called, the module instance will be garbage collected.</p>
     */
    void onDestroy();

    /**
     * Health check
     * 
     * <p>Runtime Shell periodically calls this method to check module health.
     * Recommended checks include:</p>
     * <ul>
     *   <li>Database connection status</li>
     *   <li>External service reachability</li>
     *   <li>Critical resource availability</li>
     * </ul>
     * 
     * @return the health status
     * @see HealthStatus
     */
    HealthStatus healthCheck();

    /**
     * Gets module metadata
     * 
     * <p>Returns basic information about the module for registration and monitoring.</p>
     * 
     * @return the module metadata
     * @see ModuleMetadata
     */
    ModuleMetadata getMetadata();
}
