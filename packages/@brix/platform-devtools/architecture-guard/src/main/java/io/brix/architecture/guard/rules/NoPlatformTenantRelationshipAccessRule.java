package io.brix.architecture.guard.rules;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guards tenant relationship tables behind runtime capability contracts.
 *
 * <p>Business plugins must not import platform tenant member/principal entities,
 * repositories, mappers, or validators directly. Those tables define access
 * context, so plugin code must validate references through tenant capabilities
 * instead of coupling to platform-tenant implementation types.</p>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 */
public final class NoPlatformTenantRelationshipAccessRule {

    private static final String[] PLUGIN_PACKAGES = {
            "io.brix.app..",
            "io.brix.enterprise.app.."
    };

    private static final String[] PLATFORM_TENANT_INTERNAL_PACKAGES = {
            "io.brix.platform.tenant.entity..",
            "io.brix.platform.tenant.repository..",
            "io.brix.platform.tenant.mapper..",
            "io.brix.platform.tenant.validation.."
    };

    private NoPlatformTenantRelationshipAccessRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Forbids plugin code from depending on platform tenant relationship internals.
     *
     * @return an ArchUnit rule for plugin and plugin-core profiles
     */
    public static ArchRule rule() {
        return noClasses()
                .that().resideInAnyPackage(PLUGIN_PACKAGES)
                .should().dependOnClassesThat()
                .resideInAnyPackage(PLATFORM_TENANT_INTERNAL_PACKAGES)
                .because("v3.1.3 T-23/T-24: plugins must not directly access "
                        + "sys_tenant_member/sys_tenant_principal implementation types. "
                        + "Use TenantDirectoryCapability or another runtime capability contract.")
                .allowEmptyShould(true);
    }
}
