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
package io.infra.adapter.kafka.health;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Health indicator for Kafka broker connectivity.
 *
 * <p>Provides health status for Spring Boot Actuator at /actuator/health endpoint.
 * Reports UP when at least one broker is reachable, DOWN otherwise.</p>
 *
 * <h3>Health Details</h3>
 * <ul>
 *   <li><b>clusterId</b>: Kafka cluster identifier</li>
 *   <li><b>brokerCount</b>: Number of reachable brokers</li>
 *   <li><b>brokers</b>: List of broker host:port addresses</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <p>Health check timeout can be configured via properties:</p>
 * <pre>
 * brix.infra.kafka.health.timeout-seconds=5
 * </pre>
 *
 * <p>健康指示器用于检查 Kafka Broker 的连通性。当至少一个 Broker 可达时报告 UP，
 * 否则报告 DOWN。</p>
 *
 * @author Brix Platform Team
 * @since 3.0.0
 * @see HealthIndicator
 * @see KafkaAdmin
 */
public class KafkaHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(KafkaHealthIndicator.class);

    /**
     * Default timeout for health check operations in seconds.
     * 健康检查操作的默认超时时间（秒）
     */
    private static final int DEFAULT_TIMEOUT_SECONDS = 5;

    private final KafkaAdmin kafkaAdmin;
    private final int timeoutSeconds;

    /**
     * Creates a new Kafka health indicator with default timeout.
     *
     * @param kafkaAdmin the Kafka admin client for cluster metadata operations
     */
    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this(kafkaAdmin, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Creates a new Kafka health indicator with custom timeout.
     *
     * @param kafkaAdmin     the Kafka admin client for cluster metadata operations
     * @param timeoutSeconds timeout for health check operations in seconds
     */
    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin, int timeoutSeconds) {
        this.kafkaAdmin = kafkaAdmin;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Performs health check by querying Kafka cluster metadata.
     *
     * <p>The health check connects to the configured bootstrap servers and retrieves
     * cluster information. If at least one broker responds within the timeout period,
     * the health status is UP.</p>
     *
     * <p>通过查询 Kafka 集群元数据来执行健康检查。如果至少有一个 Broker 在超时时间内响应，
     * 则健康状态为 UP。</p>
     *
     * @return Health status with cluster details
     */
    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterResult clusterResult = adminClient.describeCluster();

            // 获取集群 ID - Get cluster ID
            String clusterId = clusterResult.clusterId()
                    .get(timeoutSeconds, TimeUnit.SECONDS);

            // 获取 Broker 列表 - Get broker list
            Collection<Node> nodes = clusterResult.nodes()
                    .get(timeoutSeconds, TimeUnit.SECONDS);

            if (nodes.isEmpty()) {
                // 无可用 Broker - No available brokers
                log.warn("Kafka health check: no brokers available in cluster {}", clusterId);
                return Health.down()
                        .withDetail("clusterId", clusterId)
                        .withDetail("brokerCount", 0)
                        .withDetail("error", "No brokers available")
                        .build();
            }

            // 构建 Broker 地址列表 - Build broker address list
            String brokerList = nodes.stream()
                    .map(node -> node.host() + ":" + node.port())
                    .collect(Collectors.joining(", "));

            log.debug("Kafka health check: UP - cluster={}, brokers={}", clusterId, nodes.size());

            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("brokerCount", nodes.size())
                    .withDetail("brokers", brokerList)
                    .build();

        } catch (Exception e) {
            // 连接失败 - Connection failed
            log.warn("Kafka health check failed: {}", e.getMessage());
            return Health.down(e)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
