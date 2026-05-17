/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.fallback;

import io.runtime.sdk.capability.HttpCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

import java.net.http.HttpClient;

/**
 * Fallback HTTP Capability Implementation (JDK HttpClient-based).
 * 
 * <p>Uses Java standard library {@link java.net.http.HttpClient} to provide HTTP communication capability.
 * This implementation serves as a fallback, automatically enabled when no other HTTP adapters
 * (such as OkHttp or Apache HttpClient based) are available.</p>
 * 
 * <h3>Architecture Overview</h3>
 * <p>
 * According to the v3.0 Runtime Shell Architecture Blueprint:
 * <ul>
 *   <li>Host layer (enterprise-host) only performs assembly, contains no implementation code</li>
 *   <li>All capability implementations must be in infra-adapters or platform-commons</li>
 *   <li>This class was migrated from host-shell-standalone, complying with the thin Host principle</li>
 * </ul>
 * </p>
 * 
 * <h3>Inheritance Note</h3>
 * <p>This class extends {@link AbstractJdkHttpCapability}, reusing all its HTTP method implementations.
 * This eliminates code duplication with {@code JdkHttpCapability}, following the DRY principle.</p>
 * 
 * <h3>Technical Features</h3>
 * <ul>
 *   <li><b>Zero External Dependencies</b> — Uses only JDK 11+ standard library {@link java.net.http.HttpClient}</li>
 *   <li><b>HTTP/2 Support</b> — Automatic protocol version negotiation (HTTP/1.1 or HTTP/2)</li>
 *   <li><b>Configurable Timeouts</b> — Supports connection timeout and request timeout configuration</li>
 *   <li><b>Thread-Safe</b> — {@link HttpClient} instance is thread-safe and can be safely shared</li>
 * </ul>
 * 
 * <h3>Timeout Configuration</h3>
 * <ul>
 *   <li><b>Connection Timeout (connectTimeout)</b>: Maximum wait time for establishing TCP connection, default 10 seconds</li>
 *   <li><b>Request Timeout (requestTimeout)</b>: Maximum wait time for a single HTTP request, default 30 seconds</li>
 * </ul>
 * 
 * <h3>Usage Examples</h3>
 * <pre>{@code
 * // Using default timeouts (connection 10 seconds, request 30 seconds)
 * HttpCapability http = new FallbackHttpCapability();
 * 
 * // Custom timeouts
 * HttpCapability http = new FallbackHttpCapability(5, 60);
 * 
 * // GET request
 * HttpResult result = http.get("https://api.example.com/users", 
 *     Map.of("Authorization", "Bearer token"));
 * 
 * // POST request
 * HttpResult result = http.post("https://api.example.com/users", 
 *     "{\"name\":\"John\"}", 
 *     Map.of("Content-Type", "application/json"));
 * }</pre>
 * 
 * @author Brix Team
 * @version 3.2.0
 * @since 3.0.0
 * @see HttpCapability
 * @see AbstractJdkHttpCapability
 * @see HttpClient
 */
@Capability(
    type = HttpCapability.class,
    name = "fallback-http",
    description = "Fallback HTTP capability implementation, based on JDK HttpClient, automatically enabled when no other HTTP adapters are available",
    level = CapabilityLevel.FALLBACK,
    aliases = {"fallbackHttp", "defaultHttp"}
)
public class FallbackHttpCapability extends AbstractJdkHttpCapability {

    /**
     * Creates an instance with specified timeouts.
     * 
     * <p>Configuration details:
     * <ul>
     *   <li>Connection timeout: Maximum wait time for establishing TCP connection</li>
     *   <li>Request timeout: Maximum wait time for a single HTTP request</li>
     * </ul>
     * </p>
     * 
     * @param connectTimeoutSeconds Connection timeout in seconds (maximum wait time for TCP connection)
     * @param requestTimeoutSeconds Request timeout in seconds (maximum wait time for HTTP request)
     */
    public FallbackHttpCapability(int connectTimeoutSeconds, int requestTimeoutSeconds) {
        super(connectTimeoutSeconds, requestTimeoutSeconds);
    }

    /**
     * Creates an instance with default timeouts.
     * 
     * <p>Uses recommended default timeout values: 10 seconds for connection, 30 seconds for request.
     * These values are suitable for most business scenarios and prevent resource occupation
     * due to prolonged waiting.</p>
     */
    public FallbackHttpCapability() {
        super();
    }
}
