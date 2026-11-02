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
package io.brix.architecture.guard.profiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import io.brix.platform.fixture.CompliantPlatformService;
import io.brix.platform.fixture.PlatformUsesAdapter;
import io.brix.platform.fixture.PlatformUsesHost;
import io.brix.platform.fixture.PlatformUsesKafka;
import io.brix.platform.fixture.PlatformUsesOrchestrator;
import io.brix.platform.fixture.PlatformUsesPlugin;
import io.brix.platform.fixture.PlatformUsesRabbit;
import io.brix.platform.fixture.redis.ListedRedisClient;
import io.brix.platform.fixture.redis.UnlistedRedisClient;
import io.brix.samples.fixturebeta.core.BetaStock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the commons profile checks the real platform root, io.brix.platform: each dependency rule rejects its
 * deliberate violation, and a module without platform classes fails instead of passing on nothing.
 */
class CommonsProfileTest {

    @Test
    @DisplayName("A platform class that uses a plugin fails")
    void pluginDependencyFails() {
        assertRejects(CommonsProfile.commonsNotDependOnPlugins, PlatformUsesPlugin.class, BetaStock.class);
    }

    @Test
    @DisplayName("A platform class that uses an infrastructure adapter fails")
    void adapterDependencyFails() {
        assertRejects(CommonsProfile.commonsNotDependOnAdapters, PlatformUsesAdapter.class,
            io.infra.adapter.kafka.KafkaAdapter.class);
    }

    @Test
    @DisplayName("A platform class that uses Host source fails")
    void hostDependencyFails() {
        assertRejects(CommonsProfile.commonsNotDependOnHost, PlatformUsesHost.class,
            io.brix.enterprise.host.fixture.HostOnlyType.class);
    }

    @Test
    @DisplayName("A platform class that uses the Runtime implementation fails")
    void orchestratorDependencyFails() {
        assertRejects(CommonsProfile.commonsNotDependOnOrchestrator, PlatformUsesOrchestrator.class,
            io.runtime.orchestrator.bootstrap.RuntimeShellFatalAction.class);
    }

    @Test
    @DisplayName("A platform class that uses the Kafka client fails")
    void kafkaDependencyFails() {
        assertRejects(CommonsProfile.noKafkaDependency, PlatformUsesKafka.class,
            org.apache.kafka.clients.producer.KafkaProducer.class);
    }

    @Test
    @DisplayName("A platform class that uses the RabbitMQ client fails")
    void rabbitDependencyFails() {
        assertRejects(CommonsProfile.noRabbitDependency, PlatformUsesRabbit.class, com.rabbitmq.client.Channel.class);
    }

    @Test
    @DisplayName("A platform class that uses Redis fails unless its module lists it")
    void redisDependencyFailsUnlessListed() {
        assertRejects(CommonsProfile.noRedisDependency, UnlistedRedisClient.class,
            org.springframework.data.redis.core.StringRedisTemplate.class);
        assertDoesNotThrow(() -> CommonsProfile.noRedisDependency.check(
            new ClassFileImporter().importClasses(ListedRedisClient.class, CompliantPlatformService.class)));
    }

    @Test
    @DisplayName("A platform class that uses only the JDK passes every dependency rule")
    void compliantPlatformCodePasses() {
        JavaClasses classes = new ClassFileImporter().importClasses(CompliantPlatformService.class);

        for (ArchRule rule : dependencyRules()) {
            assertDoesNotThrow(() -> rule.check(classes), rule.getDescription());
        }
    }

    @Test
    @DisplayName("A module with no class under the platform root fails rather than passing on nothing")
    void noPlatformClassFails() {
        JavaClasses classes = new ClassFileImporter().importClasses(BetaStock.class);

        for (ArchRule rule : dependencyRules()) {
            assertThrows(AssertionError.class, () -> rule.check(classes), rule.getDescription());
        }
    }

    private static List<ArchRule> dependencyRules() {
        return List.of(CommonsProfile.commonsNotDependOnPlugins, CommonsProfile.commonsNotDependOnAdapters,
            CommonsProfile.commonsNotDependOnHost, CommonsProfile.commonsNotDependOnOrchestrator,
            CommonsProfile.noKafkaDependency, CommonsProfile.noRedisDependency, CommonsProfile.noRabbitDependency);
    }

    private static void assertRejects(ArchRule rule, Class<?> violating, Class<?> forbidden) {
        AssertionError error = assertThrows(AssertionError.class,
            () -> rule.check(new ClassFileImporter().importClasses(violating)));
        assertTrue(error.getMessage().contains(forbidden.getName()), error.getMessage());
    }
}
