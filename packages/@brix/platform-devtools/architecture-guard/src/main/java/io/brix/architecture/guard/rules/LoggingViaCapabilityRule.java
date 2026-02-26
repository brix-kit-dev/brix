/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

/**
 * Logging Rule - Domain layer should not directly use LoggerFactory.
 *
 * <p>Defines two-level logging constraints:</p>
 * <ul>
 *   <li><b>General rule</b> ({@link #rule()}): No generic exceptions as coding baseline</li>
 *   <li><b>Domain layer rule</b> ({@link #noLoggerInDomain()}): Domain/Entity layers
 *       must not use {@code LoggerFactory.getLogger()} directly</li>
 * </ul>
 *
 * <h2>Progressive Strategy</h2>
 * <p>Service layer may use SLF4J Logger for debugging efficiency.
 * But Domain and Entity layers should remain pure business logic
 * without infrastructure concerns. Future versions may use ObservabilityCapability.</p>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 * @see io.runtime.sdk.capability.ObservabilityCapability
 */
public final class LoggingViaCapabilityRule {

    private LoggingViaCapabilityRule() {}

    /**
     * No generic exceptions - coding quality baseline.
     *
     * <p>Business code should use specific exception types,
     * not throw new Exception() or throw new RuntimeException().</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule rule() {
        return NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS
                .because("Generic exceptions (Exception/RuntimeException) are forbidden. " +
                        "Define specific exception types and log in Service layer");
    }

    /**
     * Domain/Entity layers must not use LoggerFactory directly.
     *
     * <p>Domain ({@code ..domain..}) and Entity ({@code ..entity..}) layers
     * should not use {@code org.slf4j.LoggerFactory}.
     * Logging should be done in Service layer. Domain stays pure business logic.</p>
     *
     * <p>Progressive constraint: Service layer may still use Logger.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noLoggerInDomain() {
        return noClasses()
                .that().resideInAnyPackage("..domain..", "..entity..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("org.slf4j.LoggerFactory")
                .because("Domain/Entity layers must not use LoggerFactory.getLogger(). " +
                        "Logging should be done in Service layer")
                .allowEmptyShould(true);
    }
}
