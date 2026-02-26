package io.brix.platform.gateway.health;

import java.time.Instant;
import java.util.Map;

import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/**
 * 轻量级健康检查端
 * <p>
 * 提供 /healthz 端点，用于简单的健康探测
 * 此端点被设计为尽可能轻量，适合高频率的负载均衡器健康检查
 * </p>
 * 
 * <h3>响应格式</h3>
 * <pre>
 * {
 *   "status": "UP",
 *   "timestamp": "2025-12-06T10:30:00Z",
 *   "service": "shinwa-platform-gateway"
 * }
 * </pre>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since MVP v1.0
 */
@RestController
public class HealthzController {

    private static final String SERVICE_NAME = "shinwa-platform-gateway";
    private final HealthEndpoint healthEndpoint;

    public HealthzController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    /**
     * 轻量级健康检查端
     * <p>
     * 返回简单的健康状态，用于负载均衡器或 K8s Ingress 健康检查
     * </p>
     * 
     * @return 健康状态响
     */
    @GetMapping("/healthz")
    public Mono<ResponseEntity<Map<String, Object>>> healthz() {
        return Mono.fromCallable(() -> {
            HealthComponent health = healthEndpoint.health();
            Status status = health.getStatus();
            
            HttpStatus httpStatus = Status.UP.equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            
            Map<String, Object> response = Map.of(
                    "status", status.getCode(),
                    "timestamp", Instant.now().toString(),
                    "service", SERVICE_NAME
            );
            
            return ResponseEntity.status(httpStatus).body(response);
        });
    }

    /**
     * 最简健康检查端
     * <p>
     * 只返200 OK，用于最基础的存活检测
     * 不检查任何依赖，仅表示进程存活
     * </p>
     * 
     * @return 空响应体，状态码 200
     */
    @GetMapping("/health/ping")
    public Mono<ResponseEntity<Void>> ping() {
        return Mono.just(ResponseEntity.ok().build());
    }
}
