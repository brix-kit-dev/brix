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
 * Module Identification Annotation
 * 
 * <p>Marks a class as a Runtime Shell module. Classes annotated with this annotation
 * will be recognized and managed by the Runtime Shell.</p>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Module(
 *     id = "brix-app-booking",
 *     name = "Booking Management",
 *     version = "3.0.0",
 *     description = "Provides booking creation, query, and cancellation features"
 * )
 * public class BookingModule extends AbstractModule {
 *     // ...
 * }
 * }</pre>
 * 
 * <h3>Annotation Attributes and Manifest Relationship</h3>
 * <p>Annotation attributes are merged with configurations in module-manifest.yaml,
 * with manifest taking higher precedence. It's recommended to define complete
 * configurations in the manifest and use annotations only for code-level identification.</p>
 * 
 * <h3>Scanning Mechanism</h3>
 * <p>Runtime Shell scans all classes with the @Module annotation at startup,
 * and performs initialization and lifecycle management based on manifest configuration.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see io.runtime.sdk.support.AbstractModule
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Module {

    /**
     * Unique module identifier.
     * 
     * <p>Naming convention: {org}-{type}-{name}, e.g., brix-app-booking</p>
     * 
     * @return module ID
     */
    String id();

    /**
     * Module display name.
     * 
     * @return module name
     */
    String name();

    /**
     * Module version number.
     * 
     * <p>Should follow Semantic Versioning (SemVer), e.g., 3.0.0</p>
     * 
     * @return version string, defaults to empty (read from manifest)
     */
    String version() default "";

    /**
     * Module description.
     * 
     * @return module description, optional
     */
    String description() default "";

    /**
     * Startup order.
     * 
     * <p>Lower numbers start first, default is 100</p>
     * 
     * @return startup order
     */
    int startupOrder() default 100;

    /**
     * List of dependent module IDs.
     * 
     * <p>Declared dependencies will start before this module</p>
     * 
     * @return array of dependent module IDs
     */
    String[] dependsOn() default {};
}
