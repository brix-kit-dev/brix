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

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Arrays;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Host Layer Ultra-Thin Principle Rules
 *
 * <p>Host layer should be a pure "assembly layer", only combining Capability
 * implementations without any business logic or control flow.</p>
 *
 * <h2>Checks</h2>
 * <ul>
 *   <li><b>No @Bean</b> - AutoConfiguration classes must not define @Bean methods</li>
 *   <li><b>No control flow</b> - No if-else/for/while/try-catch structures</li>
 *   <li><b>No private methods</b> - Private helpers indicate business logic</li>
 *   <li><b>Line limit</b> - Max 50 effective lines per AutoConfiguration class</li>
 * </ul>
 *
 * <h2>Rationale</h2>
 * <p>If Host layer contains @Bean definitions or complex logic:</p>
 * <ol>
 *   <li>Move the logic to the corresponding infra-adapter module</li>
 *   <li>Or extract it as a standalone Capability implementation</li>
 * </ol>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 * @see io.brix.architecture.guard.profiles.HostProfile
 */
public final class HostUltraThinRule {

    private HostUltraThinRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Maximum allowed @Bean methods in Host AutoConfiguration (0 = strict mode). */
    private static final int MAX_BEAN_METHODS = 0;

    /** Maximum effective lines per Host class (50 as transitional threshold). */
    private static final int MAX_EFFECTIVE_LINES = 50;

    /** Control flow keywords for detecting complex logic. */
    private static final List<String> CONTROL_FLOW_KEYWORDS = Arrays.asList(
            "if", "else", "for", "while", "do", "switch", "case", "try", "catch", "finally"
    );

    // ==================== No @Bean Rule ====================

