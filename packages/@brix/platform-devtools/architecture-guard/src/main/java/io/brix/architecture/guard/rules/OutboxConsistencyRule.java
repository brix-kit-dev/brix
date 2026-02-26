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
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 红线 13：跨服务事件一致性（Transactional Outbox）
 * 
 * <p><b>核心目标</b>：禁止插件绕过 {@code EventBusCapability} 直接操作消息中间件。
 * 
 * <p>确保插件代码不会绕过统一入口直接发送消息，防止以下问题：
 * <ul>
 *   <li>双写问题：业务数据写入成功但消息发送失败，或反之</li>
 *   <li>事件丢失：使用 @TransactionalEventListener + @Async 可能在事务提交前异步执行</li>
 *   <li>消息重复/丢失：直接 new KafkaProducer() 没有可靠投递保证</li>
 * </ul>
 * 
 * <p>正确做法：
 * <ul>
 *   <li>通过 EventBusCapability.publish() 发布事件</li>
 *   <li>由 Host 层根据事件注解/配置决定是否走 Outbox</li>
 * </ul>
 * 
 * <p><b>事件可靠性分类</b>（Outbox 不是所有场景都必须）：
 * <table border="1">
 *   <tr><th>类型</th><th>示例</th><th>丢失后果</th><th>发送策略</th></tr>
 *   <tr><td>关键事件</td><td>订单支付、合同签署、库存扣减</td><td>业务不一致/资损</td><td>必须 Outbox</td></tr>
 *   <tr><td>非关键事件</td><td>欢迎邮件、营销推送、日志上报</td><td>体验略差可补发</td><td>Best-effort</td></tr>
 * </table>
 * 
 * <p>来源：Netflix / Uber Outbox Pattern / CAP 定理最终一致性
 * 
 * @since 1.0.0
 */
public class OutboxConsistencyRule {

    private OutboxConsistencyRule() {
        // Utility class
    }

    /**
     * 禁止插件直接使用 KafkaTemplate
     * 
     * <p>插件必须通过 EventBusCapability.publish() 发布事件，
     * 由 Runtime Shell 统一走 Outbox 模式保证事务一致性
     */
    public static ArchRule noDirectKafkaTemplateUsage() {
        return noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.kafka.core.KafkaTemplate")
            .because("红线 13：禁止直接调用 KafkaTemplate，必须通过 EventBusCapability.publish()")
            .allowEmptyShould(true);
    }

    /**
     * 禁止插件直接使用 RabbitTemplate
     * 
     * <p>RabbitMQ 同样需要走 Outbox 模式保证事务一致性
     */
    public static ArchRule noDirectRabbitTemplateUsage() {
        return noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.amqp.rabbit.core.RabbitTemplate")
            .because("红线 13：禁止直接调用 RabbitTemplate，必须通过 EventBusCapability.publish()")
            .allowEmptyShould(true);
    }

    /**
     * 禁止插件直接创建 KafkaProducer
     * 
     * <p>直接 new KafkaProducer() 绕过了所有框架层面的保护
     */
    public static ArchRule noDirectKafkaProducerUsage() {
        return noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.apache.kafka.clients.producer.KafkaProducer")
            .because("红线 13：禁止直接 new KafkaProducer()，必须通过 EventBusCapability.publish()")
            .allowEmptyShould(true);
    }

    /**
     * 禁止插件直接创建 RabbitMQ ConnectionFactory
     * 
     * <p>直接创建连接绕过了 Outbox 模式
     */
    public static ArchRule noDirectRabbitConnectionUsage() {
        return noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("com.rabbitmq.client.ConnectionFactory")
            .because("红线 13：禁止直接创建 RabbitMQ 连接，必须通过 EventBusCapability")
            .allowEmptyShould(true);
    }

    /**
     * 禁止 StreamBridge 直接发送（Spring Cloud Stream）
     * 
     * <p>StreamBridge 同样需要走 Outbox 模式
     */
    public static ArchRule noDirectStreamBridgeUsage() {
        return noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.cloud.stream.function.StreamBridge")
            .because("红线 13：禁止直接使用 StreamBridge，必须通过 EventBusCapability.publish()")
            .allowEmptyShould(true);
    }

    /**
     * 禁止 JmsTemplate 直接发送
     * 
     * <p>JMS 消息同样需要走 Outbox 模式
     */
    public static ArchRule noDirectJmsTemplateUsage() {
        return noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.jms.core.JmsTemplate")
            .because("红线 13：禁止直接使用 JmsTemplate，必须通过 EventBusCapability.publish()")
            .allowEmptyShould(true);
    }

    /**
     * 验证所有 Outbox 规则
     */
    public static void check(JavaClasses classes) {
        noDirectKafkaTemplateUsage().check(classes);
        noDirectRabbitTemplateUsage().check(classes);
        noDirectKafkaProducerUsage().check(classes);
        noDirectRabbitConnectionUsage().check(classes);
        noDirectStreamBridgeUsage().check(classes);
        noDirectJmsTemplateUsage().check(classes);
    }
}
