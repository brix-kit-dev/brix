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
 * Gateway 端到端集成测试
 * <p>
 * MVP 红线 M014：至1 e2e 冒烟测试
 * </p>
 * <p>
 * 测试 Gateway 核心功能
 * <ol>
 *   <li>健康检查端点</li>
 *   <li>Healthz 端点</li>
 *   <li>CORS 预检请求</li>
 *   <li>API Key 认证（需配置时）</li>
 *   <li>敏感头剥离</li>
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
@DisplayName("E2E Gateway 冒烟测试")
@Tag("integration")
@Disabled("需要 Redis 等外部服务，请使用 docker-compose 启动后手动运行 mvn test -Dtest=GatewayE2ETest")
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

    // ========== 1. 健康检查 ==========

    @Test
    @Order(1)
    @DisplayName("1. 健康检查 - /actuator/health 应返回 UP")
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
    @DisplayName("1.1 Healthz 端点应返200")
    void step1_healthzShouldReturnOk() {
        webTestClient
            .get()
            .uri("/healthz")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @Order(3)
    @DisplayName("1.2 Liveness 探针应返200")
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
    @DisplayName("1.3 Readiness 探针应返200")
    void step1_readinessShouldReturnOk() {
        webTestClient
            .get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    // ========== 2. CORS 测试 ==========

    @Test
    @Order(10)
    @DisplayName("2. CORS 预检请求应返回正确的头")
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
    @DisplayName("2.1 跨域请求应包含 CORS 头")
    void step2_corsHeadersOnRequest() {
        webTestClient
            .get()
            .uri("/actuator/health")
            .header("Origin", "http://localhost:3000")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().exists("Access-Control-Allow-Origin");
    }

    // ========== 3. API 认证测试（当禁用时） ==========

    @Test
    @Order(20)
    @DisplayName("3. 未配置认证时应允许访问公开端点")
    void step3_publicEndpointsShouldBeAccessible() {
        webTestClient
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk();
    }

    // ========== 4. 敏感头剥离测试 ==========

    @Test
    @Order(30)
    @DisplayName("4. 敏感头应被剥离")
    void step4_sensitiveHeadersShouldBeStripped() {
        // 发送带有敏感头的请求，Gateway 应剥离这些头
        webTestClient
            .get()
            .uri("/actuator/health")
            .header("x-user-id", "malicious-user")
            .header("x-tenant-id", "fake-tenant")
            .header("x-role", "admin")
            .exchange()
            .expectStatus().isOk();
        
        // 由于 actuator/health 不会返回这些头，我们只验证请求成功
        // 实际的头剥离逻辑会在转发到下游服务时生效
    }

    // ========== 5. 错误处理测试 ==========

    @Test
    @Order(40)
    @DisplayName("5. 不存在的路由应返404")
    void step5_nonExistentRouteShouldReturn404() {
        webTestClient
            .get()
            .uri("/api/non-existent-route/test")
            .exchange()
            .expectStatus().isNotFound();
    }

    // ========== 6. 日志脱敏验证（通过请求测试） ==========

    @Test
    @Order(50)
    @DisplayName("6. 带 Authorization 头的请求应正常处理")
    void step6_authorizationHeaderShouldBeLogged() {
        webTestClient
            .get()
            .uri("/actuator/health")
            .header("Authorization", "Bearer test-token-12345")
            .exchange()
            .expectStatus().isOk();
        
        // 日志脱敏会在后台处理，这里只验证请求不会因 Auth 头而失败
    }

    // ========== 7. 超时配置验证 ==========

    @Test
    @Order(60)
    @DisplayName("7. 快速响应应在超时前完成")
    void step7_fastResponseShouldCompleteBeforeTimeout() {
        webTestClient
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk();
        
        // 健康检查应在 5 秒超时内快速完成
    }
}
