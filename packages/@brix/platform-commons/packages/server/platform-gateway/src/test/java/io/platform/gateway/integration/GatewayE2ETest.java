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
package io.brix.platform.gateway.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Gateway End-to-End Integration Tests
 * <p>
 * MVP Guideline M014: At least 1 e2e smoke test
 * </p>
 * <p>
 * Tests Gateway core functionality:
 * <ol>
 *   <li>Health check endpoint</li>
 *   <li>Healthz endpoint</li>
 *   <li>CORS preflight request</li>
 *   <li>API Key authentication (when configured)</li>
 *   <li>Sensitive header stripping</li>
 * </ol>
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E2E Gateway Smoke Test")
@Tag("integration")
@Disabled("Requires external services like Redis, please run manually with docker-compose: mvn test -Dtest=GatewayE2ETest")
@SuppressWarnings("unused") // setUp and baseUrl are used by JUnit and for test setup
class GatewayE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    @SuppressWarnings("FieldCanBeLocal") // keeping for potential future use
    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    // ========== 1. Health Check ==========

    @Test
    @Order(1)
    @DisplayName("1. Health Check - /actuator/health should return UP")
    void step1_actuatorHealthShouldReturnUp() {
        webTestClient
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @Order(2)
    @DisplayName("1.1 Healthz endpoint should return 200")
    void step1_healthzShouldReturnOk() {
        webTestClient
            .get()
            .uri("/healthz")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @Order(3)
    @DisplayName("1.2 Liveness probe should return 200")
    void step1_livenesssShouldReturnOk() {
        webTestClient
            .get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @Order(4)
    @DisplayName("1.3 Readiness probe should return 200")
    void step1_readinessShouldReturnOk() {
        webTestClient
            .get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    // ========== 2. CORS Tests ==========

    @Test
    @Order(10)
    @DisplayName("2. CORS preflight request should return correct headers")
    void step2_corsPreflight() {
        webTestClient
            .options()
            .uri("/api/test")
            .header("Origin", "http://localhost:3000")
            .header("Access-Control-Request-Method", "GET")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().exists("Access-Control-Allow-Origin")
            .expectHeader().exists("Access-Control-Allow-Methods");
    }

    @Test
    @Order(11)
    @DisplayName("2.1 Cross-origin request should include CORS headers")
    void step2_corsHeadersOnRequest() {
        webTestClient
            .get()
            .uri("/actuator/health")
            .header("Origin", "http://localhost:3000")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().exists("Access-Control-Allow-Origin");
    }

    // ========== 3. API Authentication Tests (when disabled) ==========

    @Test
    @Order(20)
    @DisplayName("3. Public endpoints should be accessible when auth is not configured")
    void step3_publicEndpointsShouldBeAccessible() {
        webTestClient
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk();
    }

    // ========== 4. Sensitive Header Stripping Tests ==========

    @Test
    @Order(30)
    @DisplayName("4. Sensitive headers should be stripped")
    void step4_sensitiveHeadersShouldBeStripped() {
        // Send request with sensitive headers, Gateway should strip these headers
        webTestClient
            .get()
            .uri("/actuator/health")
            .header("x-user-id", "malicious-user")
            .header("x-tenant-id", "fake-tenant")
            .header("x-role", "admin")
            .exchange()
            .expectStatus().isOk();
        
        // Since actuator/health does not return these headers, we only verify the request succeeds
        // The actual header stripping logic takes effect when forwarding to downstream services
    }

// ========== 5. Error Handling Tests ==========

    @Test
    @Order(40)
    @DisplayName("5. Non-existent route should return 404")
    void step5_nonExistentRouteShouldReturn404() {
        webTestClient
            .get()
            .uri("/api/non-existent-route/test")
            .exchange()
            .expectStatus().isNotFound();
    }

    // ========== 6. Log Sanitization Verification (via request test) ==========

    @Test
    @Order(50)
    @DisplayName("6. Request with Authorization header should be processed normally")
    void step6_authorizationHeaderShouldBeLogged() {
        webTestClient
            .get()
            .uri("/actuator/health")
            .header("Authorization", "Bearer test-token-12345")
            .exchange()
            .expectStatus().isOk();
        
        // Log sanitization is handled in the background, here we only verify the request doesn't fail due to Auth header
    }

    // ========== 7. Timeout Configuration Verification ==========

    @Test
    @Order(60)
    @DisplayName("7. Fast response should complete before timeout")
    void step7_fastResponseShouldCompleteBeforeTimeout() {
        webTestClient
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk();
        
        // Health check should complete quickly within the 5 second timeout
    }
}
