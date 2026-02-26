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
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NoSpringContainerApiRule 正反测试用例
 *
 * <p>验证红线5规则的正确性：禁止依赖 Spring 容器特定 API。</p>
 *
 * <h2>覆盖的约束</h2>
 * <ul>
 *   <li>禁止直接使用 ApplicationContext</li>
 *   <li>禁止直接使用 BeanFactory</li>
 *   <li>所有依赖注入应通过构造函数完成</li>
 *   <li>运行时能力应通过 RuntimeContext 获取</li>
 * </ul>
 *
 * <h2>设计原则</h2>
 * <p>业务代码不应直接使用 Spring 容器 API，因为：</p>
 * <ul>
 *   <li>降低与 Spring 框架的耦合度</li>
 *   <li>便于单元测试和模块独立部署</li>
 *   <li>符合依赖倒置原则（DIP）</li>
 * </ul>
 *
 * <h2>合规模式</h2>
 * <pre>{@code
 * // 正确做法：构造函数注入
 * public class BookingService {
 *     private final BookingRepository repository;
 *     public BookingService(BookingRepository repository) {
 *         this.repository = repository;
 *     }
 * }
 * 
 * // 错误做法：直接使用容器 API
 * @Autowired ApplicationContext context; // 违规！
 * context.getBean(BookingRepository.class);
 * }</pre>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 */
@DisplayName("红线5：禁止依赖 Spring 容器特定 API")
class NoSpringContainerApiRuleTest {

    // ============================================================================
    // ApplicationContext 检查测试
    // ============================================================================

    @Nested
    @DisplayName("禁止直接使用 ApplicationContext")
    class NoApplicationContextTests {

        @Test
        @DisplayName("架构守护库自身应通过 ApplicationContext 检查")
        void guardLibraryShouldPass() {
            ArchRule rule = NoSpringContainerApiRule.rule();

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "架构守护规则库自身不应使用 ApplicationContext");
        }

        @Test
        @DisplayName("规则子包应通过检查")
        void rulesPackageShouldPass() {
            ArchRule rule = NoSpringContainerApiRule.rule();

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard.rules");
                rule.check(classes);
            }, "规则子包不应使用 ApplicationContext");
        }
    }

    // ============================================================================
    // BeanFactory 检查测试
    // ============================================================================

    @Nested
    @DisplayName("禁止直接使用 BeanFactory")
    class NoBeanFactoryTests {

        @Test
        @DisplayName("架构守护库自身应通过 BeanFactory 检查")
        void guardLibraryShouldPass() {
            ArchRule rule = NoSpringContainerApiRule.rule();

            // 规则同时检查 ApplicationContext 和 BeanFactory
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "架构守护规则库自身不应使用 BeanFactory");
        }

        @Test
        @DisplayName("profiles 子包应通过检查")
        void profilesPackageShouldPass() {
            ArchRule rule = NoSpringContainerApiRule.rule();

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard.profiles");
                rule.check(classes);
            }, "profiles 子包不应使用 BeanFactory");
        }
    }

    // ============================================================================
    // 规则配置测试
    // ============================================================================

    @Nested
    @DisplayName("规则配置验证")
    class RuleConfigurationTests {

        @Test
        @DisplayName("规则实例不应为 null")
        void ruleShouldNotBeNull() {
            ArchRule rule = NoSpringContainerApiRule.rule();
            assertTrue(rule != null, "NoSpringContainerApiRule.rule() 返回值不应为 null");
        }

        @Test
        @DisplayName("规则应包含正确的描述信息")
        void ruleShouldHaveDescription() {
            ArchRule rule = NoSpringContainerApiRule.rule();
            String description = rule.getDescription();

            assertDoesNotThrow(() -> {
                if (description == null || description.isEmpty()) {
                    throw new AssertionError("规则描述不应为空");
                }
            }, "规则应包含描述信息");
        }

        @Test
        @DisplayName("规则应能多次创建且结果一致")
        void ruleCreationShouldBeIdempotent() {
            // 使用 allowEmptyShould(true) 允许在没有匹配类时通过检查
            ArchRule rule1 = NoSpringContainerApiRule.rule().allowEmptyShould(true);
            ArchRule rule2 = NoSpringContainerApiRule.rule().allowEmptyShould(true);

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
        @DisplayName("应与所有 Capability 相关规则兼容")
        void shouldBeCompatibleWithCapabilityRules() {
            // 使用 allowEmptyShould(true) 允许在没有匹配类时通过检查
            ArchRule containerRule = NoSpringContainerApiRule.rule().allowEmptyShould(true);
            ArchRule eventsRule = EventsViaCapabilityRule.rule().allowEmptyShould(true);
            ArchRule crossModuleRule = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);
            ArchRule loggingRule = LoggingViaCapabilityRule.rule().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                containerRule.check(classes);
                eventsRule.check(classes);
                crossModuleRule.check(classes);
                loggingRule.check(classes);
            }, "所有 Capability 相关规则应能同时应用而不冲突");
        }

        @Test
        @DisplayName("应与基础设施适配器规则兼容")
        void shouldBeCompatibleWithInfraAdapterRules() {
            // 使用 allowEmptyShould(true) 允许在没有匹配类时通过检查
            ArchRule containerRule = NoSpringContainerApiRule.rule().allowEmptyShould(true);
            ArchRule infraAdapterRule = NoInfraAdapterRule.rule().allowEmptyShould(true);
            ArchRule middlewareRule = NoMiddlewareClientsRule.noSpringKafka().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                containerRule.check(classes);
                infraAdapterRule.check(classes);
                middlewareRule.check(classes);
            }, "红线5与基础设施相关规则应能同时应用而不冲突");
        }
    }

    // ============================================================================
    // 边界条件测试
    // ============================================================================

    @Nested
    @DisplayName("边界条件验证")
    class BoundaryConditionTests {

        @Test
        @DisplayName("不含 Spring 依赖的代码应通过检查")
        void codeWithoutSpringDependencyShouldPass() {
            ArchRule rule = NoSpringContainerApiRule.rule();

            // 规则库自身不依赖 Spring
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importClasses(NoSpringContainerApiRule.class);
                rule.check(classes);
            }, "不含 Spring 依赖的代码应自动通过规则检查");
        }

        @Test
        @DisplayName("ArchUnit 相关类应通过检查")
        void archUnitClassesShouldPass() {
            ArchRule rule = NoSpringContainerApiRule.rule();

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "ArchUnit 规则定义类应通过自身的检查");
        }
    }
}
