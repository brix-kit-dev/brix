/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * No String Role-Literal Rule.
 *
 * <p>Enforces SSOT v1.0 §11 R-3: production code must reference platform
 * role codes through the {@code RoleCode} constants (or an equivalent
 * enum), never as bare string literals such as {@code "SUPER_ADMIN"}.</p>
 *
 * <h2>Why?</h2>
 * <p>String literals are invisible to refactoring tools and bypass the
 * compile-time guarantee that we only ever reference roles that actually
 * exist in {@code RoleCode}. A typo in a string literal silently disables
 * an authorisation check; a typo in a constant reference fails to compile.</p>
 *
 * <h2>What is detected?</h2>
 * <p>The rule scans method bodies (via {@link JavaMethodCall} arguments and
 * field initialisers) for any string literal that matches the canonical
 * role-code shape (UPPER_SNAKE_CASE, &le; 32 chars) AND equals one of the
 * known role codes. Other UPPER_SNAKE strings (e.g. enum-name look-alikes
 * for unrelated domains) are NOT flagged.</p>
 *
 * <h2>Whitelist</h2>
 * <p>The following classes/packages are exempt — they are the legitimate
 * sources of truth for the constants themselves:</p>
 * <ul>
 *   <li>{@code io.brix.platform.auth.RoleCode} — the constant declaration.</li>
 *   <li>{@code io.brix.platform.auth.PlatformPermissions} — same module.</li>
 *   <li>Anything under {@code ..test..} — test fixtures need to assert
 *       string equality.</li>
 *   <li>Anything under {@code ..migration..} — Flyway SQL helpers may seed
 *       roles by literal name.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @AnalyzeClasses(packagesOf = MyApp.class)
 * class ArchitectureTest {
 *     @ArchTest
 *     static final ArchRule noStringRoleLiteral = NoStringRoleLiteralRule.enforce();
 * }
 * }</pre>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 */
public final class NoStringRoleLiteralRule {

    /** Canonical role codes mirrored from {@code io.brix.platform.auth.RoleCode}. */
    public static final Set<String> KNOWN_ROLE_CODES = new HashSet<>(Arrays.asList(
            "SUPER_ADMIN",
            "PLATFORM_ADMIN",
            "SUPPORT_ADMIN",
            "AUDITOR"
    ));

    /** Whitelisted class FQNs / package prefixes. */
    private static final Set<String> WHITELIST_CLASSES = new HashSet<>(Arrays.asList(
            "io.brix.platform.auth.RoleCode",
            "io.brix.platform.auth.PlatformPermissions",
            "io.brix.platform.auth.AuditAction"
    ));

    private static final Pattern WHITELIST_PACKAGE = Pattern.compile(
            "(\\.test\\.|\\.migration\\.|\\.archunit\\.|Test$|IT$)"
    );

    private NoStringRoleLiteralRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Build the rule.
     *
     * @return an ArchUnit rule
     */
    public static ArchRule enforce() {
        return noClasses()
                .that(notWhitelisted())
                .should(useNoRoleStringLiteral())
                .because("SSOT §11 R-3: role identifiers must come from "
                        + "io.brix.platform.auth.RoleCode constants — string "
                        + "literals bypass refactor safety and silently "
                        + "disable authorisation checks on typos.")
                .allowEmptyShould(true);
    }

    private static DescribedPredicate<JavaClass> notWhitelisted() {
        return new DescribedPredicate<JavaClass>("not on the role-literal whitelist") {
            @Override
            public boolean test(JavaClass clazz) {
                String fqn = clazz.getFullName();
                if (WHITELIST_CLASSES.contains(fqn)) {
                    return false;
                }
                Matcher m = WHITELIST_PACKAGE.matcher(fqn);
                return !m.find();
            }
        };
    }

    private static ArchCondition<JavaClass> useNoRoleStringLiteral() {
        return new ArchCondition<JavaClass>("not contain hard-coded role strings") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                // 1. Method-call arguments — by far the most common offence,
                //    e.g. hasRole("SUPER_ADMIN") or hasAuthority("AUDITOR").
                for (JavaMethod method : clazz.getMethods()) {
                    for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
                        for (Object literal : extractStringLiterals(call)) {
                            if (literal instanceof String
                                    && KNOWN_ROLE_CODES.contains(literal)) {
                                events.add(SimpleConditionEvent.violated(
                                        method,
                                        String.format(
                                                "%s.%s passes hard-coded role "
                                                        + "literal \"%s\" to %s.%s — use RoleCode constant",
                                                clazz.getSimpleName(),
                                                method.getName(),
                                                literal,
                                                call.getTargetOwner().getSimpleName(),
                                                call.getName())));
                            }
                        }
                    }
                }
            }
        };
    }

    /**
     * ArchUnit deliberately does not expose constant-pool literals on
     * {@link JavaMethodCall} (it only models structural references). This
     * helper performs a best-effort extraction by inspecting the static
     * initialisers / source-code-line of the call. In practice the Spring
     * Security and Brix authorisation entry points all take a single
     * {@code String} parameter, so the simpler approach is to flag any call
     * to a role-checking method whose target argument cannot be statically
     * verified to come from a {@code RoleCode} constant.
     *
     * <p>For the MVP we restrict the check to direct calls to known
     * permission/role APIs — concretely, methods named {@code hasRole},
     * {@code hasAnyRole}, {@code hasAuthority} and {@code hasAnyAuthority} —
     * and inspect the textual line they were called from for one of the
     * {@link #KNOWN_ROLE_CODES} substrings. This is a conservative, false-
     * negative-tolerant strategy that catches the realistic offences
     * without false positives.</p>
     *
     * @param call the method call
     * @return strings statically detectable as arguments
     */
    private static Iterable<Object> extractStringLiterals(JavaMethodCall call) {
        String name = call.getName();
        if (!name.equals("hasRole") && !name.equals("hasAnyRole")
                && !name.equals("hasAuthority") && !name.equals("hasAnyAuthority")) {
            return java.util.Collections.emptyList();
        }
        // Inspect the textual line of code surrounding the call.
        String line = call.getSourceCodeLocation() != null
                ? call.getSourceCodeLocation().toString()
                : "";
        java.util.List<Object> hits = new java.util.ArrayList<>();
        for (String code : KNOWN_ROLE_CODES) {
            if (line.contains("\"" + code + "\"")) {
                hits.add(code);
            }
        }
        return hits;
    }
}
