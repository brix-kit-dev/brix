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
package io.runtime.sdk.capability;

/**
 * Circuit Breaker Open Exception
 * 
 * <p>Thrown when the circuit breaker is in an open state, indicating the request was rejected.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ResilienceCapability#executeWithCircuitBreaker(String, java.util.function.Supplier)
 */
public class CircuitBreakerOpenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Circuit breaker name
     */
    private final String circuitBreakerName;

    /**
     * Creates a circuit breaker open exception
     * 
     * @param circuitBreakerName the circuit breaker name
     */
    public CircuitBreakerOpenException(String circuitBreakerName) {
        super("Circuit breaker '" + circuitBreakerName + "' is open");
        this.circuitBreakerName = circuitBreakerName;
    }

    /**
     * Creates a circuit breaker open exception
     * 
     * @param circuitBreakerName the circuit breaker name
     * @param message            the exception message
     */
    public CircuitBreakerOpenException(String circuitBreakerName, String message) {
        super(message);
        this.circuitBreakerName = circuitBreakerName;
    }

    /**
     * Gets the circuit breaker name
     * 
     * @return the circuit breaker name
     */
    public String getCircuitBreakerName() {
        return circuitBreakerName;
    }
}
