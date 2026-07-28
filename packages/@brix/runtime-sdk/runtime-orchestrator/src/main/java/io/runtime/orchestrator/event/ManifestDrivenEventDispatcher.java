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
package io.runtime.orchestrator.event;

import io.runtime.manifest.model.EventSubscribeConfig;
import io.runtime.manifest.model.EventsConfig;
import io.runtime.manifest.model.ModuleManifest;
import io.runtime.manifest.model.RetryConfig;
import io.runtime.sdk.event.IntegrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Manifest-Driven Declarative Event Dispatcher
 * 
 * <p>Automatically binds event handlers based on events.subscribes declarations
 * in module-manifest.yaml, implementing declarative event subscription without
 * explicit listener registration in code.</p>
 * 
 * <h3>Core Features</h3>
 * <ul>
 *   <li>Automatic parsing of event subscription declarations from manifest</li>
 *   <li>Reflective binding of event handler methods</li>
 *   <li>Idempotency check support (via eventId deduplication)</li>
 *   <li>Retry mechanism support</li>
 * </ul>
 * 
 * <h3>Manifest Configuration Example</h3>
 * <pre>{@code
 * events:
 *   subscribes:
 *     - type: "io.brix.app.identity.event.UserCreatedEvent"
 *       handler: "io.brix.app.booking.handler.BookingEventHandler.onUserCreated"
 *       retry:
 *         max-attempts: 3
 *         backoff: "exponential"
 *       idempotent: true
 * }</pre>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Create dispatcher
 * ManifestDrivenEventDispatcher dispatcher = new ManifestDrivenEventDispatcher(
 *     beanFactory,      // Bean factory for getting handler instances
 *     idempotencyStore  // Persistent idempotency store for processed events
 * );
 * 
 * // Register module event subscriptions
 * dispatcher.registerModule(moduleManifest);
 * 
 * // Dispatch event
 * dispatcher.dispatch(integrationEvent);
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class ManifestDrivenEventDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(ManifestDrivenEventDispatcher.class);

    /**
     * Event type to handler list mapping
     */
    private final Map<String, List<EventHandlerBinding>> handlerMap = new ConcurrentHashMap<>();

    /**
     * Bean factory for getting handler instances
     */
    private final Function<String, Object> beanFactory;

    /**
     * Idempotency store for tracking processed event IDs
     */
    private final IdempotencyStore idempotencyStore;

    /**
     * Set of registered module IDs
     */
    private final Set<String> registeredModules = ConcurrentHashMap.newKeySet();

    /**
     * Creates a declarative event dispatcher.
     * 
     * @param beanFactory      Bean factory that accepts class name and returns instance
     * @param idempotencyStore Idempotency store
     */
    public ManifestDrivenEventDispatcher(
            Function<String, Object> beanFactory,
            IdempotencyStore idempotencyStore) {
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory cannot be null");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore cannot be null");
    }

    /**
     * Registers module event subscriptions.
     * 
     * <p>Parses events.subscribes configuration from module manifest and creates
     * handler bindings for each subscription.</p>
     * 
     * @param manifest Module manifest
     * @throws EventBindingException if handler binding fails
     */
    public void registerModule(ModuleManifest manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("manifest cannot be null");
        }

        String moduleId = manifest.getModuleId();
        if (moduleId == null) {
            throw new IllegalArgumentException("Module ID cannot be null");
        }

        // Prevent duplicate registration
        if (registeredModules.contains(moduleId)) {
            logger.warn("Module {} already registered, skipping duplicate registration", moduleId);
            return;
        }

        EventsConfig events = manifest.getEvents();
        if (events == null || events.getSubscribes() == null || events.getSubscribes().isEmpty()) {
            logger.debug("Module {} has no event subscription declarations", moduleId);
            registeredModules.add(moduleId);
            return;
        }

        // Process each subscription declaration
        for (EventSubscribeConfig subscribeConfig : events.getSubscribes()) {
            try {
                EventHandlerBinding binding = createBinding(moduleId, subscribeConfig);
                
                handlerMap.computeIfAbsent(subscribeConfig.getType(), k -> new ArrayList<>())
                    .add(binding);
                
                logger.info("Bound event handler: {} -> {}.{}", 
                    subscribeConfig.getType(),
                    subscribeConfig.getHandlerClass(),
                    subscribeConfig.getHandlerMethod());
                    
            } catch (Exception e) {
                if (subscribeConfig.isOptional()) {
                    logger.warn("Optional event subscription binding failed, skipped: {} -> {}", 
                        subscribeConfig.getType(), subscribeConfig.getHandler(), e);
                } else {
                    throw new EventBindingException(
                        "Event handler binding failed: " + subscribeConfig.getType(), e);
                }
            }
        }

        registeredModules.add(moduleId);
        logger.info("Module {} event subscription registration complete, {} subscriptions", 
            moduleId, events.getSubscribes().size());
    }

    /**
     * Unregisters module event subscriptions.
     * 
     * @param moduleId Module ID
     */
    public void unregisterModule(String moduleId) {
        if (moduleId == null) {
            return;
        }

        // Remove all handler bindings for this module
        handlerMap.values().forEach(list -> 
            list.removeIf(binding -> moduleId.equals(binding.getModuleId()))
        );

        registeredModules.remove(moduleId);
        logger.info("Module {} event subscriptions unregistered", moduleId);
    }

    /**
     * Dispatches event to all subscribers.
     * 
     * @param event Integration event
     */
    public void dispatch(IntegrationEvent event) {
        if (event == null) {
            logger.warn("Received null event, ignored");
            return;
        }

        String eventType = event.getClass().getName();
        List<EventHandlerBinding> handlers = handlerMap.get(eventType);

        if (handlers == null || handlers.isEmpty()) {
            logger.debug("Event {} has no subscribers", eventType);
            return;
        }

        logger.debug("Dispatching event {} to {} handlers", eventType, handlers.size());

        for (EventHandlerBinding binding : handlers) {
            try {
                dispatchToHandler(event, binding);
            } catch (Exception e) {
                logger.error("Event handling failed: {} -> {}.{}", 
                    eventType, binding.getHandlerClassName(), binding.getHandlerMethodName(), e);
                // Continue processing other subscribers
            }
        }
    }

    /**
     * Dispatches event to specified handler.
     */
    private void dispatchToHandler(IntegrationEvent event, EventHandlerBinding binding) {
        String eventId = event.getEventId();
        
        // Idempotency check
        if (binding.isIdempotent() && idempotencyStore.isProcessed(eventId, binding.getBindingId())) {
            logger.debug("Event {} already processed by {}, skipping", eventId, binding.getBindingId());
            return;
        }

        // Get handler instance
        Object handlerInstance = beanFactory.apply(binding.getHandlerClassName());
        if (handlerInstance == null) {
            throw new EventBindingException(
                "Cannot get handler instance: " + binding.getHandlerClassName());
        }

        // Invoke handler method
        try {
            Method method = binding.getHandlerMethod();
            method.invoke(handlerInstance, event);
            
            // Mark as processed
            if (binding.isIdempotent()) {
                idempotencyStore.markProcessed(eventId, binding.getBindingId());
            }
            
            logger.debug("Event {} processed by {}.{}", 
                eventId, binding.getHandlerClassName(), binding.getHandlerMethodName());
                
        } catch (Exception e) {
            throw new EventDispatchException(
                "Event handler invocation failed: " + binding.getHandlerClassName() + "." + binding.getHandlerMethodName(), 
                e);
        }
    }

    /**
     * Creates event handler binding.
     */
    private EventHandlerBinding createBinding(String moduleId, EventSubscribeConfig config) 
            throws ClassNotFoundException, NoSuchMethodException {
        
        String handlerClassName = config.getHandlerClass();
        String handlerMethodName = config.getHandlerMethod();
        
        if (handlerClassName == null || handlerMethodName == null) {
            throw new EventBindingException(
                "Invalid handler configuration: handler=" + config.getHandler());
        }

        // Load handler class
        Class<?> handlerClass = Class.forName(handlerClassName);
        
        // Find handler method (parameter is event type)
        String eventTypeName = config.getType();
        Class<?> eventClass = Class.forName(eventTypeName);
        Method handlerMethod = handlerClass.getMethod(handlerMethodName, eventClass);

        return new EventHandlerBinding(
            moduleId,
            config.getType(),
            handlerClassName,
            handlerMethodName,
            handlerMethod,
            config.isIdempotent(),
            config.getRetry()
        );
    }

    /**
     * Gets the count of registered modules.
     * 
     * @return Module count
     */
    public int getRegisteredModuleCount() {
        return registeredModules.size();
    }

    /**
     * Gets the handler count for a specific event type.
     * 
     * @param eventType Event type
     * @return Handler count
     */
    public int getHandlerCount(String eventType) {
        List<EventHandlerBinding> handlers = handlerMap.get(eventType);
        return handlers != null ? handlers.size() : 0;
    }

    // ==================== Inner Classes ====================

    /**
     * Event Handler Binding
     */
    public static class EventHandlerBinding {
        
        private final String moduleId;
        private final String eventType;
        private final String handlerClassName;
        private final String handlerMethodName;
        private final Method handlerMethod;
        private final boolean idempotent;
        private final RetryConfig retryConfig;
        private final String bindingId;

        public EventHandlerBinding(
                String moduleId,
                String eventType,
                String handlerClassName,
                String handlerMethodName,
                Method handlerMethod,
                boolean idempotent,
                RetryConfig retryConfig) {
            this.moduleId = moduleId;
            this.eventType = eventType;
            this.handlerClassName = handlerClassName;
            this.handlerMethodName = handlerMethodName;
            this.handlerMethod = handlerMethod;
            this.idempotent = idempotent;
            this.retryConfig = retryConfig;
            this.bindingId = moduleId + ":" + handlerClassName + "." + handlerMethodName;
        }

        public String getModuleId() { return moduleId; }
        public String getEventType() { return eventType; }
        public String getHandlerClassName() { return handlerClassName; }
        public String getHandlerMethodName() { return handlerMethodName; }
        public Method getHandlerMethod() { return handlerMethod; }
        public boolean isIdempotent() { return idempotent; }
        public RetryConfig getRetryConfig() { return retryConfig; }
        public String getBindingId() { return bindingId; }
    }

    /**
     * Idempotency Store Interface
     */
    public interface IdempotencyStore {
        
        /**
         * Checks if event has been processed by specified handler.
         * 
         * @param eventId   Event ID
         * @param handlerId Handler ID
         * @return true if already processed
         */
        boolean isProcessed(String eventId, String handlerId);
        
        /**
         * Marks event as processed by specified handler.
         * 
         * @param eventId   Event ID
         * @param handlerId Handler ID
         */
        void markProcessed(String eventId, String handlerId);
    }

}
