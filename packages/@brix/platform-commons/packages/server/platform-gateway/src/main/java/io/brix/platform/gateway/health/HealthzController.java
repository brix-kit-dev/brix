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
 * Lightweight Health Check Endpoint
 * <p>
 * Provides /healthz endpoint for simple health probes.
 * This endpoint is designed to be as lightweight as possible, suitable for high-frequency load balancer health checks.
 * </p>
 * 
 * <h3>Response Format</h3>
 * <pre>
 * {
 *   "status": "UP",
 *   "timestamp": "2025-12-06T10:30:00Z",
 *   "service": "brix-platform-gateway"
 * }
 * </pre>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since MVP v1.0
 */
@RestController
public class HealthzController {

    private static final String SERVICE_NAME = "brix-platform-gateway";
    private final HealthEndpoint healthEndpoint;

    public HealthzController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    /**
     * Lightweight health check endpoint
     * <p>
     * Returns simple health status for load balancer or K8s Ingress health checks.
     * </p>
     * 
     * @return health status response
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
     * Minimal health check endpoint
     * <p>
     * Only returns 200 OK, for basic liveness detection.
     * Does not check any dependencies, only indicates process is alive.
     * </p>
     * 
     * @return empty response body with 200 status code
     */
    @GetMapping("/health/ping")
    public Mono<ResponseEntity<Void>> ping() {
        return Mono.just(ResponseEntity.ok().build());
    }
}
