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
package io.runtime.sdk.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.annotation.Module;
import io.runtime.sdk.capability.AuthContextCapability;
import io.runtime.sdk.capability.ConfigStoreCapability;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.HealthStatus;
import io.runtime.sdk.capability.LifecycleCapability;
import io.runtime.sdk.capability.LockCapability;
import io.runtime.sdk.capability.ModuleMetadata;
import io.runtime.sdk.capability.ObservabilityCapability;
import io.runtime.sdk.capability.ResilienceCapability;
import io.runtime.sdk.capability.SchedulingCapability;
import io.runtime.sdk.capability.StateStoreCapability;
import io.runtime.sdk.context.RuntimeContext;

/**
 * Legacy module abstract base class.
 * 
 * <p>Provides the v3.0.9 module lifecycle support implementation, encapsulating lifecycle
 * management and common capability access through {@link RuntimeContext}.</p>
 *
 * <p>Under the v3.0.10 Runtime Shell baseline, newly migrated plugins must expose
 * {@link io.runtime.sdk.plugin.BrixPlugin} instead of extending this class.
 * This class remains available so existing modules keep compiling during the staged
 * migration.</p>
 * 
 * <h3>Core Features</h3>
 * <ul>
 *   <li>Default implementation of lifecycle callbacks</li>
 *   <li>Convenient access to RuntimeContext</li>
 *   <li>Automatic extraction of module metadata</li>
 *   <li>Default health check implementation</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Module(id = "brix-app-booking", name = "Booking Management", version = "3.0.0")
 * public class BookingModule extends AbstractModule {
 *     
 *     private BookingRepository repository;
 *     
 *     @Override
 *     protected void doInit(RuntimeContext context) {
 *         // Initialize resources
 *         this.repository = new BookingRepository(context.getConfigStore());
 *         getLogger().info("Booking module initialized");
 *     }
 *     
 *     @Override
 *     protected void doStart() {
 *         // Start background tasks
 *         getScheduling().ifPresent(s -> 
 *             s.scheduleAtFixedRate("cleanup", Duration.ofHours(1), this::cleanupExpired));
 *         
 *         // Publish module ready event
 *         publishEvent(new ModuleReadyEvent(getModuleId()));
 *     }
 *     
 *     @Override
 *     protected void doStop() {
 *         // Save state, release resources
 *         repository.flush();
 *     }
 *     
 *     @Override
 *     protected HealthStatus doHealthCheck() {
 *         // Custom health check
 *         return repository.isConnected() ? HealthStatus.UP : HealthStatus.DOWN;
 *     }
 * }
 * }</pre>
 * 
 * <h3>Lifecycle Method Execution Order</h3>
 * <pre>{@code
 * 1. onInit(context)  -> doInit(context)
 * 2. onStart()        -> doStart()
 * 3. healthCheck()    -> doHealthCheck() [called periodically during runtime]
 * 4. onStop()         -> doStop()
 * 5. onDestroy()      -> doDestroy()
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @deprecated since 3.0.10 for new plugin migrations. Existing modules may continue to use this
 *             compatibility base class until they are moved to the v3.0.10 plugin SPI.
 * @see Module
 * @see LifecycleCapability
 */
@Deprecated(since = "3.0.10", forRemoval = false)
public abstract class AbstractModule implements LifecycleCapability {

    /**
     * Runtime context
     */
    private RuntimeContext context;

    /**
     * Module state
     */
    private volatile ModuleState state = ModuleState.REGISTERED;

    /**
     * Module metadata (extracted from annotation)
     */
    private ModuleMetadata metadata;

    /**
     * Logger
     */
    private Logger logger;

    /**
     * Default constructor
     * 
     * <p>Automatically extracts metadata from @Module annotation</p>
     */
    protected AbstractModule() {
        this.logger = LoggerFactory.getLogger(getClass());
        this.metadata = extractMetadataFromAnnotation();
    }

    // ==================== Lifecycle Implementation ====================

