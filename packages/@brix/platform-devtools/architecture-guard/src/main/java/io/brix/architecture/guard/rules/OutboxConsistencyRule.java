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
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Red Line 13: Cross-Service Event Consistency (Transactional Outbox)
 * 
 * <p><b>Core Objective</b>: Prohibit plugins from bypassing {@code EventBusCapability}
 * to directly operate messaging middleware or Outbox/Relay implementations.
 * 
 * <p>Ensures plugin code does not bypass the unified entry point to send messages directly, preventing the following issues:
 * <ul>
 *   <li>Dual-write problem: Business data write succeeds but message send fails, or vice versa</li>
 *   <li>Event loss: Using @TransactionalEventListener + @Async may execute asynchronously before transaction commits</li>
 *   <li>Message duplication/loss: Direct new KafkaProducer() has no reliable delivery guarantee</li>
 * </ul>
 * 
 * <p>Correct approach:
 * <ul>
 *   <li>Publish events through EventBusCapability.publish()</li>
 *   <li>Runtime Shell decides reliability from the active plugin manifest</li>
 *   <li>Plugins never import Outbox repositories, Relay, broker SDKs, or adapter types</li>
 * </ul>
 * 
 * <p><b>Event Reliability Classification</b> (Outbox is not required for all scenarios):
 * <table border="1">
 *   <tr><th>Type</th><th>Examples</th><th>Loss Consequences</th><th>Send Strategy</th></tr>
 *   <tr><td>Critical Events</td><td>Order payment, Contract signing, Inventory deduction</td><td>Business inconsistency/Loss</td><td>Must use Outbox</td></tr>
 *   <tr><td>Non-critical Events</td><td>Welcome email, Marketing push, Log reporting</td><td>Slightly degraded experience, can resend</td><td>Best-effort</td></tr>
 * </table>
 * 
 * <p>Source: Netflix / Uber Outbox Pattern / CAP Theorem Eventual Consistency
 * 
 * @since 1.0.0
 */
public class OutboxConsistencyRule {

    private static final DescribedPredicate<JavaClass> LEGACY_OUTBOX_SSOT_TYPES =
        new DescribedPredicate<>("legacy L2C Outbox SSoT types") {
            @Override
            public boolean test(JavaClass javaClass) {
                String name = javaClass.getName();
                return name.equals("io.infra.adapter.outbox.CriticalEventOutboxAspect")
                    || name.equals("io.infra.adapter.outbox.OutboxEvent")
                    || name.equals("io.infra.adapter.outbox.OutboxEventPublisher")
                    || name.equals("io.infra.adapter.outbox.OutboxEventRepository")
                    || name.equals("io.infra.adapter.outbox.config.OutboxAutoConfiguration");
            }
        };

    private OutboxConsistencyRule() {
        // Utility class
    }

    /**
     * Prohibit plugins from directly using KafkaTemplate
     * 
     * <p>Plugins must publish events through EventBusCapability.publish(),
     * Runtime Shell will uniformly use Outbox pattern to ensure transaction consistency
     */
    public static ArchRule noDirectKafkaTemplateUsage() {
        return noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.kafka.core.KafkaTemplate")
            .because("Red Line 13: Direct KafkaTemplate calls prohibited, must use EventBusCapability.publish()")
            .allowEmptyShould(true);
    }

    /**
     * Prohibit plugins from directly using RabbitTemplate
     * 
     * <p>RabbitMQ also requires Outbox pattern to ensure transaction consistency
     */
    public static ArchRule noDirectRabbitTemplateUsage() {
        return noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.amqp.rabbit.core.RabbitTemplate")
            .because("Red Line 13: Direct RabbitTemplate calls prohibited, must use EventBusCapability.publish()")
            .allowEmptyShould(true);
    }

