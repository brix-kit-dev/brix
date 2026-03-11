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
package io.runtime.orchestrator.lifecycle;

import io.runtime.manifest.model.ModuleManifest;
import io.runtime.orchestrator.registry.ModuleRegistry;
import io.runtime.sdk.capability.HealthStatus;
import io.runtime.sdk.capability.LifecycleCapability;
import io.runtime.sdk.context.RuntimeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Default Module Lifecycle Manager Implementation.
 * 
 * <p>Thread-safe lifecycle manager supporting parallel and sequential module startup.
 * Includes capability validation, dependency checking, and lifecycle event management.</p>
 * 
 * <h3>Core Features</h3>
 * <ul>
 *   <li>Start modules in topological order (considering dependencies)</li>
 *   <li>Capability validation (reject startup if required capability is missing)</li>
 *   <li>Lifecycle event notifications</li>
 *   <li>Health check</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class DefaultModuleLifecycleManager implements ModuleLifecycleManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultModuleLifecycleManager.class);

    /**
     * Module registry.
     */
    private final ModuleRegistry registry;

    /**
     * Context factory.
     */
    private volatile RuntimeContextFactory contextFactory;

    /**
     * Capability provider (used to validate module required capabilities).
     */
    private volatile CapabilityProvider capabilityProvider;

    /**
     * Lifecycle listener list.
     */
    private final List<LifecycleListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Manager state.
     */
    private volatile LifecycleManagerState state = LifecycleManagerState.CREATED;

    /**
     * Executor.
     */
    private final ExecutorService executor;

    /**
     * Module context cache.
     */
    private final Map<String, RuntimeContext> contextCache = new ConcurrentHashMap<>();

    /**
     * Creates default lifecycle manager.
     * 
     * @param registry module registry
     */
    public DefaultModuleLifecycleManager(ModuleRegistry registry) {
        this(registry, null);
    }

    /**
     * Creates default lifecycle manager.
     * 
     * @param registry module registry
     * @param contextFactory context factory
     */
    public DefaultModuleLifecycleManager(ModuleRegistry registry, RuntimeContextFactory contextFactory) {
        this.registry = Objects.requireNonNull(registry, "Registry cannot be null");
        this.contextFactory = contextFactory;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "lifecycle-manager");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Void> initializeAll() {
        if (contextFactory == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Context factory not set"));
        }

        state = LifecycleManagerState.INITIALIZING;
        logger.info("Initializing all modules...");

        List<LifecycleCapability> modules = registry.getByStartupOrder();
        
        return CompletableFuture.runAsync(() -> {
            for (LifecycleCapability module : modules) {
                String moduleId = module.getMetadata().getModuleId();
                try {
                    initializeModule(module, moduleId);
                } catch (Exception e) {
                    state = LifecycleManagerState.ERROR;
                    throw new ModuleLifecycleException(moduleId, LifecyclePhase.INIT, e);
                }
            }
            state = LifecycleManagerState.INITIALIZED;
            logger.info("All modules initialized successfully");
        }, executor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Void> initialize(String moduleId) {
        return CompletableFuture.runAsync(() -> {
            LifecycleCapability module = registry.getRequired(moduleId);
            initializeModule(module, moduleId);
        }, executor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Void> startAll() {
        state = LifecycleManagerState.STARTING;
        logger.info("Starting all modules...");

        List<LifecycleCapability> modules = registry.getByStartupOrder();

        return CompletableFuture.runAsync(() -> {
            for (LifecycleCapability module : modules) {
                String moduleId = module.getMetadata().getModuleId();
                try {
                    startModule(module, moduleId);
                } catch (Exception e) {
                    state = LifecycleManagerState.ERROR;
                    throw new ModuleLifecycleException(moduleId, LifecyclePhase.START, e);
                }
            }
            state = LifecycleManagerState.RUNNING;
            logger.info("All modules started successfully");
        }, executor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Void> start(String moduleId) {
        return CompletableFuture.runAsync(() -> {
            LifecycleCapability module = registry.getRequired(moduleId);
            startModule(module, moduleId);
        }, executor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Void> stopAll() {
        state = LifecycleManagerState.STOPPING;
        logger.info("Stopping all modules...");

        // Reverse order stop
        List<LifecycleCapability> modules = registry.getByShutdownOrder();

        return CompletableFuture.runAsync(() -> {
            for (LifecycleCapability module : modules) {
                String moduleId = module.getMetadata().getModuleId();
                try {
                    stopModule(module, moduleId);
                } catch (Exception e) {
                    // Errors during stop should not interrupt the flow
                    logger.error("Error stopping module: {}", moduleId, e);
                    notifyError(moduleId, LifecyclePhase.STOP, e);
                }
            }
            state = LifecycleManagerState.STOPPED;
            logger.info("All modules stopped");
        }, executor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Void> stop(String moduleId) {
        return CompletableFuture.runAsync(() -> {
            LifecycleCapability module = registry.getRequired(moduleId);
            stopModule(module, moduleId);
        }, executor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Void> destroyAll() {
        logger.info("Destroying all modules...");

        List<LifecycleCapability> modules = registry.getByShutdownOrder();

        return CompletableFuture.runAsync(() -> {
            for (LifecycleCapability module : modules) {
                String moduleId = module.getMetadata().getModuleId();
                try {
                    destroyModule(module, moduleId);
                } catch (Exception e) {
                    logger.error("Error destroying module: {}", moduleId, e);
                }
            }
            contextCache.clear();
            logger.info("All modules destroyed");
        }, executor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, HealthStatus> checkHealth() {
        Map<String, HealthStatus> result = new HashMap<>();
        for (LifecycleCapability module : registry.getAll()) {
            String moduleId = module.getMetadata().getModuleId();
            try {
                result.put(moduleId, module.healthCheck());
            } catch (Exception e) {
                logger.warn("Health check failed for module: {}", moduleId, e);
                result.put(moduleId, HealthStatus.DOWN);
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HealthStatus checkHealth(String moduleId) {
        try {
            return registry.getRequired(moduleId).healthCheck();
        } catch (Exception e) {
            logger.warn("Health check failed for module: {}", moduleId, e);
            return HealthStatus.DOWN;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Void> restart(String moduleId) {
        return stop(moduleId)
            .thenCompose(v -> start(moduleId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setContextFactory(RuntimeContextFactory contextFactory) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "Context factory cannot be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addListener(LifecycleListener listener) {
        listeners.add(Objects.requireNonNull(listener, "Listener cannot be null"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeListener(LifecycleListener listener) {
        listeners.remove(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LifecycleManagerState getState() {
        return state;
    }

    // ==================== Private Methods ====================

    private void initializeModule(LifecycleCapability module, String moduleId) {
        logger.debug("Initializing module: {}", moduleId);
        
        notifyBeforeInit(moduleId);
        
        try {
            RuntimeContext context = contextFactory.createContext(moduleId);
            contextCache.put(moduleId, context);
            module.onInit(context);
            notifyAfterInit(moduleId, true);
            logger.info("Module initialized: {}", moduleId);
        } catch (Exception e) {
            notifyAfterInit(moduleId, false);
            notifyError(moduleId, LifecyclePhase.INIT, e);
            throw e;
        }
    }

    private void startModule(LifecycleCapability module, String moduleId) {
        logger.debug("Starting module: {}", moduleId);
        
        notifyBeforeStart(moduleId);
        
        try {
            module.onStart();
            notifyAfterStart(moduleId, true);
            logger.info("Module started: {}", moduleId);
        } catch (Exception e) {
            notifyAfterStart(moduleId, false);
            notifyError(moduleId, LifecyclePhase.START, e);
            throw e;
        }
    }

    private void stopModule(LifecycleCapability module, String moduleId) {
        logger.debug("Stopping module: {}", moduleId);
        
        notifyBeforeStop(moduleId);
        
        try {
            module.onStop();
            notifyAfterStop(moduleId);
            logger.info("Module stopped: {}", moduleId);
        } catch (Exception e) {
            notifyError(moduleId, LifecyclePhase.STOP, e);
            throw e;
        }
    }

    private void destroyModule(LifecycleCapability module, String moduleId) {
        logger.debug("Destroying module: {}", moduleId);
        
        notifyBeforeDestroy(moduleId);
        
        try {
            module.onDestroy();
            contextCache.remove(moduleId);
            notifyAfterDestroy(moduleId);
            logger.info("Module destroyed: {}", moduleId);
        } catch (Exception e) {
            notifyError(moduleId, LifecyclePhase.DESTROY, e);
            throw e;
        }
    }

    // ==================== Notification Methods ====================

    private void notifyBeforeInit(String moduleId) {
        listeners.forEach(l -> {
            try { l.beforeInit(moduleId); } catch (Exception e) { 
                logger.warn("Listener error", e); 
            }
        });
    }

    private void notifyAfterInit(String moduleId, boolean success) {
        listeners.forEach(l -> {
            try { l.afterInit(moduleId, success); } catch (Exception e) { 
                logger.warn("Listener error", e); 
            }
        });
    }

    private void notifyBeforeStart(String moduleId) {
        listeners.forEach(l -> {
            try { l.beforeStart(moduleId); } catch (Exception e) { 
                logger.warn("Listener error", e); 
            }
        });
    }

    private void notifyAfterStart(String moduleId, boolean success) {
        listeners.forEach(l -> {
            try { l.afterStart(moduleId, success); } catch (Exception e) { 
                logger.warn("Listener error", e); 
            }
        });
    }

    private void notifyBeforeStop(String moduleId) {
        listeners.forEach(l -> {
            try { l.beforeStop(moduleId); } catch (Exception e) { 
                logger.warn("Listener error", e); 
            }
        });
    }

    private void notifyAfterStop(String moduleId) {
        listeners.forEach(l -> {
            try { l.afterStop(moduleId); } catch (Exception e) { 
                logger.warn("Listener error", e); 
            }
        });
    }

    private void notifyBeforeDestroy(String moduleId) {
        listeners.forEach(l -> {
            try { l.beforeDestroy(moduleId); } catch (Exception e) { 
                logger.warn("Listener error", e); 
            }
        });
    }

    private void notifyAfterDestroy(String moduleId) {
        listeners.forEach(l -> {
            try { l.afterDestroy(moduleId); } catch (Exception e) { 
                logger.warn("Listener error", e); 
            }
        });
    }

    private void notifyError(String moduleId, LifecyclePhase phase, Throwable error) {
        listeners.forEach(l -> {
            try { l.onError(moduleId, phase, error); } catch (Exception e) { 
                logger.warn("Listener error", e); 
            }
        });
    }

    // ==================== Capability Validation Methods ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCapabilityProvider(CapabilityProvider capabilityProvider) {
        this.capabilityProvider = Objects.requireNonNull(capabilityProvider, 
            "CapabilityProvider cannot be null");
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Validation process:</p>
     * <ol>
     *   <li>Get required capabilities list from manifest (capabilities.required)</li>
     *   <li>Check if each required capability is provided by Host</li>
     *   <li>If missing capabilities exist, throw {@link CapabilityMissingException}</li>
     *   <li>For optional capability missing, only log warning</li>
     * </ol>
     */
    @Override
    public void validateCapabilities(ModuleManifest manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("Manifest cannot be null");
        }

        String moduleId = manifest.getModuleId();
        
        // If no capability provider is set, skip validation (allow use in test environments)
        if (capabilityProvider == null) {
            logger.warn("CapabilityProvider not set, skipping capability validation for module {}", moduleId);
            return;
        }

        // Validate required capabilities
        List<String> requiredCapabilities = manifest.getRequiredCapabilities();
        if (!requiredCapabilities.isEmpty()) {
            List<String> missingCapabilities = new ArrayList<>();
            
            for (String capability : requiredCapabilities) {
                if (!capabilityProvider.hasCapability(capability)) {
                    missingCapabilities.add(capability);
                }
            }
            
            if (!missingCapabilities.isEmpty()) {
                logger.error("Module {} startup failed: missing required capabilities {}", moduleId, missingCapabilities);
                throw new CapabilityMissingException(moduleId, missingCapabilities);
            }
            
            logger.debug("Module {} required capability validation passed: {}", moduleId, requiredCapabilities);
        }

        // Check optional capabilities (only log warning)
        List<String> optionalCapabilities = manifest.getOptionalCapabilities();
        if (!optionalCapabilities.isEmpty()) {
            List<String> missingOptional = new ArrayList<>();
            
            for (String capability : optionalCapabilities) {
                if (!capabilityProvider.hasCapability(capability)) {
                    missingOptional.add(capability);
                }
            }
            
            if (!missingOptional.isEmpty()) {
                logger.warn("Module {} missing optional capabilities {}, related features will be unavailable", moduleId, missingOptional);
            }
        }
    }
}
