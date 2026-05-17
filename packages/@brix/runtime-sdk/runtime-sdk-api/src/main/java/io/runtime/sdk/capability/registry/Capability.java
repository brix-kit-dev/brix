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
package io.runtime.sdk.capability.registry;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.runtime.sdk.annotation.Since;

/**
 * Capability Annotation
 * 
 * <p>Used to mark a Bean as an implementation of a Capability, supporting auto-discovery and registration.
 * Works with Spring Boot AutoConfiguration for declarative capability assembly.</p>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Capability(
 *     type = EventBusCapability.class,
 *     name = "kafka-eventbus",
 *     description = "Kafka-based EventBus implementation",
 *     level = CapabilityLevel.CORE,
 *     priority = 100
 * )
 * @Component
 * public class KafkaEventBus implements EventBusCapability {
 *     // Implementation...
 * }
 * }</pre>
 * 
 * <h3>Auto-Discovery Mechanism</h3>
 * <p>During Host startup, all Beans annotated with @Capability are scanned
 * and automatically registered to CapabilityRegistry.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see CapabilityRegistry
 * @see CapabilityLevel
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Since("3.0.0")
public @interface Capability {

    /**
     * Capability interface type
     * 
     * <p>Specifies the capability interface this implementation corresponds to.
     * If not specified, will be auto-inferred (first implemented *Capability interface).</p>
     * 
     * @return capability interface type
     */
    Class<?> type() default Void.class;

    /**
     * Capability name
     * 
     * <p>Friendly name used for logging, monitoring, and configuration reference.</p>
     * 
     * @return capability name
     */
    String name() default "";

    /**
     * Capability description
     * 
     * @return capability description
     */
    String description() default "";

    /**
     * Capability level
     * 
     * @return capability level
     * @see CapabilityLevel
     */
    CapabilityLevel level() default CapabilityLevel.STANDARD;

    /**
     * Capability priority
     * 
     * <p>When multiple implementations exist for the same type, higher priority wins.
     * Higher numbers mean higher priority.</p>
     * 
     * @return priority, default 0
     */
    int priority() default 0;

    /**
     * Whether capability is required
     * 
     * <p>If true, Host startup will verify this capability must exist.</p>
     * 
     * @return whether required
     */
    boolean required() default false;

    /**
     * Capability aliases
     * 
     * <p>Capability instance can be retrieved via aliases.</p>
     * 
     * @return alias array
     */
    String[] aliases() default {};
}
