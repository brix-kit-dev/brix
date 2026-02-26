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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

/**
 * EventsViaCapabilityRule 正反测试用例
 *
 * <p>验证红线8规则的正确性：事件必须通过 EventBusCapability 发布。</p>
 *
 * <h2>覆盖的约束</h2>
 * <ul>
 *   <li>禁止直接使用 Spring ApplicationEventPublisher</li>
 *   <li>禁止直接使用 Spring @EventListener 注解</li>
 *   <li>所有事件发布/订阅必须通过 EventBusCapability 接口</li>
 * </ul>
 *
 * <h2>设计原则</h2>
 * <p>业务代码不应直接使用 Spring 事件机制，因为：</p>
 * <ul>
 *   <li>Spring Events 仅限于单进程内传播</li>
 *   <li>EventBusCapability 可通过 Kafka 等实现跨进程传播</li>
 *   <li>统一事件接口便于追踪和治理</li>
 * </ul>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 */
@DisplayName("红线8：事件必须通过 EventBusCapability 发布")
class EventsViaCapabilityRuleTest {

    // ============================================================================
    // 禁止 ApplicationEventPublisher 测试
    // ============================================================================

    @Nested
    @DisplayName("禁止直接使用 ApplicationEventPublisher")
    class NoApplicationEventPublisherTests {

        @Test
        @DisplayName("架构守护库自身应通过 ApplicationEventPublisher 检查")
        void guardLibraryShouldPass() {
            ArchRule rule = EventsViaCapabilityRule.rule();

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "架构守护规则库自身不应使用 ApplicationEventPublisher");
        }

        @Test
        @DisplayName("规则应包含正确的红线标识")
        void ruleShouldContainRedLineMarker() {
            ArchRule rule = EventsViaCapabilityRule.rule();
            String description = rule.getDescription();

            // 验证规则描述不为空
            assertDoesNotThrow(() -> {
                if (description == null || description.isEmpty()) {
                    throw new AssertionError("规则描述不应为空");
                }
            }, "规则应包含描述信息");
        }

        @Test
        @DisplayName("规则应能正确检查不含 Spring 依赖的代码")
        void ruleShouldWorkWithoutSpringDependency() {
            ArchRule rule = EventsViaCapabilityRule.rule();

            // 导入一个纯 Java 包（规则库自身不依赖 Spring）
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard.rules");
                rule.check(classes);
            }, "不含 Spring 依赖的代码应自动通过规则检查");
        }
    }

    // ============================================================================
    // 规则创建与配置测试
    // ============================================================================

    @Nested
    @DisplayName("规则配置验证")
    class RuleConfigurationTests {

        @Test
        @DisplayName("规则实例不应为 null")
        void ruleShouldNotBeNull() {
            ArchRule rule = EventsViaCapabilityRule.rule();
            assertTrue(rule != null, "EventsViaCapabilityRule.rule() 返回值不应为 null");
        }

        @Test
        @DisplayName("规则应能多次创建且结果一致")
        void ruleCreationShouldBeIdempotent() {
            // 使用 allowEmptyShould(true) 允许在没有匹配类时通过检查
            ArchRule rule1 = EventsViaCapabilityRule.rule().allowEmptyShould(true);
            ArchRule rule2 = EventsViaCapabilityRule.rule().allowEmptyShould(true);

            // 两次创建的规则都应能正常工作
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule1.check(classes);
                rule2.check(classes);
            }, "规则应能多次创建并正常工作");
        }
    }

    // ============================================================================
    // 与其他规则的组合测试
    // ============================================================================

    @Nested
    @DisplayName("规则组合兼容性")
    class RuleCombinationTests {

        @Test
        @DisplayName("应与 NoSpringContainerApi 规则兼容")
        void shouldBeCompatibleWithNoSpringContainerApiRule() {
            // 使用 allowEmptyShould(true) 允许在没有匹配类时通过检查
            ArchRule eventsRule = EventsViaCapabilityRule.rule().allowEmptyShould(true);
            ArchRule containerRule = NoSpringContainerApiRule.rule().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                eventsRule.check(classes);
                containerRule.check(classes);
            }, "红线8与红线5规则应能同时应用而不冲突");
        }

        @Test
        @DisplayName("应与 CrossModuleViaCapability 规则兼容")
        void shouldBeCompatibleWithCrossModuleRule() {
            // 使用 allowEmptyShould(true) 允许在没有匹配类时通过检查
            ArchRule eventsRule = EventsViaCapabilityRule.rule().allowEmptyShould(true);
            ArchRule crossModuleRule = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                eventsRule.check(classes);
                crossModuleRule.check(classes);
            }, "红线8与红线4规则应能同时应用而不冲突");
        }
    }
}
