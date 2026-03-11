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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.HttpCapability;
import io.runtime.sdk.capability.HttpCapabilityException;

/**
 * Abstract base class for HTTP capability based on JDK HttpClient.
 * 
 * <p>Provides common HTTP communication implementation based on the Java standard library
 * {@link java.net.http.HttpClient}. Subclasses can quickly implement the {@link HttpCapability}
 * interface by extending this class.</p>
 * 
 * <h3>Architecture Overview</h3>
 * <p>
 * This class belongs to the {@code infra-adapter-fallback} module (Layer 2.5: Adapter Layer),
 * serving as the common base class for HTTP capability implementations. The following classes
 * extend this class:
 * <ul>
 *   <li>{@code FallbackHttpCapability} — Fallback implementation in infra-adapter-fallback module</li>
 *   <li>{@code JdkHttpCapability} — Simple implementation in infra-adapter-simple module</li>
 * </ul>
 * </p>
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
 * // Inheritance approach
 * @Capability(type = HttpCapability.class, name = "my-http")
 * public class MyHttpCapability extends AbstractJdkHttpCapability {
 *     public MyHttpCapability() {
 *         super(10, 30);
 *     }
 * }
 * 
 * // Direct usage
 * HttpCapability http = new FallbackHttpCapability();
 * HttpResult result = http.get("https://api.example.com/users", 
 *     Map.of("Authorization", "Bearer token"));
 * }</pre>
 * 
 * @author Brix Team
 * @version 3.2.0
 * @since 3.2.0
 * @see HttpCapability
 * @see HttpClient
 * @see FallbackHttpCapability
 */
public abstract class AbstractJdkHttpCapability implements HttpCapability {

    /**
     * Logger instance.
     * 
     * <p>Uses the subclass name as the logger name to distinguish log output
     * from different implementations.</p>
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * JDK HttpClient instance.
     * 
     * <p>HttpClient is thread-safe and can be safely shared across multiple threads.
     * This instance is created during construction and has the same lifecycle as the
     * Capability instance.</p>
     */
    protected final HttpClient httpClient;

    /**
     * Request timeout duration.
     * 
     * <p>Used to set the maximum wait time for a single HTTP request.
     * Note: This differs from connection timeout; request timeout includes
     * data transfer time after the connection is established.</p>
     */
    protected final Duration requestTimeout;

    /**
     * Creates an instance with specified configuration.
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
    protected AbstractJdkHttpCapability(int connectTimeoutSeconds, int requestTimeoutSeconds) {
        // Create HttpClient instance with connection timeout configuration
        // HttpClient uses builder pattern for fluent configuration
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        
        log.info("[{}] Initialization complete: connectTimeout={}s, requestTimeout={}s",
                getClass().getSimpleName(), connectTimeoutSeconds, requestTimeoutSeconds);
    }

    /**
     * Creates an instance with default timeouts.
     * 
     * <p>Uses recommended default timeout values: 10 seconds for connection, 30 seconds for request.
     * These values are suitable for most business scenarios and prevent resource occupation
     * due to prolonged waiting.</p>
     */
    protected AbstractJdkHttpCapability() {
        this(10, 30);
    }

    /**
     * Executes an HTTP GET request.
     * 
     * <p>Sends an HTTP GET request to the specified URL and returns the response.
     * GET requests are used to retrieve resources and are idempotent operations.</p>
     * 
     * @param url Request URL (must be a complete HTTP/HTTPS URL)
     * @param headers Request headers (can be null)
     * @return HTTP response result containing status code, response body, and headers
     * @throws HttpCapabilityException When the request fails
     */
    @Override
    public HttpResult get(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .GET();
        applyHeaders(builder, headers);
        return execute(builder.build());
    }

    /**
     * Executes an HTTP POST request.
     * 
     * <p>Sends an HTTP POST request to the specified URL with a request body.
     * POST requests are used to create resources and are not idempotent operations.</p>
     * 
     * @param url Request URL
     * @param body Request body (can be null, will send empty body)
     * @param headers Request headers (can be null, recommend setting Content-Type at minimum)
     * @return HTTP response result
     * @throws HttpCapabilityException When the request fails
     */
    @Override
    public HttpResult post(String url, String body, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .POST(bodyPublisher(body));
        applyHeaders(builder, headers);
        return execute(builder.build());
    }

