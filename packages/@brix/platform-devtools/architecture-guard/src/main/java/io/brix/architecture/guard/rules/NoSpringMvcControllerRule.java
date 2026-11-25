/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import java.util.Set;

/**
 * No Spring MVC controllers in plugin code: the runtime's dispatcher publishes every plugin endpoint from the manifest,
 * and a plugin has no Spring MVC publishing path of its own (root BRX-RS-MANIFEST-016, MANIFEST-021).
 *
 * <p>The rule checks every class the module's test imports, whatever its package, and fails when the test imports none.
 * It replaces {@code NoRestControllerInCoreRule}, which looked only at packages named {@code core} (standard-link plan
 * queue item 21, 21b, D173).</p>
 *
 * <p>A module lists the controllers it had when the rule reached it, one fully qualified name per line, in
 * {@value #KNOWN_CONTROLLERS} of its test resources (see {@link KnownViolations}); the lists only shrink, as their
 * endpoints move to the runtime (21e, D178).</p>
 *
 * <h2>Violation Example</h2>
 * <pre>{@code
 * package io.brix.samples.orders.server;
 *
 * @RestController  // VIOLATION: an endpoint handler, declared under endpoints.provides
 * public class OrderController { }
 * }</pre>
 *
 * @author Brix Architecture Team
 * @since 3.3.0
 */
public final class NoSpringMvcControllerRule {

    /** Where a module lists its Spring MVC controllers from before the rule reached it. */
    public static final String KNOWN_CONTROLLERS = "META-INF/brix/guard/spring-mvc-controllers.txt";

    private static final Set<String> CONTROLLER_ANNOTATIONS = Set.of(
            "org.springframework.web.bind.annotation.RestController", "org.springframework.stereotype.Controller");

    private NoSpringMvcControllerRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Refuses classes annotated with {@code @RestController} or {@code @Controller} that the module does not list.
     *
     * <p>A module may hold nothing but listed controllers, so the rule selects every class and its condition lets the
     * listed ones through. The condition reports each violation, so the rule is {@code classes().should(...)}: under
     * {@code noClasses()} ArchUnit inverts a condition's events.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule rule() {
        return classes()
                .should(new NotBeAnUnlistedController(
                        KnownViolations.read(KNOWN_CONTROLLERS, KnownViolations.classLoader())))
                .because("the runtime's dispatcher publishes every plugin endpoint (root BRX-RS-MANIFEST-016, " +
                        "MANIFEST-021): declare it under endpoints.provides and bind an endpoint handler. A controller " +
                        "from before is listed in " + KNOWN_CONTROLLERS + " of its module, and the lists only shrink");
    }

    private static final class NotBeAnUnlistedController extends ArchCondition<JavaClass> {

        private final Set<String> known;

        NotBeAnUnlistedController(Set<String> known) {
            super("not be a Spring MVC controller");
            this.known = known;
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            for (String annotation : CONTROLLER_ANNOTATIONS) {
                if (javaClass.isAnnotatedWith(annotation) && !KnownViolations.listed(javaClass.getName(), known)) {
                    events.add(SimpleConditionEvent.violated(javaClass, javaClass.getDescription()
                            + " is annotated with @" + annotation.substring(annotation.lastIndexOf('.') + 1)));
                }
            }
        }
    }
}
