/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.starter.resilience;

import java.lang.annotation.*;

/**
 * Circuit Breaker Protection Annotation
 * 
 * <p>v2.1 Phase 4 circuit breaker and degradation implementation</p>
 * 
 * <p>Feature Description:</p>
 * <p>Marks methods that need circuit breaker protection. When method call failure rate
 * exceeds threshold, circuit breaker triggers and directly returns degraded result.</p>
 * 
 * <p>Usage Example:</p>
 * <pre>{@code
 * @CircuitProtected(
 *     name = "fileStorage",
 *     fallbackMethod = "downloadFallback"
 * )
 * public InputStream download(Long fileId) {
 *     return storageAdapter.download(fileId);
 * }
 * 
 * public InputStream downloadFallback(Long fileId, Throwable t) {
 *     log.warn("File download degraded: fileId={}, error={}", fileId, t.getMessage());
 *     throw new ServiceUnavailableException("File service temporarily unavailable, please retry later");
 * }
 * }</pre>
 * 
 * <p>Circuit Breaker Strategy:</p>
 * <ul>
 *   <li><b>Failure Rate Threshold</b>: Triggers circuit breaker when failure ratio in consecutive requests exceeds threshold</li>
 *   <li><b>Slow Call Threshold</b>: Response time exceeding threshold counts as slow call, high slow call ratio triggers circuit breaker</li>
 *   <li><b>Half-Open State</b>: After circuit breaker, waits for a period then enters half-open state, allowing partial request attempts</li>
 *   <li><b>Recovery</b>: Recovers to normal when success rate in half-open state meets criteria</li>
 * </ul>
 * 
 * <p>⚠️ Important Notes:</p>
 * <ul>
 *   <li>fallbackMethod must be in the same class as the original method</li>
 *   <li>fallbackMethod parameters must match original method, optionally with Throwable as last parameter</li>
 *   <li>Different businesses should use different names for independent circuit breaking</li>
 * </ul>
 *
 * @author Brix Platform Authors Platform Team
 * @since v2.1
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CircuitProtected {
    
    /**
     * Circuit breaker name
     * 
     * <p>Used to identify circuit breaker instance, methods with same name share circuit state</p>
     * <p>Recommend naming by service/function, e.g.: fileStorage, caseService, notification</p>
     * 
     * @return Circuit breaker name
     */
    String name();
    
    /**
     * Fallback method name
     * 
     * <p>Degradation method called when circuit breaker triggers or exception occurs</p>
     * <p>Method signature requirement: same parameters as original method, optionally with Throwable as last parameter</p>
     * 
     * @return Fallback method name
     */
    String fallbackMethod() default "";
    
    /**
     * Exception types to record as failure
     * 
     * <p>By default all exceptions are considered failures</p>
     * 
     * @return Array of exception types
     */
    Class<? extends Throwable>[] recordFailureFor() default {Exception.class};
    
    /**
     * Exception types not to record as failure
     * 
     * <p>These exceptions are not counted in failure rate statistics (e.g., business exceptions like IllegalArgumentException)</p>
     * 
     * @return Array of exception types
     */
    Class<? extends Throwable>[] ignoreExceptions() default {};
}
