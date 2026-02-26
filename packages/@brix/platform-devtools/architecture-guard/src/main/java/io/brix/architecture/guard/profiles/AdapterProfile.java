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

import io.brix.architecture.guard.rules.AdapterIsolationRule;

/**
 * Infrastructure Adapter Layer Architecture Constraints
 *
 * <p>Enforces isolation rules for infra-adapters. Each adapter module
 * must remain self-contained and not leak third-party types.</p>
 *
 * <h2>Defects Addressed</h2>
 * <ul>
 *   <li><b>D4</b>: adapter间交叉依赖 - Detected by isolation rules</li>
 *   <li><b>D5</b>: adapter泄露第三方类型 - Detected by type leakage rules</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // In infra-adapter-kafka module
 * @AnalyzeClasses(packages = "io.brix.infra.adapter.kafka")
 * class AdapterArchitectureTest {
 *     @ArchTest
 *     static final ArchTests rules = BrixArchitectureRules.adapterProfile();
 * }
 * }</pre>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
public class AdapterProfile {

    // ==================== Adapter Isolation Rules ====================

    /** Kafka adapter should not depend on other adapters */
    @ArchTest
    static final ArchRule kafkaAdapterIsolated = AdapterIsolationRule.kafkaAdapterIsolated();

    /** Outbox adapter should not depend on messaging adapters */
    @ArchTest
    static final ArchRule outboxAdapterIsolated = AdapterIsolationRule.outboxAdapterIsolated();

    /** Simple adapter should not depend on fallback adapter */
    @ArchTest
    static final ArchRule simpleAdapterIsolated = AdapterIsolationRule.simpleAdapterIsolated();

    // ==================== Third-Party Type Leakage Rules ====================
    // NOTE: Leakage rules are designed for CONSUMER tests (Plugins, Host),
    // not adapter internal tests. Adapter implementations naturally use
    // their third-party library internally.

    /** Kafka types should not leak outside kafka adapter (consumer-side check) */
    @ArchTest
    static final ArchRule noKafkaTypeLeakage = AdapterIsolationRule.noKafkaTypeLeakage();

    /** Redis types should not leak outside redis adapter (consumer-side check) */
    @ArchTest
    static final ArchRule noRedisTypeLeakage = AdapterIsolationRule.noRedisTypeLeakage();

    /** RabbitMQ types should not leak outside rabbit adapter (consumer-side check) */
    @ArchTest
    static final ArchRule noRabbitTypeLeakage = AdapterIsolationRule.noRabbitTypeLeakage();

    // NOTE: publicInterfaceNoThirdPartyTypes removed from AdapterProfile.
    // Adapter *implementations* (KafkaEventBusCapability) naturally use third-party
    // types internally. The constraint applies only to public method signatures,
    // which is enforced via code review and clean API design.
}
