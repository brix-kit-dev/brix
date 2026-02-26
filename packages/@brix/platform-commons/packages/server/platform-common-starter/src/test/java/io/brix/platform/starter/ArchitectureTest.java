/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.platform.starter;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import io.brix.architecture.guard.BrixArchitectureRules;

/**
 * 架构约束测试。
 *
 * <p>使用 CommonsProfile 验证平台公共库层架构红线。</p>
 */
@AnalyzeClasses(
    packages = "io.brix.platform.starter",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchTests commonsRules = BrixArchitectureRules.commonsProfile();
}
