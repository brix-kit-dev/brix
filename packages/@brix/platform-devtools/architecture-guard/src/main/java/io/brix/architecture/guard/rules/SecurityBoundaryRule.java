/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * Security Boundary Rules - Red Line 12.
 *
 * <p>Enforces security boundaries that plugins cannot bypass.</p>
 *
 * <h2>Architecture Principle</h2>
 * <blockquote>
 * Red Line 12: Security Boundary Cannot Be Bypassed
 * ✗ Prohibited: Plugins implementing authentication logic themselves (bypassing AuthCapability)
 * ✗ Prohibited: Outputting sensitive data in logs/error messages
 * ✗ Prohibited: Hardcoded credentials
 * ✗ Prohibited: Direct credential passing between plugins
 * </blockquote>
 *
 * <h2>Source</h2>
 * AWS IAM / Zero Trust Architecture
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
public final class SecurityBoundaryRule {

    private SecurityBoundaryRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== Forbidden Auth Libraries ====================

    /** JWT libraries that should not be used directly by plugins */
    private static final String JWT_IMPL = "io.jsonwebtoken..";
    
    /** Auth0 libraries */
    private static final String AUTH0_IMPL = "com.auth0..";
    
    /** Spring Security crypto (password encoding) */
    private static final String SPRING_CRYPTO = "org.springframework.security.crypto..";
    
    /** OAuth2 client implementation */
    private static final String OAUTH2_CLIENT = "org.springframework.security.oauth2.client..";

    // ==================== No Direct Auth Implementation ====================

    /**
     * Plugins must not use JWT libraries directly.
     *
     * <p>Authentication and token handling must go through AuthCapability.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noDirectJwtUsage() {
        return noClasses()
                .that().resideInAPackage("..core..")
                .should().dependOnClassesThat()
                .resideInAPackage(JWT_IMPL)
                .because("Red Line 12: Plugins must not use JWT libraries directly. " +
                        "Use AuthCapability for token operations.")
                .allowEmptyShould(true);
    }

    /**
     * Plugins must not use Auth0 libraries directly.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noDirectAuth0Usage() {
        return noClasses()
                .that().resideInAPackage("..core..")
                .should().dependOnClassesThat()
                .resideInAPackage(AUTH0_IMPL)
                .because("Red Line 12: Plugins must not use Auth0 libraries directly. " +
                        "Use AuthCapability for authentication.")
                .allowEmptyShould(true);
    }

    /**
     * Plugins must not use password encoding directly.
     *
     * <p>Password handling must be done through AuthCapability to ensure
     * consistent security policies.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noDirectCryptoUsage() {
        return noClasses()
                .that().resideInAPackage("..core..")
                .should().dependOnClassesThat()
                .resideInAPackage(SPRING_CRYPTO)
                .because("Red Line 12: Plugins must not use crypto libraries directly. " +
                        "Use AuthCapability for password operations.")
                .allowEmptyShould(true);
    }

    /**
     * Plugins must not implement OAuth2 client directly.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noDirectOAuth2ClientUsage() {
        return noClasses()
                .that().resideInAPackage("..core..")
                .should().dependOnClassesThat()
                .resideInAPackage(OAUTH2_CLIENT)
                .because("Red Line 12: Plugins must not implement OAuth2 client directly. " +
                        "Use AuthCapability for OAuth2 flows.")
                .allowEmptyShould(true);
    }

    // ==================== Combined Rules ====================

    /**
     * Plugins must not implement any authentication logic.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noCustomAuthImplementation() {
        return noClasses()
                .that().resideInAPackage("..core..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        JWT_IMPL,
                        AUTH0_IMPL,
                        SPRING_CRYPTO,
                        OAUTH2_CLIENT
                )
                .because("Red Line 12: Plugins must not implement authentication logic. " +
                        "All auth operations must go through AuthCapability.")
                .allowEmptyShould(true);
    }

    // ==================== Hardcoded Credentials Detection ====================

    /**
     * No fields with suspicious credential-like names should be plain strings.
     *
     * <p>This is a heuristic rule to catch potential hardcoded credentials.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noHardcodedCredentials() {
        return noFields()
                .that().areDeclaredInClassesThat().resideInAPackage("..core..")
                .and().haveNameMatching(".*(?i)(password|secret|apikey|api_key|token|credential).*")
                .and().haveRawType(String.class)
                .should().bePrivate()
                .andShould().beFinal()
                .because("Red Line 12: Potential hardcoded credentials detected. " +
                        "Sensitive values must be obtained from ConfigStore.");
    }

    /**
     * Default rule: no custom auth implementation.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule rule() {
        return noCustomAuthImplementation();
    }
}
