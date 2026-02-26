/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.runtime.sdk;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import io.brix.architecture.guard.BrixArchitectureRules;

/**
 * 运行时SDK架构守护测试。
 *
 * <p>本测试类验证 runtime-sdk-api 模块遵守以下架构红线：
 * <ul>
 *   <li>R5: API层不依赖实现层（Orchestrator、Adapter）</li>
 *   <li>R6: 禁止直接依赖基础设施中间件（Kafka、Redis、MySQL）</li>
 *   <li>R9: 禁止循环依赖</li>
 * </ul>
 *
 * <p>runtime-sdk-api 是契约定义层，定义了模块与 Runtime Shell 之间的能力接口。
 * 本模块必须保持纯净，不引入任何基础设施实现依赖。
 *
 * @see BrixArchitectureRules#sdkProfile()
 */
@AnalyzeClasses(
    packages = "io.runtime.sdk",
    importOptions = ImportOption.DoNotIncludeTests.class
)
public class ArchitectureTest {

    /**
     * 应用SDK层架构规则集。
     *
     * <p>包含以下规则：
     * <ul>
     *   <li>apiNotDependOnImpl - API不依赖实现包</li>
     *   <li>sdkNotDependOnOrchestrator - SDK不依赖编排层</li>
     *   <li>sdkNotDependOnAdapters - SDK不依赖适配器</li>
     *   <li>sdkNotDependOnPlugins - SDK不依赖插件</li>
     *   <li>noKafkaDependency - 禁止Kafka依赖</li>
     *   <li>noRedisDependency - 禁止Redis依赖</li>
     *   <li>noMySqlDependency - 禁止MySQL依赖</li>
     *   <li>noMongoDbDependency - 禁止MongoDB依赖</li>
     *   <li>noCyclicDependencies - 禁止循环依赖</li>
     * </ul>
     */
    @ArchTest
    static final ArchTests sdkRules = BrixArchitectureRules.sdkProfile();
}