    /**
     * {@inheritDoc}
     * 
     * <p>Template method: calls {@link #doInit(RuntimeContext)} after setting context</p>
     */
    @Override
    public final void onInit(RuntimeContext context) {
        if (this.state != ModuleState.REGISTERED) {
            throw new IllegalStateException("Module already initialized: " + getModuleId());
        }
        
        this.context = context;
        this.state = ModuleState.INITIALIZING;
        
        logger.info("Initializing module: {}", getModuleId());
        
        try {
            doInit(context);
            this.state = ModuleState.INITIALIZED;
            logger.info("Module initialized successfully: {}", getModuleId());
        } catch (Exception e) {
            this.state = ModuleState.FAILED;
            logger.error("Failed to initialize module: {}", getModuleId(), e);
            throw new ModuleInitializationException(getModuleId(), e);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Template method: calls {@link #doStart()}</p>
     */
    @Override
    public final void onStart() {
        if (this.state != ModuleState.INITIALIZED) {
            throw new IllegalStateException("Module not initialized: " + getModuleId());
        }
        
        this.state = ModuleState.STARTING;
        logger.info("Starting module: {}", getModuleId());
        
        try {
            doStart();
            this.state = ModuleState.RUNNING;
            logger.info("Module started successfully: {}", getModuleId());
        } catch (Exception e) {
            this.state = ModuleState.FAILED;
            logger.error("Failed to start module: {}", getModuleId(), e);
            throw new ModuleStartupException(getModuleId(), e);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Template method: calls {@link #doStop()}</p>
     */
    @Override
    public final void onStop() {
        if (this.state != ModuleState.RUNNING && this.state != ModuleState.DEGRADED) {
            logger.warn("Module not running, skipping stop: {}", getModuleId());
            return;
        }
        
        this.state = ModuleState.STOPPING;
        logger.info("Stopping module: {}", getModuleId());
        
        try {
            doStop();
            this.state = ModuleState.STOPPED;
            logger.info("Module stopped successfully: {}", getModuleId());
        } catch (Exception e) {
            logger.error("Error stopping module: {}", getModuleId(), e);
            // Don't throw exception, continue stop process
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Template method: calls {@link #doDestroy()}</p>
     */
    @Override
    public final void onDestroy() {
        logger.info("Destroying module: {}", getModuleId());
        
        try {
            doDestroy();
            this.state = ModuleState.DESTROYED;
            logger.info("Module destroyed: {}", getModuleId());
        } catch (Exception e) {
            logger.error("Error destroying module: {}", getModuleId(), e);
        } finally {
            this.context = null;
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Template method: calls {@link #doHealthCheck()}</p>
     */
    @Override
    public final HealthStatus healthCheck() {
        if (this.state != ModuleState.RUNNING && this.state != ModuleState.DEGRADED) {
            return HealthStatus.DOWN;
        }
        
        try {
            HealthStatus status = doHealthCheck();
            if (status == HealthStatus.DEGRADED && this.state == ModuleState.RUNNING) {
                this.state = ModuleState.DEGRADED;
            } else if (status == HealthStatus.UP && this.state == ModuleState.DEGRADED) {
                this.state = ModuleState.RUNNING;
            }
            return status;
        } catch (Exception e) {
            logger.warn("Health check failed for module: {}", getModuleId(), e);
            return HealthStatus.DOWN;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ModuleMetadata getMetadata() {
        return metadata;
    }

    // ==================== Subclass Extension Points ====================

    /**
     * Initialization extension point
     * 
     * <p>Subclasses override this method to implement custom initialization logic</p>
     * 
     * @param context runtime context
     */
    protected void doInit(RuntimeContext context) {
        // Subclasses may override
    }

    /**
     * Startup extension point
     * 
     * <p>Subclasses override this method to implement custom startup logic</p>
     */
    protected void doStart() {
        // Subclasses may override
    }

    /**
     * Stop extension point
     * 
     * <p>Subclasses override this method to implement custom stop logic</p>
     */
    protected void doStop() {
        // Subclasses may override
    }

    /**
     * Destroy extension point
     * 
     * <p>Subclasses override this method to implement custom destroy logic</p>
     */
    protected void doDestroy() {
        // Subclasses may override
    }

    /**
     * Health check extension point
     * 
     * <p>Subclasses override this method to implement custom health check</p>
     * 
     * @return health status, defaults to UP
     */
    protected HealthStatus doHealthCheck() {
        return HealthStatus.UP;
    }

    // ==================== Convenience Methods ====================

    /**
     * Get runtime context
     * 
     * @return runtime context
     * @throws IllegalStateException if module not initialized
     */
    protected RuntimeContext getContext() {
        if (context == null) {
            throw new IllegalStateException("Module not initialized: " + getModuleId());
        }
        return context;
    }

    /**
     * Get module ID
     * 
     * @return unique module identifier
     */
    protected String getModuleId() {
        return metadata.getModuleId();
    }

    /**
     * Get module name
     * 
     * @return module display name
     */
    protected String getModuleName() {
        return metadata.getModuleName();
    }

    /**
     * Get logger
     * 
     * @return Logger instance
     */
    protected Logger getLogger() {
        return logger;
    }

    /**
     * Get module state
     * 
     * @return current module state
     */
    protected ModuleState getState() {
        return state;
    }

    /**
     * Get event bus
     * 
     * @return event bus capability
     */
    protected EventBusCapability getEventBus() {
        return getContext().getEventBus();
    }

    /**
     * Get state store
     * 
     * @return state store capability
     */
    protected StateStoreCapability getStateStore() {
        return getContext().getStateStore();
    }

    /**
     * Get authentication context
     * 
     * @return authentication context capability
     */
    protected AuthContextCapability getAuthContext() {
        return getContext().getAuthContext();
    }

    /**
     * Get observability capability
     * 
     * @return observability capability
     */
    protected ObservabilityCapability getObservability() {
        return getContext().getObservability();
    }

    /**
     * Get config store
     * 
     * @return config store capability
     */
    protected ConfigStoreCapability getConfigStore() {
        return getContext().getConfigStore();
    }

    /**
     * Get scheduling capability (optional)
     * 
     * @return scheduling capability
     */
    protected java.util.Optional<SchedulingCapability> getScheduling() {
        return getContext().getScheduling();
    }

    /**
     * Get distributed lock capability (optional)
     * 
     * @return distributed lock capability
     */
    protected java.util.Optional<LockCapability> getLock() {
        return getContext().getLock();
    }

    /**
     * Get resilience capability (optional)
     * 
     * @return resilience capability
     */
    protected java.util.Optional<ResilienceCapability> getResilience() {
        return getContext().getResilience();
    }

    /**
     * Convenience method to publish a domain event
     * 
     * @param event domain event
     */
    protected void publishEvent(io.runtime.sdk.event.DomainEvent event) {
        getEventBus().publish(event);
    }

    /**
     * Convenience method to publish an integration event
     * 
     * @param event integration event
     */
    protected void publishIntegrationEvent(io.runtime.sdk.event.IntegrationEvent event) {
        getEventBus().publishIntegration(event);
    }

    /**
     * Extract metadata from @Module annotation
     */
    private ModuleMetadata extractMetadataFromAnnotation() {
        Module annotation = getClass().getAnnotation(Module.class);
        if (annotation == null) {
            throw new IllegalStateException(
                "Module class must be annotated with @Module: " + getClass().getName());
        }
        
        return ModuleMetadata.builder()
            .moduleId(annotation.id())
            .moduleName(annotation.name())
            .version(annotation.version().isEmpty() ? "0.0.0" : annotation.version())
            .description(annotation.description())
            .startupOrder(annotation.startupOrder())
            .dependsOn(annotation.dependsOn())
            .build();
    }
}
