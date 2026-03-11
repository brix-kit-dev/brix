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
package io.infra.adapter.simple;

import io.infra.adapter.fallback.AbstractJdkHttpCapability;
import io.runtime.sdk.capability.HttpCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

import java.net.http.HttpClient;

/**
 * JDK HttpClient-based HTTP Capability Implementation
 * 
 * <p>Provides HTTP communication capability using the Java standard library {@link java.net.http.HttpClient}.
 * Suitable for Simple Adapter scenarios (development, testing, embedded deployment).</p>
 * 
 * <h3>Architecture Note</h3>
 * <p>
 * This class belongs to the {@code infra-adapter-simple} module (Layer 2.5: Adapter Layer),
 * implementing the {@link HttpCapability} contract to provide HTTP communication capability for plugins.
 * </p>
 * 
 * <h3>Inheritance Note</h3>
 * <p>This class extends {@link AbstractJdkHttpCapability}, reusing all its HTTP method implementations.
 * This eliminates code duplication with {@code FallbackHttpCapability}, following the DRY principle.</p>
 * 
 * <h3>Features</h3>
 * <ul>
 *   <li><b>Zero External Dependencies</b> — Uses only JDK 11+ standard library {@link java.net.http.HttpClient}</li>
 *   <li><b>Configurable Timeouts</b> — Supports connection timeout and request timeout configuration</li>
 *   <li><b>HTTP/2 Support</b> — Automatic protocol version negotiation</li>
 *   <li><b>Simple Deployment</b> — Suitable for development, testing, and embedded deployment scenarios</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * HttpCapability http = new JdkHttpCapability();
 * HttpResult result = http.get("https://api.example.com/users", 
 *     Map.of("Authorization", "Bearer token"));
 * }</pre>
 * 
 * @author Brix Team
 * @since 3.1.0
 * @see HttpCapability
 * @see AbstractJdkHttpCapability
 * @see HttpClient
 */
@Capability(
    type = HttpCapability.class,
    name = "jdk-http",
    description = "JDK HttpClient-based HTTP capability implementation, zero external dependencies, suitable for development and simplified deployment scenarios",
    level = CapabilityLevel.STANDARD,
    aliases = {"jdkHttp", "simpleHttp"}
)
public class JdkHttpCapability extends AbstractJdkHttpCapability {

    /**
     * Creates an instance with specified timeouts
     * 
     * @param connectTimeoutSeconds Connection timeout in seconds (maximum wait time to establish TCP connection)
     * @param requestTimeoutSeconds Request timeout in seconds (maximum wait time for a single HTTP request)
     */
    public JdkHttpCapability(int connectTimeoutSeconds, int requestTimeoutSeconds) {
        super(connectTimeoutSeconds, requestTimeoutSeconds);
    }

    /**
     * Creates an instance with default timeouts (connection 10s, request 30s)
     */
    public JdkHttpCapability() {
        super();
    }
}
