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
 * Host 层架构约束规则集（增强版 — 覆盖更多红线）
 * 
 * <p>Host 层作为"超薄组装层"，拥有特殊的架构约束。根据 v3.0 运行壳架构设计蓝图，
 * Host 层允许直接使用基础设施适配器和 Spring 容器 API（红线1/5放宽），
 * 但必须遵守"超薄原则"和其他架构红线。</p>
 * 
 * <h2>放宽的规则</h2>
 * <ul>
 *   <li>红线1（基础设施适配器依赖）— Host 层需要直接组装适配器，允许 import</li>
 *   <li>红线5（Spring 容器 API）— Host 层需要 ApplicationContext 进行 Bean 注册，允许使用</li>
 * </ul>
 * 
 * <h2>严格执行的规则</h2>
 * <ul>
 *   <li>红线2 — 禁止直接使用中间件客户端（Kafka/Redis/AMQP 等）</li>
 *   <li>红线3 — 禁止直接使用 HTTP 客户端（RestTemplate/WebClient/OkHttp）</li>
 *   <li>红线6 — 禁止直接访问系统环境变量和配置（部分放宽：允许通过 Properties 注入）</li>
 *   <li>红线8 — 事件必须通过 EventBusCapability 发布</li>
 * </ul>
 * 
 * <h2>超薄原则（Ultra-Thin Host）</h2>
 * <ul>
 *   <li>AutoConfiguration 类禁止定义 @Bean 方法</li>
 *   <li>禁止复杂控制流（if/else/for/while/try-catch）</li>
 *   <li>禁止私有辅助方法</li>
 *   <li>禁止使用 @Service/@Repository/@Controller 等业务注解</li>
 *   <li>禁止实现 Capability 接口（应在 infra-adapter 中实现）</li>
 * </ul>
 * 
 * <h2>架构蓝图参考</h2>
 * <p>参见 v3.0 运行壳架构设计蓝图 第 9.1 节"Host 层职责边界"</p>
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
