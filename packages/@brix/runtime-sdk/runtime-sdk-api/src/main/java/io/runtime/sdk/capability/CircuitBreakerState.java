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
 * Circuit Breaker State Enumeration
 * 
 * <p>Defines three states of a circuit breaker, implementing fault isolation based on state machine pattern.</p>
 * 
 * <h3>State Transition Rules</h3>
 * <pre>{@code
 *                 Failure rate exceeds threshold
 * CLOSED ────────────────────> OPEN
 *   ^                            |
 *   |                            | Wait time elapsed
 *   |     Success rate recovered  v
 *   └──────────────────── HALF_OPEN
 *                            |
 *                            | Failure
 *                            v
 *                          OPEN
 * }</pre>
 * 
 * <h3>State Description</h3>
 * <table border="1">
 *   <tr><th>State</th><th>Description</th><th>Request Handling</th></tr>
 *   <tr><td>CLOSED</td><td>Normal state</td><td>All requests pass normally</td></tr>
 *   <tr><td>OPEN</td><td>Circuit broken state</td><td>Requests directly rejected</td></tr>
 *   <tr><td>HALF_OPEN</td><td>Half-open state</td><td>Some requests allowed for recovery probing</td></tr>
 * </table>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ResilienceCapability#getCircuitBreakerState(String)
 */
public enum CircuitBreakerState {

    /**
     * Closed state (normal)
     * 
     * <p>Circuit breaker is closed, all requests pass normally.
     * Transitions to OPEN state when failure rate exceeds threshold.</p>
     */
    CLOSED("Closed (Normal)"),

    /**
     * Open state (circuit broken)
     * 
     * <p>Circuit breaker is open, all requests are directly rejected.
     * Transitions to HALF_OPEN state after configured wait time.</p>
     */
    OPEN("Open (Circuit Broken)"),

    /**
     * Half-open state (recovering)
     * 
     * <p>Circuit breaker is half-open, allowing a configured number of requests for probing.
     * Transitions to CLOSED if probe success rate is high; otherwise transitions back to OPEN.</p>
     */
    HALF_OPEN("Half-Open (Recovering)"),

    /**
     * Disabled state
     * 
     * <p>Circuit breaker is disabled, no circuit breaking processing.</p>
     */
    DISABLED("Disabled"),

    /**
     * Forced open state
     * 
     * <p>Manually forced open for maintenance or testing.</p>
     */
    FORCED_OPEN("Forced Open");

    /**
     * State description
     */
    private final String description;

    CircuitBreakerState(String description) {
        this.description = description;
    }

    /**
     * Gets the state description
     * 
     * @return the state description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if requests are allowed to pass
     * 
     * @return true if requests are allowed to pass
     */
    public boolean isCallPermitted() {
        return this == CLOSED || this == HALF_OPEN || this == DISABLED;
    }
}
