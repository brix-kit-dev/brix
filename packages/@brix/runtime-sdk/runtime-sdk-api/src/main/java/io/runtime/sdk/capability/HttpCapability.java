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

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * HTTP Communication Capability Contract
 * 
 * <p>Provides unified abstraction for cross-service HTTP calls. All module HTTP communications must go through this interface.
 * This ensures uniform error handling, distributed tracing, timeout control, and observability.</p>
 * 
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li>HTTP GET/POST/PUT/DELETE requests</li>
 *   <li>Unified request header and response handling</li>
 *   <li>Connection timeout and read timeout management</li>
 *   <li>Distributed tracing context propagation (implemented by adapter)</li>
 * </ul>
 * 
 * <h3>Architecture Constraint (Red Line R3)</h3>
 * <p>Business modules (Plugin Layer) are prohibited from directly using any HTTP client library
 * (RestTemplate, WebClient, OpenFeign, OkHttp, java.net.http.HttpClient).
 * HTTP calls must be made through this capability contract.</p>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Inject
 * private HttpCapability httpClient;
 * 
 * public void callExternalApi() {
 *     HttpResult result = httpClient.get(
 *         "https://api.example.com/data",
 *         Map.of("Authorization", "Bearer token123")
 *     );
 *     
 *     if (result.isSuccess()) {
 *         String data = result.body();
 *         // Process response...
 *     }
 * }
 * }</pre>
 * 
 * <h3>Implementation Notes</h3>
 * <ul>
 *   <li>Simple Adapter: Uses JDK HttpClient (dev/test environment)</li>
 *   <li>Full Product Host: Can integrate Spring WebClient + Resilience4j (production environment)</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.1.0
 * @see HttpResult
 */
public interface HttpCapability {

    /**
     * Sends an HTTP GET request
     * 
     * @param url     the request URL, cannot be empty
     * @param headers the request headers, can be an empty Map
     * @return the HTTP response result
     * @throws HttpCapabilityException if the request fails to send (network error, timeout, etc.)
     */
    HttpResult get(String url, Map<String, String> headers);

    /**
     * Sends an HTTP POST request
     * 
     * @param url     the request URL, cannot be empty
     * @param body    the request body, can be null
     * @param headers the request headers, can be an empty Map
     * @return the HTTP response result
     * @throws HttpCapabilityException if the request fails to send
     */
    HttpResult post(String url, String body, Map<String, String> headers);

    /**
     * Sends an HTTP PUT request
     * 
     * @param url     the request URL, cannot be empty
     * @param body    the request body, can be null
     * @param headers the request headers, can be an empty Map
     * @return the HTTP response result
     * @throws HttpCapabilityException if the request fails to send
     */
    HttpResult put(String url, String body, Map<String, String> headers);

    /**
     * Sends an HTTP DELETE request
     * 
     * @param url     the request URL, cannot be empty
     * @param headers the request headers, can be an empty Map
     * @return the HTTP response result
     * @throws HttpCapabilityException if the request fails to send
     */
    HttpResult delete(String url, Map<String, String> headers);

    /**
     * Sends a GET request without headers (convenience method)
     */
    default HttpResult get(String url) {
        return get(url, Collections.emptyMap());
    }

    /**
     * Sends a POST request without headers (convenience method)
     */
    default HttpResult post(String url, String body) {
        return post(url, body, Collections.emptyMap());
    }

    // ==================== Response Result ====================

    /**
     * HTTP Response Result
     * 
     * @param statusCode      the HTTP status code
     * @param body            the response body string
     * @param responseHeaders the response headers
     */
    record HttpResult(
            int statusCode,
            String body,
            Map<String, List<String>> responseHeaders
    ) {
        /**
         * Checks if the response was successful (2xx status code)
         */
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }

        /**
         * Creates a result containing only status code and response body
         */
        public static HttpResult of(int statusCode, String body) {
            return new HttpResult(statusCode, body, Collections.emptyMap());
        }
    }
}
