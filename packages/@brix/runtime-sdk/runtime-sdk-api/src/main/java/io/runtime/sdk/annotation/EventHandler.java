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
 * Legacy event handler annotation of the module model; nothing reads it any more.
 *
 * @deprecated A plugin declares each subscription in {@code META-INF/brix/plugin-manifest.yaml} under
 *     {@code events.subscribes} (event type, schema range, handler id, retry and idempotency policies) and binds the
 *     handler in {@link io.runtime.sdk.plugin.BrixPlugin#configure} with
 *     {@code bootstrap.bindEventHandlerFactory(handlerId, context -> new MyHandler(...))}, where the handler implements
 *     {@link io.runtime.sdk.plugin.EventHandler}. The runtime then delivers each event once per handler through the
 *     subscriber's inbox (root BRX-RS-MESSAGE-025, MESSAGE-029; Kafka design §14 K23).
 * @author Runtime SDK Team
 * @since 3.0.0
 */
@Deprecated(since = "3.3.0", forRemoval = true)
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
