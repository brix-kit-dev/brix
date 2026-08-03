/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.architecture.guard.executor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.brix.devtools.governance.artifact.ModuleKind;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaStaticBoundaryExecutorTest {

    private final JavaStaticBoundaryExecutor executor = new JavaStaticBoundaryExecutor();

    @TempDir
    private Path emptyDirectory;

    @Test
    void compliantPluginCorePassesAndReportsCardinalityForEveryRequirement() {
        JavaClasses classes = new ClassFileImporter().importPackages(
            "io.brix.phase2fixtures.compliant.core");

        List<JavaStaticBoundaryRuleResult> results = executor.execute(ModuleKind.PLUGIN_CORE, classes);

        assertEquals(executor.requirementsFor(ModuleKind.PLUGIN_CORE).size(), results.size());
        assertTrue(results.stream().allMatch(JavaStaticBoundaryRuleResult::passed));
        assertTrue(results.stream().allMatch(result -> result.targetCardinality() > 0));
    }

    @Test
    void violatingPluginCoreFailsWithIndependentKafkaNegativeFixture() {
        JavaClasses classes = new ClassFileImporter().importPackages(
            "io.brix.phase2fixtures.violating.core",
            "org.apache.kafka.clients.producer");

        JavaStaticBoundaryViolationException error = org.junit.jupiter.api.Assertions.assertThrows(
            JavaStaticBoundaryViolationException.class,
            () -> executor.executeOrThrow(ModuleKind.PLUGIN_CORE, classes));

        assertTrue(error.failures().stream().anyMatch(result ->
            result.requirementId().equals("A-1:plugin-core-no-kafka")
                && result.diagnosticCode().equals(JavaStaticBoundaryExecutor.RULE_VIOLATION)
                && result.targetCardinality() > 0));
    }

    @Test
    void hostSourceCannotImportAdapterImplementationTypes() {
        JavaClasses classes = new ClassFileImporter().importPackages(
            "io.brix.phase2fixtures.violating.host",
            "io.brix.infra.adapter.kafka");

        JavaStaticBoundaryViolationException error = org.junit.jupiter.api.Assertions.assertThrows(
            JavaStaticBoundaryViolationException.class,
            () -> executor.executeOrThrow(ModuleKind.HOST, classes));

        assertTrue(error.failures().stream().anyMatch(result ->
            result.requirementId().equals("A-6:host-no-adapter-source")
                && result.diagnosticCode().equals(JavaStaticBoundaryExecutor.RULE_VIOLATION)
                && result.targetCardinality() > 0));
    }

    @Test
    void emptyBytecodeTargetFailsEveryStaticRequirement() {
        JavaClasses classes = new ClassFileImporter().importPath(emptyDirectory);

        List<JavaStaticBoundaryRuleResult> results = executor.execute(ModuleKind.PLUGIN_CORE, classes);

        assertEquals(executor.requirementsFor(ModuleKind.PLUGIN_CORE).size(), results.size());
        assertTrue(results.stream().allMatch(result -> !result.passed()));
        assertTrue(results.stream().allMatch(result ->
            result.diagnosticCode().equals(JavaStaticBoundaryExecutor.EMPTY_TARGET)
                && result.targetCardinality() == 0));
    }

    @Test
    void sharedContractAndUiArtifactsHaveNoJavaStaticRequirementsInPhase2() {
        assertDoesNotThrow(() -> executor.executeOrThrow(ModuleKind.SHARED_CONTRACT,
            new ClassFileImporter().importPath(emptyDirectory)));
        assertTrue(executor.requirementsFor(ModuleKind.UI_WEB).isEmpty());
    }
}
