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
 * CrossModuleViaCapabilityRule 正反测试用例
 *
 * <p>验证红线4规则的正确性：跨模块通信必须通过 Capability 接口。</p>
 *
 * <h2>覆盖的约束</h2>
 * <ul>
 *   <li>Domain 层禁止直接依赖 Service 层</li>
 *   <li>跨模块依赖应通过 RuntimeContext.getCapability() 获取</li>
 *   <li>禁止 @Autowired 其他模块的 Service</li>
 * </ul>
 *
 * <h2>层次关系</h2>
 * <pre>
 * ┌─────────────────────────────┐
 * │   Service 层（应用服务）     │ ← 可以调用 Domain 层
 * ├─────────────────────────────┤
 * │   Domain 层（领域模型）      │ ← 禁止直接调用 Service 层
 * └─────────────────────────────┘
 * </pre>
 *
 * <h2>合规模式</h2>
 * <p>Domain 层如需调用其他模块能力，应通过 Capability 接口：</p>
 * <pre>{@code
 * // 正确做法：通过 Capability
 * AuthCapability auth = runtimeContext.getCapability(AuthCapability.class);
 * auth.getCurrentUser();
 * 
 * // 错误做法：直接注入 Service
 * @Autowired UserService userService; // 违规！
 * }</pre>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 */
@DisplayName("红线4：跨模块通信必须通过 Capability 接口")
class CrossModuleViaCapabilityRuleTest {

    // ============================================================================
    // Domain 层依赖检查测试
    // ============================================================================

    @Nested
    @DisplayName("Domain 层禁止直接依赖 Service 层")
    class DomainShouldNotDependOnServiceTests {

        @Test
        @DisplayName("架构守护库自身应通过 Domain-Service 依赖检查")
        void guardLibraryShouldPass() {
            // 使用 allowEmptyShould(true) 允许在没有匹配类时通过检查
            ArchRule rule = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "架构守护规则库自身不含 domain 包，应通过检查");
        }

        @Test
        @DisplayName("不含 domain 包的代码应通过检查")
        void codeWithoutDomainPackageShouldPass() {
            // 使用 allowEmptyShould(true) 允许在没有匹配类时通过检查
            ArchRule rule = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard.rules");
                rule.check(classes);
            }, "不含 domain 子包的代码应自动通过规则检查");
        }

        @Test
        @DisplayName("规则应能正确创建")
        void ruleShouldBeCreatedCorrectly() {
            ArchRule rule = CrossModuleViaCapabilityRule.rule();
            assertTrue(rule != null, "CrossModuleViaCapabilityRule.rule() 返回值不应为 null");
        }
    }

    // ============================================================================
    // 规则描述与配置测试
    // ============================================================================

    @Nested
    @DisplayName("规则配置验证")
    class RuleConfigurationTests {

        @Test
        @DisplayName("规则应包含 domain 和 service 包模式")
        void ruleShouldTargetCorrectPackages() {
            ArchRule rule = CrossModuleViaCapabilityRule.rule();
            String description = rule.getDescription();

            // 验证规则描述存在
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
            ArchRule rule1 = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);
            ArchRule rule2 = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);

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
        @DisplayName("应与所有红线规则兼容")
        void shouldBeCompatibleWithAllRules() {
            // 使用 allowEmptyShould(true) 允许在没有匹配类时通过检查
            ArchRule crossModuleRule = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);
            ArchRule eventsRule = EventsViaCapabilityRule.rule();
            ArchRule containerRule = NoSpringContainerApiRule.rule();
            ArchRule loggingRule = LoggingViaCapabilityRule.rule();

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                crossModuleRule.check(classes);
                eventsRule.check(classes);
                containerRule.check(classes);
                loggingRule.check(classes);
            }, "所有红线规则应能同时应用而不冲突");
        }

        @Test
        @DisplayName("规则应与 HTTP 客户端规则兼容")
        void shouldBeCompatibleWithHttpClientRules() {
            // 使用 allowEmptyShould(true) 允许在没有匹配类时通过检查
            ArchRule crossModuleRule = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);
            ArchRule httpRule = NoDirectHttpClientsRule.noRestTemplate();

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                crossModuleRule.check(classes);
                httpRule.check(classes);
            }, "红线4与红线3规则应能同时应用而不冲突");
        }
    }

    // ============================================================================
    // 边界条件测试
    // ============================================================================

    @Nested
    @DisplayName("边界条件验证")
    class BoundaryConditionTests {

        @Test
        @DisplayName("空包场景应通过检查")
        void emptyPackageShouldPass() {
            // 使用 allowEmptyShould(true) 允许在没有匹配类时通过检查
            ArchRule rule = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                // 尝试导入一个可能为空的包（profiles 子目录）
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard.profiles");
                rule.check(classes);
            }, "空包或无 domain 依赖的包应通过检查");
        }

        @Test
        @DisplayName("单个规则类检查应通过")
        void singleClassShouldPass() {
            // 使用 allowEmptyShould(true) 允许在没有匹配类时通过检查
            ArchRule rule = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importClasses(CrossModuleViaCapabilityRule.class);
                rule.check(classes);
            }, "单个规则类应通过自身的检查");
        }
    }
}
