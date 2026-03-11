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
package io.runtime.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Event Handler Annotation
 * 
 * <p>Used to identify a method as an event handler. Methods annotated with this annotation will be registered as event listeners by the Runtime Shell.</p>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Module(id = "booking-module", name = "Booking Module")
 * public class BookingModule extends AbstractModule {
 *     
 *     // Handle user created event
 *     @EventHandler
 *     public void onUserCreated(UserCreatedEvent event) {
 *         // Create default booking configuration for new users...
 *     }
 *     
 *     // Handle order completed event, specifying event type
 *     @EventHandler(eventType = "com.example.OrderCompletedEvent")
 *     public void handleOrderCompleted(OrderCompletedEvent event) {
 *         // Handle booking update after order completion...
 *     }
 *     
 *     // Asynchronously handle bulk events
 *     @EventHandler(async = true)
 *     public void onBatchDataSync(DataSyncEvent event) {
 *         // Asynchronously process data sync...
 *     }
 * }
 * }</pre>
 * 
 * <h3>Method Signature Requirements</h3>
 * <ul>
 *   <li>Must be a public method</li>
 *   <li>Must have exactly one parameter, with the parameter type being the event class</li>
 *   <li>Return type should be void (non-void return values are ignored)</li>
 * </ul>
 * 
 * <h3>Relationship with Manifest</h3>
 * <p>Event handlers declared via annotations must be whitelisted in the module-manifest.yaml subscribes section.
 * Only event types in the whitelist will be routed to the corresponding handlers.</p>
 * 
 * <pre>{@code
 * # module-manifest.yaml
 * events:
 *   subscribes:
 *     - type: "com.example.UserCreatedEvent"
 *       handler: "com.example.BookingModule.onUserCreated"
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventHandler {

    /**
     * Event type
     * 
     * <p>Inferred from the method parameter type by default. If specified, the specified type name is used.</p>
     * 
     * @return the fully qualified event type name
     */
    String eventType() default "";

    /**
     * Whether to process asynchronously
     * 
     * <p>If true, the event will be processed asynchronously in a separate thread, not blocking the event publisher</p>
     * 
     * @return whether to process asynchronously, default false
     */
    boolean async() default false;

    /**
     * Processing order
     * 
     * <p>When multiple handlers subscribe to the same event, they execute in ascending order by order value</p>
     * 
     * @return the processing order, default 0
     */
    int order() default 0;

    /**
     * Condition expression
     * 
     * <p>SpEL expression, the handler is only executed when the expression evaluates to true</p>
     * <pre>{@code
     * @EventHandler(condition = "#event.amount > 1000")
     * public void onLargeOrder(OrderCreatedEvent event) { }
     * }</pre>
     * 
     * @return the condition expression, default empty (unconditional)
     */
    String condition() default "";
}
