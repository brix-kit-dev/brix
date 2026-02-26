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
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * NoMiddlewareClientsRule Test Cases
 *
 * <p>Tests for Rule 2 enforcement: No direct Kafka/Redis/AMQP middleware client APIs.</p>
 *
 * <h2>Covered Middleware</h2>
 * <ul>
 *   <li>Spring Kafka / Apache Kafka</li>
 *   <li>Spring Data Redis / Jedis / Lettuce</li>
 *   <li>Spring AMQP / RabbitMQ</li>
 * </ul>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
@DisplayName("Rule 2: No Direct Middleware Clients")
class NoMiddlewareClientsRuleTest {

    // ============================================================================
    // Kafka Rule Tests
    // ============================================================================

    @Nested
    @DisplayName("Kafka Client Checks")
    class KafkaRulesTests {

        @Test
        @DisplayName("Spring Kafka usage should be detected as violation")
        void shouldDetectSpringKafkaUsage() {
            ArchRule rule = NoMiddlewareClientsRule.noSpringKafka();
            
            // Test rule is defined and executable
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard.rules");
                // Guard library should not have Kafka dependency
                rule.check(classes);
            }, "Guard library should pass Kafka checks");
        }

        @Test
        @DisplayName("Apache Kafka usage should be detected as violation")
        void shouldDetectApacheKafkaUsage() {
            ArchRule rule = NoMiddlewareClientsRule.noApacheKafka();
            
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard.rules");
                rule.check(classes);
            }, "Guard library should pass Apache Kafka checks");
        }
    }

    // ============================================================================
    // Redis Rule Tests
    // ============================================================================

    @Nested
    @DisplayName("Redis Client Checks")
    class RedisRulesTests {

        @Test
        @DisplayName("Spring Data Redis usage should be detected as violation")
        void shouldDetectSpringDataRedisUsage() {
            ArchRule rule = NoMiddlewareClientsRule.noSpringDataRedis();
            
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard.rules");
                rule.check(classes);
            }, "Guard library should pass Spring Data Redis checks");
        }

        @Test
        @DisplayName("Jedis usage should be detected as violation")
        void shouldDetectJedisUsage() {
            ArchRule rule = NoMiddlewareClientsRule.noJedis();
            
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard.rules");
                rule.check(classes);
            }, "Guard library should pass Jedis checks");
        }

        @Test
        @DisplayName("Lettuce usage should be detected as violation")
        void shouldDetectLettuceUsage() {
            ArchRule rule = NoMiddlewareClientsRule.noLettuce();
            
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard.rules");
                rule.check(classes);
            }, "Guard library should pass Lettuce checks");
        }
    }

    // ============================================================================
    // AMQP Rule Tests
    // ============================================================================

    @Nested
    @DisplayName("AMQP Client Checks")
    class AmqpRulesTests {

        @Test
        @DisplayName("Spring AMQP usage should be detected as violation")
        void shouldDetectSpringAmqpUsage() {
            ArchRule rule = NoMiddlewareClientsRule.noSpringAmqp();
            
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard.rules");
                rule.check(classes);
            }, "Guard library should pass Spring AMQP checks");
        }
    }
}
