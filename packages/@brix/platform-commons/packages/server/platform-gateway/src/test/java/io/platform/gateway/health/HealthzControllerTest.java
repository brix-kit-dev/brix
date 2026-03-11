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
package io.brix.platform.gateway.health;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.HttpStatus;

import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HealthzController Unit Tests
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HealthzController Test")
@SuppressWarnings("unused") // setUp is used by JUnit
class HealthzControllerTest {

    @Mock
    private HealthEndpoint healthEndpoint;

    private HealthzController controller;

    @BeforeEach
    void setUp() {
        controller = new HealthzController(healthEndpoint);
    }

    @Test
    @DisplayName("/healthz should return 200 UP status when healthy")
    void healthzShouldReturnOkWhenHealthy() {
        Health health = Health.up().build();
        when(healthEndpoint.health()).thenReturn(health);

        StepVerifier.create(controller.healthz())
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    Map<String, Object> body = response.getBody();
                    assertNotNull(body);
                    assertEquals("UP", body.get("status"));
                    assertEquals("brix-platform-gateway", body.get("service"));
                    assertNotNull(body.get("timestamp"));
                    return true;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("/healthz should return 503 DOWN status when unhealthy")
    void healthzShouldReturn503WhenUnhealthy() {
        Health health = Health.down().withDetail("error", "test").build();
        when(healthEndpoint.health()).thenReturn(health);

        StepVerifier.create(controller.healthz())
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
                    Map<String, Object> body = response.getBody();
                    assertNotNull(body);
                    assertEquals("DOWN", body.get("status"));
                    return true;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("/health/ping should always return 200")
    void pingShouldAlwaysReturn200() {
        StepVerifier.create(controller.ping())
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    return true;
                })
                .verifyComplete();
    }
}
