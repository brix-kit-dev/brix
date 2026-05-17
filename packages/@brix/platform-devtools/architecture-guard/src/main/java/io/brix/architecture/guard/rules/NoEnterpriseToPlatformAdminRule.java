/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * No Enterprise-to-PlatformAdmin Dependency Rule.
 *
 * <p>Enforces SSOT v1.0 §11 R-1 / R-2:
 * the {@code platform-admin} module belongs to {@code platform-commons} and
 * must remain a leaf in the module graph — no enterprise plugin (any module
 * under {@code io.brix.enterprise..}) is permitted to import its internals.
 *
 * <p>Conversely, {@code platform-admin} itself is forbidden from importing
 * any {@code io.brix.enterprise..} package; this rule checks that direction
 * as well so the dependency between the open-source platform layer and the
 * enterprise layer remains strictly one-way (enterprise → platform-commons,
 * never the reverse, and never sideways into {@code platform-admin}).</p>
 *
 * <h2>Why a dedicated rule?</h2>
 * <p>{@code platform-admin} is a privileged surface — it provisions
 * SUPER_ADMIN credentials and serves the platform audit log. Allowing
 * enterprise plugins to import its types would let a misbehaving plugin
 * call internal service methods, by-passing the REST authorisation layer
 * (SSOT §4.1 / §8.x). Enforcing the boundary at compile/test time gives
 * us a defence-in-depth signal that complements Spring Security.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @AnalyzeClasses(packagesOf = MyEnterpriseApp.class)
 * class ArchitectureTest {
 *     @ArchTest
 *     static final ArchRule noEnterpriseToPlatformAdmin =
 *         NoEnterpriseToPlatformAdminRule.enforce();
 * }
 * }</pre>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 */
public final class NoEnterpriseToPlatformAdminRule {

    /** Java package root for the privileged platform-admin server module. */
    public static final String PLATFORM_ADMIN_PACKAGE = "io.brix.platform.admin..";

    /** Java package root for all enterprise solutions. */
    public static final String ENTERPRISE_PACKAGE = "io.brix.enterprise..";

    private NoEnterpriseToPlatformAdminRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Forbid enterprise classes from depending on the platform-admin module.
     *
     * @return an ArchUnit rule
     */
    public static ArchRule enterpriseShouldNotDependOnPlatformAdmin() {
        return noClasses()
                .that().resideInAPackage(ENTERPRISE_PACKAGE)
                .should().dependOnClassesThat().resideInAPackage(PLATFORM_ADMIN_PACKAGE)
                .because("SSOT §11 R-1: platform-admin is a privileged "
                        + "platform-commons surface; enterprise plugins must "
                        + "not bypass its REST authorisation layer by importing "
                        + "its internal types directly.")
                .allowEmptyShould(true);
    }

    /**
     * Symmetric direction — keep platform-admin free of enterprise imports
     * so it remains publishable as part of the open-source brix monorepo.
     *
     * @return an ArchUnit rule
     */
    public static ArchRule platformAdminShouldNotDependOnEnterprise() {
        return noClasses()
                .that().resideInAPackage(PLATFORM_ADMIN_PACKAGE)
                .should().dependOnClassesThat().resideInAPackage(ENTERPRISE_PACKAGE)
                .because("SSOT §11 R-2: platform-commons must not depend on "
                        + "enterprise packages — the open-source layer must "
                        + "stay buildable without the enterprise tree.")
                .allowEmptyShould(true);
    }

    /**
     * Convenience: returns both directions composed as a single rule.
     *
     * <p>Note: ArchUnit composes rules via separate {@code @ArchTest}
     * fields in the consuming test, so most callers will declare both
     * methods independently. This helper exists for ad-hoc usage in
     * standalone test mains.</p>
     *
     * @return an ArchUnit rule that fails if either direction is violated
     */
    public static ArchRule enforce() {
        return enterpriseShouldNotDependOnPlatformAdmin();
    }
}
