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

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import io.brix.architecture.guard.rules.ConfigViaCapabilityRule;
import io.brix.architecture.guard.rules.EventsViaCapabilityRule;
import io.brix.architecture.guard.rules.HostUltraThinRule;
import io.brix.architecture.guard.rules.NoDirectHttpClientsRule;
import io.brix.architecture.guard.rules.NoMiddlewareClientsRule;

/**
 * Host Layer Architecture Constraint Rules (Enhanced Version - Covering More Red Lines)
 *
 * <p>Host layer serves as the "ultra-thin assembly layer" with special architectural constraints.
 * According to the v3.0 Runtime Shell Architecture Design Blueprint, Host layer is allowed to
 * directly use infrastructure adapters and Spring Container APIs (Red Lines 1/5 relaxed),
 * but must follow the "ultra-thin principle" and other architecture red lines.</p>
 *
 * <h2>Relaxed Rules</h2>
 * <ul>
 *   <li>Red Line 1 (Infrastructure Adapter Dependency) - Host layer needs to directly assemble adapters, import allowed</li>
 *   <li>Red Line 5 (Spring Container API) - Host layer needs ApplicationContext for Bean registration, usage allowed</li>
 * </ul>
 *
 * <h2>Strictly Enforced Rules</h2>
 * <ul>
 *   <li>Red Line 2 - Prohibit direct use of middleware clients (Kafka/Redis/AMQP, etc.)</li>
 *   <li>Red Line 3 - Prohibit direct use of HTTP clients (RestTemplate/WebClient/OkHttp)</li>
 *   <li>Red Line 6 - Prohibit direct access to system environment variables and config (partially relaxed: Properties injection allowed)</li>
 *   <li>Red Line 8 - Events must be published through EventBusCapability</li>
 * </ul>
 *
 * <h2>Ultra-Thin Principle</h2>
 * <ul>
 *   <li>AutoConfiguration classes are prohibited from defining @Bean methods</li>
 *   <li>Complex control flow prohibited (if/else/for/while/try-catch)</li>
 *   <li>Private helper methods prohibited</li>
 *   <li>Business annotations prohibited (@Service/@Repository/@Controller, etc.)</li>
 *   <li>Implementing Capability interfaces prohibited (should be in infra-adapter)</li>
 * </ul>
 *
 * <h2>Architecture Blueprint Reference</h2>
 * <p>See v3.0 Runtime Shell Architecture Design Blueprint Section 9.1 "Host Layer Responsibility Boundaries"</p>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
public class HostProfile {

    // ============================================================================
    // Relaxed Rules
    // ============================================================================
    // Rule 1 - Relaxed (Host may use infra adapters, needs to import io.brix.infra.adapter.*)
    // Rule 5 - Relaxed (Host may use Spring container API, needs ApplicationContext)

    // ============================================================================
    // Rule 2: No middleware clients
    // ============================================================================
    // Even if Host can use adapters, it should not use middleware client APIs directly

    /** No direct Spring Kafka */
    @ArchTest
    static final ArchRule noSpringKafka = NoMiddlewareClientsRule.noSpringKafka();

    /** No direct Apache Kafka client */
    @ArchTest
    static final ArchRule noApacheKafka = NoMiddlewareClientsRule.noApacheKafka();

    /** No direct Spring Data Redis */
    @ArchTest
    static final ArchRule noSpringDataRedis = NoMiddlewareClientsRule.noSpringDataRedis();

    /** No direct Jedis client */
    @ArchTest
    static final ArchRule noJedis = NoMiddlewareClientsRule.noJedis();

    /** No direct Lettuce client */
    @ArchTest
    static final ArchRule noLettuce = NoMiddlewareClientsRule.noLettuce();

    /** No direct Spring AMQP / RabbitMQ */
    @ArchTest
    static final ArchRule noSpringAmqp = NoMiddlewareClientsRule.noSpringAmqp();

    // ============================================================================
    // Rule 3: No HTTP clients
    // ============================================================================

    /** No direct RestTemplate */
    @ArchTest
    static final ArchRule noRestTemplate = NoDirectHttpClientsRule.noRestTemplate();

    /** No direct WebClient */
    @ArchTest
    static final ArchRule noWebClient = NoDirectHttpClientsRule.noWebClient();

    /** No direct OkHttp */
    @ArchTest
    static final ArchRule noOkHttp = NoDirectHttpClientsRule.noOkHttp();

    // ============================================================================
    // Rule 6: Config access (partially relaxed)
    // ============================================================================
    // Host layer may use @ConfigurationProperties for injection
    // But still no direct System.getenv() / System.getProperty()

    /** No direct System.getenv() / System.getProperty() */
    @ArchTest
    static final ArchRule noSystemEnvAccess = ConfigViaCapabilityRule.noSystemEnvAccess();

    // ============================================================================
    // Rule 8: Events via EventBusCapability
    // ============================================================================

    /** Events must go through EventBusCapability, no direct ApplicationEventPublisher */
    @ArchTest
    static final ArchRule eventsViaCapability = EventsViaCapabilityRule.rule();

    // ============================================================================
    // Ultra-Thin Principle - Host-specific constraints
    // ============================================================================

    /**
     * AutoConfiguration classes must not define @Bean methods.
     *
     * <p>Host layer only assembles, should not define any Beans.
     * All Capability Bean registration belongs in infra-adapter modules.</p>
     */
    @ArchTest
    static final ArchRule noBeanDefinitions = HostUltraThinRule.noBeanDefinitionsInAutoConfiguration();

    /**
     * Host layer must not contain complex control flow.
     *
     * <p>if/else/for/while/try-catch structures indicate business logic,
     * violating the "pure assembly layer" design.</p>
     */
    @ArchTest
    static final ArchRule noControlFlow = HostUltraThinRule.noControlFlowInHost();

    /**
     * AutoConfiguration classes must not have private helper methods.
     *
     * <p>Private methods typically indicate reusable business logic.</p>
     */
    @ArchTest
    static final ArchRule noPrivateMethods = HostUltraThinRule.noPrivateMethodsInAutoConfiguration();

    /**
     * Host layer must not use business component annotations.
     *
     * <p>No @Service/@Repository/@Controller annotations.</p>
     */
    @ArchTest
    static final ArchRule noBusinessAnnotations = HostUltraThinRule.noBusinessAnnotations();

    /**
     * Host layer must not implement Capability interfaces.
     *
     * <p>Capability implementations belong in infra-adapter modules.</p>
     */
    @ArchTest
    static final ArchRule noCapabilityImplementation = HostUltraThinRule.noCapabilityImplementation();
}
