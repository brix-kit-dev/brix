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
 * Internal API Marker Annotation
 * 
 * <p>Methods, classes, or interfaces marked as Internal API are only for runtime
 * framework internal use, <b>plugins/modules should not call directly</b>. Internal APIs
 * may have incompatible changes in minor version upgrades without following semantic
 * versioning compatibility constraints.</p>
 * 
 * <h3>Use Cases</h3>
 * <ul>
 *   <li><b>Infrastructure Exposure</b>: such as {@code DataSource}, {@code Connection} and other low-level objects</li>
 *   <li><b>Framework Extension Points</b>: for Host adapter implementations, not for business plugins</li>
 *   <li><b>Performance Hot Paths</b>: low-level APIs for high-performance scenarios</li>
 * </ul>
 * 
 * <h3>ArchUnit Rules</h3>
 * <p>Detected via ArchUnit rules in architecture-guard module:</p>
 * <pre>{@code
 * // Prohibit plugins from directly calling @InternalApi marked methods
 * noClasses().that().resideInAPackage("..module..")
 *     .should().callMethodsAnnotatedWith(InternalApi.class)
 *     .because("Internal APIs are for framework use only, plugins should use public APIs");
 * }</pre>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * public interface DatabaseCapability {
 *     
 *     // Recommended: plugins use this method to execute SQL
 *     <T> T executeNative(String sql, Class<T> resultType, Object... params);
 *     
 *     // Internal API: only for adapter layer use
 *     @InternalApi("Exposes infrastructure type, plugins should use executeNative()")
 *     DataSource getDataSource();
 * }
 * }</pre>
 * 
 * <h3>Difference from @Deprecated</h3>
 * <ul>
 *   <li>{@code @Deprecated}: API will be removed, please migrate to alternative</li>
 *   <li>{@code @InternalApi}: API continues to exist, but not for plugin developers</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.2.0
 * @see io.runtime.sdk.capability.DatabaseCapability#getDataSource()
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface InternalApi {
    
    /**
     * Explanation of why this API is internal and recommended alternatives
     * 
     * @return internal API description
     */
    String value() default "";
    
    /**
     * Recommended public API method name
     * 
     * <p>If an alternative public API exists, specify the method name here for IDE hints.</p>
     * 
     * @return recommended alternative method name, empty string means no alternative
     */
    String instead() default "";
}
