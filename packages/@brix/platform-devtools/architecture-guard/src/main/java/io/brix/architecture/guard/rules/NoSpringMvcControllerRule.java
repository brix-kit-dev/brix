/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * No Spring MVC controllers in plugin code: the runtime's dispatcher publishes every plugin endpoint from the manifest,
 * and a plugin has no Spring MVC publishing path of its own (root BRX-RS-MANIFEST-016, MANIFEST-021).
 *
 * <p>The rule checks every class the module's test imports, whatever its package, and fails when the test imports none.
 * It replaces {@code NoRestControllerInCoreRule}, which looked only at packages named {@code core} (standard-link plan
 * queue item 21, 21b, D173).</p>
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

    private NoSpringMvcControllerRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Refuses classes annotated with {@code @RestController} or {@code @Controller}.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule rule() {
        return noClasses()
                .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Controller")
                .because("the runtime's dispatcher publishes every plugin endpoint (root BRX-RS-MANIFEST-016, " +
                        "MANIFEST-021): declare it under endpoints.provides and bind an endpoint handler");
    }
}
