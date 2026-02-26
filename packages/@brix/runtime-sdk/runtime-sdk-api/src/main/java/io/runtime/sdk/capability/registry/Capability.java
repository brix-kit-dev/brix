/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.capability.registry;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 能力标注注解
 * 
 * <p>用于标记一个 Bean 是某种 Capability 的实现，支持自动发现和注册。
 * 配合 Spring Boot AutoConfiguration 实现声明式能力组装。</p>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Capability(
 *     type = EventBusCapability.class,
 *     name = "kafka-eventbus",
 *     description = "Kafka-based EventBus implementation",
 *     level = CapabilityLevel.CORE,
 *     priority = 100
 * )
 * @Component
 * public class KafkaEventBus implements EventBusCapability {
 *     // 实现...
 * }
 * }</pre>
 * 
 * <h3>自动发现机制</h3>
 * <p>Host 启动时会扫描所有带有 @Capability 注解的 Bean，
 * 自动注册到 CapabilityRegistry 中。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see CapabilityRegistry
 * @see CapabilityLevel
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Capability {

    /**
     * 能力接口类型
     * 
     * <p>指定此实现对应的能力接口。如果不指定，
     * 将自动推断（取第一个实现的 *Capability 接口）。</p>
     * 
     * @return 能力接口类型
     */
    Class<?> type() default Void.class;

    /**
     * 能力名称
     * 
     * <p>用于日志、监控和配置引用的友好名称。</p>
     * 
     * @return 能力名称
     */
    String name() default "";

    /**
     * 能力描述
     * 
     * @return 能力描述信息
     */
    String description() default "";

    /**
     * 能力级别
     * 
     * @return 能力级别
     * @see CapabilityLevel
     */
    CapabilityLevel level() default CapabilityLevel.STANDARD;

    /**
     * 能力优先级
     * 
     * <p>当同一类型有多个实现时，优先级高的会被选中。
     * 数值越大优先级越高。</p>
     * 
     * @return 优先级，默认 0
     */
    int priority() default 0;

    /**
     * 是否为必需能力
     * 
     * <p>如果为 true，Host 启动时会验证此能力必须存在。</p>
     * 
     * @return 是否必需
     */
    boolean required() default false;

    /**
     * 能力别名
     * 
     * <p>可通过别名获取能力实例。</p>
     * 
     * @return 别名数组
     */
    String[] aliases() default {};
}
