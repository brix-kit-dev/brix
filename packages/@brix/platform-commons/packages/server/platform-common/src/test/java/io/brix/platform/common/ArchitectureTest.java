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
package io.brix.platform.common;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import io.brix.architecture.guard.BrixArchitectureRules;

/**
 * 平台公共库架构守护测试。
 *
 * <p>本测试类验证 platform-common 模块遵守以下架构红线：
 * <ul>
 *   <li>R5: 公共库不依赖业务层（插件、适配器）</li>
 *   <li>R6: 禁止直接依赖基础设施中间件</li>
 *   <li>R7: 禁止抛出泛型异常</li>
 *   <li>R9: 禁止循环依赖</li>
 * </ul>
 *
 * <p>platform-common 是纯工具库层，提供跨模块共享的基础能力
 * （如分页、异常、租户上下文等），不包含任何业务逻辑。
 *
 * @see BrixArchitectureRules#commonsProfile()
 */
@AnalyzeClasses(
    packages = "io.brix.platform.common",
    importOptions = ImportOption.DoNotIncludeTests.class
)
public class ArchitectureTest {

    /**
     * 应用公共库架构规则集。
     *
     * <p>包含以下规则：
     * <ul>
     *   <li>commonsNotDependOnPlugins - 不依赖插件</li>
     *   <li>commonsNotDependOnAdapters - 不依赖适配器</li>
     *   <li>commonsNotDependOnHost - 不依赖主机壳</li>
     *   <li>commonsNotDependOnOrchestrator - 不依赖编排层</li>
     *   <li>noGenericExceptions - 禁止泛型异常</li>
     *   <li>noKafkaDependency - 禁止Kafka依赖</li>
     *   <li>noRedisDependency - 禁止Redis依赖</li>
     *   <li>noMySqlDependency - 禁止MySQL依赖</li>
     *   <li>noCyclicDependencies - 禁止循环依赖</li>
     * </ul>
     */
    @ArchTest
    static final ArchTests commonsRules = BrixArchitectureRules.commonsProfile();
}
