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
 * HealthzController 单元测试
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HealthzController 测试")
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
    @DisplayName("健康时 /healthz 应返回 200 UP 状态")
    void healthzShouldReturnOkWhenHealthy() {
        Health health = Health.up().build();
        when(healthEndpoint.health()).thenReturn(health);

        StepVerifier.create(controller.healthz())
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    Map<String, Object> body = response.getBody();
                    assertNotNull(body);
                    assertEquals("UP", body.get("status"));
                    assertEquals("shinwa-platform-gateway", body.get("service"));
                    assertNotNull(body.get("timestamp"));
                    return true;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("不健康时 /healthz 应返回 503 DOWN 状态")
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
    @DisplayName("/health/ping 应始终返200")
    void pingShouldAlwaysReturn200() {
        StepVerifier.create(controller.ping())
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    return true;
                })
                .verifyComplete();
    }
}
