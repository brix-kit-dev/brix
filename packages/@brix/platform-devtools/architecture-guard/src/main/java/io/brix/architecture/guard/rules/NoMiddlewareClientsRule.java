/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import io.brix.architecture.guard.ArchitectureLayers;

/**
 * No Direct Middleware Clients Rule.
 *
 * <p>Business modules must not directly use Kafka, Redis, RabbitMQ client APIs.
 * All middleware access must go through corresponding Capability interfaces.</p>
 *
 * @since 3.1.0
 */
public final class NoMiddlewareClientsRule {

    private NoMiddlewareClientsRule() {}

    public static ArchRule noSpringKafka() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(ArchitectureLayers.SPRING_KAFKA)
                .because("Direct Spring Kafka usage is forbidden. Use EventBusCapability");
    }

    public static ArchRule noApacheKafka() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(ArchitectureLayers.APACHE_KAFKA)
                .because("Direct Apache Kafka client usage is forbidden. Use EventBusCapability");
    }

    public static ArchRule noSpringDataRedis() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(ArchitectureLayers.SPRING_DATA_REDIS)
                .because("Direct Spring Data Redis usage is forbidden. Use StateStoreCapability/CacheCapability");
    }

    public static ArchRule noJedis() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(ArchitectureLayers.JEDIS)
                .because("Direct Jedis client usage is forbidden. Use StateStoreCapability/CacheCapability");
    }

    public static ArchRule noLettuce() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(ArchitectureLayers.LETTUCE)
                .because("Direct Lettuce client usage is forbidden. Use StateStoreCapability/CacheCapability");
    }

    public static ArchRule noSpringAmqp() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(ArchitectureLayers.SPRING_AMQP)
                .because("Direct Spring AMQP/RabbitMQ usage is forbidden. Use EventBusCapability");
    }
}
