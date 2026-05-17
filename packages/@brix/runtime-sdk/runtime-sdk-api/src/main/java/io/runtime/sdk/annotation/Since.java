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
 * API Version Marker Annotation
 *
 * <p>Indicates the version in which a type or member was introduced.
 * This annotation provides <b>machine-readable</b> version metadata
 * that tooling (OpenAPI diff, ArchUnit rules, changelog generators)
 * can consume, complementing the Javadoc {@code @since} tag.</p>
 *
 * <h3>Versioning Scheme</h3>
 * <p>Values follow <a href="https://semver.org">Semantic Versioning</a>
 * in the format {@code MAJOR.MINOR.PATCH} (e.g. {@code "3.0.0"}).
 * Only the first two components ({@code MAJOR.MINOR}) carry compatibility
 * guarantees — patch versions never introduce new API surface.</p>
 *
 * <h3>Compatibility Rules</h3>
 * <ul>
 *   <li><b>MINOR bump</b>: New types/methods may be added (backward compatible)</li>
 *   <li><b>MAJOR bump</b>: Existing APIs may change or be removed</li>
 *   <li>A method annotated {@code @Since("3.1.0")} is guaranteed stable
 *       within the 3.x line</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Since("3.0.0")
 * public interface EventBusCapability {
 *
 *     @Since("3.0.0")
 *     void publish(DomainEvent event);
 *
 *     @Since("3.1.0")
 *     void publishIntegration(IntegrationEvent event);
 * }
 * }</pre>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({
    ElementType.TYPE,
    ElementType.METHOD,
    ElementType.FIELD,
    ElementType.CONSTRUCTOR,
    ElementType.ANNOTATION_TYPE,
})
public @interface Since {

    /**
     * The version string in which this API element was introduced.
     *
     * @return semantic version, e.g. {@code "3.0.0"}
     */
    String value();
}
