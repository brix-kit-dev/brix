/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Infrastructure Adapter Isolation Rules.
 *
 * <p>Enforces that infra-adapters remain isolated and do not create
 * cross-dependencies or leak third-party types.</p>
 *
 * <h2>Defects Addressed (From Evaluation Report)</h2>
 * <ul>
 *   <li><b>D4</b>: adapter间交叉依赖 - infra-adapter-outbox → infra-adapter-kafka</li>
 *   <li><b>D5</b>: adapter泄露第三方类型到core模块</li>
 * </ul>
 *
 * <h2>Design Principle</h2>
 * <p>Each adapter should be a self-contained, replaceable unit. If adapters depend
 * on each other, it creates coupling that defeats the purpose of the adapter pattern.</p>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
public final class AdapterIsolationRule {

    private AdapterIsolationRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== Adapter Package Patterns ====================

    private static final String INFRA_ADAPTER_BASE = "io.infra.adapter..";
    
    // Known adapters - each should not depend on others
    private static final String[] ADAPTER_PACKAGES = {
        "io.infra.adapter.kafka..",
        "io.infra.adapter.redis..",
        "io.infra.adapter.outbox..",
        "io.infra.adapter.simple..",
        "io.infra.adapter.fallback..",
        "io.infra.adapter.http..",
        "io.infra.adapter.biometric..",
        "io.infra.adapter.storage.."
    };

    // Third-party types that should NOT leak to consumers
    private static final String KAFKA_TYPES = "org.apache.kafka..";
    private static final String REDIS_TYPES = "io.lettuce..|redis.clients.jedis..";
    private static final String RABBIT_TYPES = "com.rabbitmq..";

    // ==================== Cross-Adapter Dependency Rules ====================

    /**
     * Kafka adapter should not depend on other adapters.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule kafkaAdapterIsolated() {
        return noClasses()
                .that().resideInAPackage("io.infra.adapter.kafka..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "io.infra.adapter.redis..",
                        "io.infra.adapter.outbox..",
                        "io.infra.adapter.http.."
                )
                .allowEmptyShould(true)
                .because("Kafka adapter should not depend on other adapters (D4)");
    }

    /**
     * Outbox adapter should not depend on specific messaging adapters.
     *
     * <p>Outbox pattern should work with any messaging system, so it should
     * depend on abstractions (Capability interfaces), not concrete adapters.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule outboxAdapterIsolated() {
        return noClasses()
                .that().resideInAPackage("io.infra.adapter.outbox..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "io.infra.adapter.kafka..",
                        "io.infra.adapter.rabbit.."
                )
                .allowEmptyShould(true)
                .because("Outbox adapter should not directly depend on messaging adapters. " +
                        "Use Capability abstractions instead (D4)");
    }

    /**
     * Simple adapter should not depend on fallback adapter.
     *
     * <p>Each adapter should implement its own strategy, not chain to others.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule simpleAdapterIsolated() {
        return noClasses()
                .that().resideInAPackage("io.infra.adapter.simple..")
                .should().dependOnClassesThat()
                .resideInAPackage("io.infra.adapter.fallback..")
                .allowEmptyShould(true)
                .because("Simple adapter should not depend on fallback adapter (D4)");
    }

    // ==================== Third-Party Type Leakage Rules ====================
    // NOTE: These rules are intended for CONSUMER tests (Plugin/Host), not adapter tests.
    // When testing an adapter module, the adapter code correctly uses third-party libs.

    /**
     * Classes outside adapter packages should not depend on Kafka types.
     *
     * <p>Kafka types (Producer, Consumer, ConsumerRecord, etc.) should only
     * be used within the Kafka adapter, not leaked to core modules.</p>
     * 
     * <p><b>Note:</b> Use this from PluginProfile/HostProfile, not AdapterProfile.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noKafkaTypeLeakage() {
        return noClasses()
                .that().resideOutsideOfPackage("io.infra.adapter.kafka..")
                .and().resideOutsideOfPackage("..test..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.apache.kafka..")
                .allowEmptyShould(true)
                .because("Kafka types should be encapsulated in kafka adapter; " +
                        "core modules should use Capability interfaces (D5)");
    }

    /**
     * Classes outside adapter packages should not depend on Redis client types.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noRedisTypeLeakage() {
        return noClasses()
                .that().resideOutsideOfPackage("io.infra.adapter.redis..")
                .and().resideOutsideOfPackage("..test..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("io.lettuce..", "redis.clients.jedis..")
                .allowEmptyShould(true)
                .because("Redis client types should be encapsulated in redis adapter; " +
                        "core modules should use Capability interfaces (D5)");
    }

    /**
     * Classes outside adapter packages should not depend on RabbitMQ types.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noRabbitTypeLeakage() {
        return noClasses()
                .that().resideOutsideOfPackage("io.infra.adapter.rabbit..")
                .and().resideOutsideOfPackage("..test..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.rabbitmq..")
                .allowEmptyShould(true)
                .because("RabbitMQ types should be encapsulated in rabbit adapter; " +
                        "core modules should use Capability interfaces (D5)");
    }

    // ==================== Public Interface Rule ====================

    /**
     * Adapter public interfaces should only use runtime-sdk-api types.
     *
     * <p>Methods exposed by adapters (implementing Capability interfaces)
     * should not expose third-party types in method signatures.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule publicInterfaceNoThirdPartyTypes() {
        // Note: This is a documentation rule; full implementation would
        // require method signature analysis which is more complex
        return noClasses()
                .that().resideInAPackage("io.infra.adapter..")
                .and().haveSimpleNameEndingWith("Capability")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.apache.kafka..",
                        "io.lettuce..",
                        "redis.clients.jedis..",
                        "com.rabbitmq.."
                )
                .allowEmptyShould(true)
                .because("Capability interfaces must not leak third-party types in signatures (D5)");
    }
}