    /**
     * Executes an HTTP PUT request.
     * 
     * <p>Sends an HTTP PUT request to the specified URL with a request body.
     * PUT requests are used to update resources and are idempotent operations.</p>
     * 
     * @param url Request URL
     * @param body Request body (can be null)
     * @param headers Request headers
     * @return HTTP response result
     * @throws HttpCapabilityException When the request fails
     */
    @Override
    public HttpResult put(String url, String body, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .PUT(bodyPublisher(body));
        applyHeaders(builder, headers);
        return execute(builder.build());
    }

    /**
     * Executes an HTTP DELETE request.
     * 
     * <p>Sends an HTTP DELETE request to the specified URL.
     * DELETE requests are used to remove resources and are idempotent operations.</p>
     * 
     * @param url Request URL
     * @param headers Request headers
     * @return HTTP response result
     * @throws HttpCapabilityException When the request fails
     */
    @Override
    public HttpResult delete(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .DELETE();
        applyHeaders(builder, headers);
        return execute(builder.build());
    }

    // ==================== Internal Methods ====================

    /**
     * Executes an HTTP request and returns the result.
     * 
     * <p>Unified request execution method that handles:
     * <ul>
     *   <li>Sending the request and waiting for response</li>
     *   <li>Handling interrupt exceptions (restoring interrupt status)</li>
     *   <li>Converting exceptions to {@link HttpCapabilityException}</li>
     *   <li>Logging requests (DEBUG level)</li>
     * </ul>
     * </p>
     * 
     * @param request The constructed HTTP request
     * @return HTTP response result
     * @throws HttpCapabilityException When the request fails
     */
    protected HttpResult execute(HttpRequest request) {
        try {
            // Log request at DEBUG level to avoid excessive logging in production
            log.debug("[HTTP] {} {}", request.method(), request.uri());
            
            // Send synchronous request using String as response body handler
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Extract response headers (converted to immutable Map)
            Map<String, List<String>> responseHeaders = response.headers().map();
            
            // Log response
            log.debug("[HTTP] {} {} -> {}", request.method(), request.uri(), response.statusCode());

            // Return wrapped result
            return new HttpResult(response.statusCode(), response.body(), responseHeaders);
            
        } catch (InterruptedException e) {
            // CRITICAL: Handle interrupt exception - must restore interrupt status
            // Reference: Java Concurrency in Practice, Chapter 7
            Thread.currentThread().interrupt();
            throw new HttpCapabilityException("HTTP request interrupted: " + request.uri(), e);
            
        } catch (Exception e) {
            // Wrap all other exceptions as HttpCapabilityException
            throw new HttpCapabilityException(
                    "HTTP request failed: " + request.method() + " " + request.uri() + " - " + e.getMessage(), e);
        }
    }

    /**
     * Applies request headers to HttpRequest.Builder.
     * 
     * <p>Adds Map-formatted request headers one by one to the request builder.
     * If headers is null, no operation is performed.</p>
     * 
     * @param builder Request builder
     * @param headers Request headers Map (can be null)
     */
    protected void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers != null) {
            headers.forEach(builder::header);
        }
    }

    /**
     * Creates a request body publisher.
     * 
     * <p>Returns the appropriate BodyPublisher based on whether body is null:
     * <ul>
     *   <li>body is not null: returns string publisher</li>
     *   <li>body is null: returns empty publisher</li>
     * </ul>
     * </p>
     * 
     * @param body Request body string (can be null)
     * @return Corresponding BodyPublisher
     */
    protected HttpRequest.BodyPublisher bodyPublisher(String body) {
        return body != null
                ? HttpRequest.BodyPublishers.ofString(body)
                : HttpRequest.BodyPublishers.noBody();
    }
}
