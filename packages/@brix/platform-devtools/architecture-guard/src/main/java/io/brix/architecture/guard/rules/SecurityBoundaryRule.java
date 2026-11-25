/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * Security Boundary Rules - Red Line 12.
 *
 * <p>Enforces security boundaries that plugins cannot bypass.</p>
 *
 * <p>The library rules check every class the module's test imports, whatever its package, and fail when the test
 * imports none: what makes a class plugin code is the artifact it is in, not the name of its package (root
 * BRX-RS-MANIFEST-009, A-14). They used to look only at packages named {@code core}, which a plugin of one artifact
 * and most enterprise plugin cores do not have (standard-link plan queue item 21, 21a, D173).</p>
 *
 * <p>A class that already used JWT or Spring Security crypto when the rules reached it is listed in its module, one
 * list per rule (see {@link KnownViolations}); the lists only shrink.</p>
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

    /**
     * JWT and JOSE libraries that should not be used directly by plugins: JJWT, Nimbus, jose4j and Spring Security's JWT
     * and resource-server support, which the host brings with its identity provider.
     */
    private static final String[] JWT_LIBRARIES = {
        "io.jsonwebtoken..",
        "com.nimbusds..",
        "org.jose4j..",
        "org.springframework.security.oauth2.jwt..",
        "org.springframework.security.oauth2.jose..",
        "org.springframework.security.oauth2.server.resource.."
    };

    /** Auth0 libraries */
    private static final String AUTH0_IMPL = "com.auth0..";
    
    /** Spring Security crypto (password encoding) */
    private static final String SPRING_CRYPTO = "org.springframework.security.crypto..";
    
    /** OAuth2 client implementation */
    private static final String OAUTH2_CLIENT = "org.springframework.security.oauth2.client..";

    // ==================== Known Violations ====================

    /** Where a module lists its classes that {@link #noDirectJwtUsage()} lets through. */
    public static final String KNOWN_JWT_LIBRARY_USERS = "META-INF/brix/guard/jwt-library-users.txt";

    /** Where a module lists its classes that {@link #noDirectCryptoUsage()} lets through. */
    public static final String KNOWN_SPRING_CRYPTO_USERS = "META-INF/brix/guard/spring-crypto-users.txt";

    // ==================== No Direct Auth Implementation ====================

    /**
     * Plugins must not use JWT libraries directly (root S-1).
     *
     * <p>Authentication and token handling must go through AuthCapability. A class listed in
     * {@value #KNOWN_JWT_LIBRARY_USERS} of its module is let through, by this rule only.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noDirectJwtUsage() {
        return noClasses()
                .that(KnownViolations.notListedIn(KNOWN_JWT_LIBRARY_USERS))
                .should().dependOnClassesThat()
                .resideInAnyPackage(JWT_LIBRARIES)
                .because("Red Line 12: Plugins must not use JWT libraries directly. " +
                        "Use AuthCapability for token operations. A class that already does is listed in " +
                        KNOWN_JWT_LIBRARY_USERS + " of its module, and the lists only shrink.");
    }

    /**
     * Plugins must not use Auth0 libraries directly.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noDirectAuth0Usage() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(AUTH0_IMPL)
                .because("Red Line 12: Plugins must not use Auth0 libraries directly. " +
                        "Use AuthCapability for authentication.");
    }

    /**
     * Plugins must not use password encoding directly.
     *
     * <p>Password handling must be done through PasswordCapability to ensure
     * consistent security policies. A class listed in {@value #KNOWN_SPRING_CRYPTO_USERS} of its module is let
     * through, by this rule only.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noDirectCryptoUsage() {
        return noClasses()
                .that(KnownViolations.notListedIn(KNOWN_SPRING_CRYPTO_USERS))
                .should().dependOnClassesThat()
                .resideInAPackage(SPRING_CRYPTO)
                .because("Red Line 12: Plugins must not use crypto libraries directly. " +
                        "Use PasswordCapability for password operations. A class that already does is listed in " +
                        KNOWN_SPRING_CRYPTO_USERS + " of its module, and the lists only shrink.");
    }

    /**
     * Plugins must not implement OAuth2 client directly.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noDirectOAuth2ClientUsage() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(OAUTH2_CLIENT)
                .because("Red Line 12: Plugins must not implement OAuth2 client directly. " +
                        "Use AuthCapability for OAuth2 flows.");
    }

    // ==================== Combined Rules ====================

    /**
     * Plugins must not implement any authentication logic: the four library rules above, with their lists, as one rule.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noCustomAuthImplementation() {
        return CompositeArchRule.of(noDirectJwtUsage())
                .and(noDirectAuth0Usage())
                .and(noDirectCryptoUsage())
                .and(noDirectOAuth2ClientUsage())
                .because("Red Line 12: Plugins must not implement authentication logic. " +
                        "All auth operations must go through AuthCapability and PasswordCapability.");
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