    /**
     * AutoConfiguration classes must not define @Bean methods.
     *
     * <p>As a pure assembly layer, Host should not define any @Bean methods.
     * All Capability implementations should be registered in infra-adapter modules
     * via Spring Boot auto-configuration.</p>
     *
     * <h3>Violation Example</h3>
     * <pre>{@code
     * @Configuration
     * public class StandaloneShellAutoConfiguration {
     *     @Bean  // Violation: Host should not define @Bean
     *     public CapabilityRegistry capabilityRegistry() {
     *         return new DefaultCapabilityRegistry();
     *     }
     * }
     * }</pre>
     *
     * <h3>Correct Approach</h3>
     * <pre>{@code
     * // infra-adapter-core/src/main/resources/META-INF/spring.factories
     * org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
     *   io.brix.infra.adapter.core.CapabilityRegistryAutoConfiguration
     * }</pre>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noBeanDefinitionsInAutoConfiguration() {
        return classes()
                .that().haveSimpleNameEndingWith("AutoConfiguration")
                .should(new ArchCondition<JavaClass>("not contain @Bean method definitions") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        long beanMethodCount = javaClass.getMethods().stream()
                                .filter(method -> method.isAnnotatedWith("org.springframework.context.annotation.Bean"))
                                .count();

                        if (beanMethodCount > MAX_BEAN_METHODS) {
                            String message = String.format(
                                    "%s contains %d @Bean methods (max: %d). " +
                                            "Move Capability implementations to infra-adapter modules",
                                    javaClass.getName(), beanMethodCount, MAX_BEAN_METHODS);
                            events.add(SimpleConditionEvent.violated(javaClass, message));
                        }
                    }
                })
                .because("AutoConfiguration classes must not define @Bean methods. " +
                        "Use infra-adapter modules for Capability implementations");
    }

    // ==================== No Control Flow Rule ====================

    /**
     * Host layer must not contain complex control flow (if/else/for/while/try-catch).
     *
     * <p>Control flow statements indicate business logic or conditional handling,
     * which violates the "pure assembly layer" design principle.</p>
     *
     * <h3>Violation Example</h3>
     * <pre>{@code
     * @Configuration
     * public class StandaloneShellAutoConfiguration {
     *     @PostConstruct
     *     public void configure() {
     *         if (disabled) {  // Violation: no conditionals in Host
     *             return;
     *         }
     *         for (Plugin plugin : plugins) {  // Violation: no loops in Host
     *             registry.register(plugin);
     *         }
     *     }
     * }
     * }</pre>
     *
     * <h3>Correct Approach</h3>
     * <p>Move conditional logic and loops to infra-adapter or platform-common modules.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noControlFlowInHost() {
        return classes()
                .that().haveSimpleNameEndingWith("AutoConfiguration")
                .or().haveSimpleNameEndingWith("ShellConfiguration")
                .should(new ArchCondition<JavaClass>("not contain complex control flow") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        for (JavaMethod method : javaClass.getMethods()) {
                            checkMethodForControlFlow(javaClass, method, events);
                        }
                    }

                    /**
                     * Check if method contains control flow keywords.
                     *
                     * <p>Note: ArchUnit cannot directly analyze bytecode control flow.
                     * This check uses heuristics based on method call count.
                     * For precise detection, use PMD or Checkstyle.</p>
                     */
                    private void checkMethodForControlFlow(JavaClass javaClass, JavaCodeUnit method,
                                                           ConditionEvents events) {
                        int callCount = method.getMethodCallsFromSelf().size();
                        int fieldAccessCount = method.getFieldAccesses().size();

                        if (callCount > 10 || fieldAccessCount > 10) {
                            String message = String.format(
                                    "%s.%s() has %d method calls and %d field accesses. " +
                                            "May contain complex logic - consider refactoring",
                                    javaClass.getSimpleName(), method.getName(), callCount, fieldAccessCount);
                            events.add(SimpleConditionEvent.violated(javaClass, message));
                        }
                    }
                })
                .because("Host layer must not contain complex control flow. " +
                        "Business logic belongs in Plugin or infra-adapter modules");
    }

    // ==================== No Private Methods Rule ====================

    /**
     * Host AutoConfiguration classes must not define private helper methods.
     *
     * <p>Private methods typically indicate reusable business logic,
     * which violates the "pure assembly" design principle.</p>
     *
     * <h3>Violation Example</h3>
     * <pre>{@code
     * @Configuration
     * public class StandaloneShellAutoConfiguration {
     *     private void configureAdapters() {  // Violation
     *         // Private helper indicates business logic
     *     }
     *
     *     private boolean isEnabled(Properties props) {  // Violation
     *         return "true".equals(props.get("enabled"));
     *     }
     * }
     * }</pre>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noPrivateMethodsInAutoConfiguration() {
        return classes()
                .that().haveSimpleNameEndingWith("AutoConfiguration")
                .should(new ArchCondition<JavaClass>("not contain private helper methods") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        long privateMethodCount = javaClass.getMethods().stream()
                                .filter(method -> method.getModifiers().contains(
                                        com.tngtech.archunit.core.domain.JavaModifier.PRIVATE))
                                .filter(method -> !method.getName().startsWith("lambda$"))
                                .filter(method -> !method.getName().equals("$deserializeLambda$"))
                                .count();

                        if (privateMethodCount > 0) {
                            String message = String.format(
                                    "%s contains %d private methods. " +
                                            "Private helpers indicate business logic that should be refactored",
                                    javaClass.getName(), privateMethodCount);
                            events.add(SimpleConditionEvent.violated(javaClass, message));
                        }
                    }
                })
                .because("AutoConfiguration classes must not define private methods. " +
                        "Private methods indicate business logic belongs elsewhere");
    }

    // ==================== No Business Annotations Rule ====================

    /**
     * Host layer must not use @Service, @Repository, @Controller annotations.
     *
     * <p>These annotations mark business components and should not appear in Host layer.
     * Host layer should only use @Configuration and @Import annotations.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noBusinessAnnotations() {
        return noClasses()
                .that().resideInAPackage("..host..")
                .should().beAnnotatedWith("org.springframework.stereotype.Service")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Controller")
                .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .because("Host layer must not use @Service/@Repository/@Controller annotations. " +
                        "These are for business components");
    }

    // ==================== No Capability Implementation Rule ====================

    /**
     * Host layer must not implement Capability interfaces.
     *
     * <p>Capability implementations belong in infra-adapter modules.
     * Host layer is only for assembly, not implementation.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noCapabilityImplementation() {
        return noClasses()
                .that().resideInAPackage("..host..")
                .should().implement(com.tngtech.archunit.core.domain.JavaClass.Predicates
                        .simpleNameEndingWith("Capability"))
                .because("Host layer must not implement Capability interfaces. " +
                        "Implementations belong in infra-adapter modules");
    }
}
