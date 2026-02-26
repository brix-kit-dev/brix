/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.infra.adapter.redis;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import io.brix.architecture.guard.BrixArchitectureRules;

/**
 * 适配器层架构约束测试。
 *
 * <p>使用 AdapterProfile 验证基础设施适配器层架构红线。</p>
 */
@AnalyzeClasses(
    packages = "io.infra.adapter.redis",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchTests adapterRules = BrixArchitectureRules.adapterProfile();
}