    /**
     * Prohibit plugins from directly creating KafkaProducer
     * 
     * <p>Direct new KafkaProducer() bypasses all framework-level protections
     */
    public static ArchRule noDirectKafkaProducerUsage() {
        return noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.apache.kafka.clients.producer.KafkaProducer")
            .because("Red Line 13: Direct new KafkaProducer() prohibited, must use EventBusCapability.publish()")
            .allowEmptyShould(true);
    }

    /**
     * Prohibit plugins from directly creating RabbitMQ ConnectionFactory
     * 
     * <p>Direct connection creation bypasses Outbox pattern
     */
    public static ArchRule noDirectRabbitConnectionUsage() {
        return noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("com.rabbitmq.client.ConnectionFactory")
            .because("Red Line 13: Direct RabbitMQ connection creation prohibited, must use EventBusCapability")
            .allowEmptyShould(true);
    }

    /**
     * Prohibit direct StreamBridge sending (Spring Cloud Stream)
     * 
     * <p>StreamBridge also requires Outbox pattern
     */
    public static ArchRule noDirectStreamBridgeUsage() {
        return noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.cloud.stream.function.StreamBridge")
            .because("Red Line 13: Direct StreamBridge usage prohibited, must use EventBusCapability.publish()")
            .allowEmptyShould(true);
    }

    /**
     * Prohibit direct JmsTemplate sending
     * 
     * <p>JMS messages also require Outbox pattern for transaction consistency
     */
    public static ArchRule noDirectJmsTemplateUsage() {
        return noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.jms.core.JmsTemplate")
            .because("Red Line 13: Direct JmsTemplate usage prohibited, must use EventBusCapability.publish()")
            .allowEmptyShould(true);
    }

    /**
     * Prohibit plugins from depending on Outbox or Relay implementation packages.
     *
     * <p>Manifest reliability is an L2B startup policy. Plugin code may publish
     * facts through EventBusCapability, but must not import repositories,
     * relay workers, persistence mappers, or adapter-owned outbox types.</p>
     */
    public static ArchRule noDirectOutboxImplementationDependency() {
        return noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "io.infra.adapter.outbox..",
                "io.runtime.orchestrator.outbox..",
                "io.runtime.orchestrator.event..")
            .because("A-10/M1: plugins must not depend on Outbox, Relay, dispatcher, or adapter implementation")
            .allowEmptyShould(true);
    }

    /**
     * Prohibit plugins from depending on broker SDK packages.
     */
    public static ArchRule noBrokerSdkDependencies() {
        return noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.apache.kafka..",
                "org.springframework.kafka..",
                "com.rabbitmq.client..",
                "org.springframework.amqp.rabbit..",
                "org.springframework.cloud.stream..",
                "jakarta.jms..",
                "javax.jms..")
            .because("A-10/M1: plugins must publish through EventBusCapability and never import broker SDKs")
            .allowEmptyShould(true);
    }

    /**
     * Prohibit the retired Kafka Outbox AOP/global-table implementation from
     * re-entering the production classpath.
     */
    public static ArchRule noLegacyL2cOutboxSsoTTypes() {
        return noClasses()
            .that(LEGACY_OUTBOX_SSOT_TYPES)
            .should().resideInAnyPackage("..")
            .because("M6 retires the Kafka AOP Outbox implementation and global table production path")
            .allowEmptyShould(true);
    }

    /**
     * Validate all Outbox rules
     */
    public static void check(JavaClasses classes) {
        noDirectKafkaTemplateUsage().check(classes);
        noDirectRabbitTemplateUsage().check(classes);
        noDirectKafkaProducerUsage().check(classes);
        noDirectRabbitConnectionUsage().check(classes);
        noDirectStreamBridgeUsage().check(classes);
        noDirectJmsTemplateUsage().check(classes);
        noDirectOutboxImplementationDependency().check(classes);
        noBrokerSdkDependencies().check(classes);
        noLegacyL2cOutboxSsoTTypes().check(classes);
    }
}
