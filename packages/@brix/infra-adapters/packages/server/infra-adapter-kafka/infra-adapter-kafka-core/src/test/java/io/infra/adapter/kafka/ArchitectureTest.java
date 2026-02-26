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
package io.infra.adapter.kafka;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import io.brix.architecture.guard.BrixArchitectureRules;

/**
 * Kafka适配器架构守护测试。
 *
 * <p>本测试类验证 infra-adapter-kafka 模块遵守以下架构红线：
 * <ul>
 *   <li>R6/D4: 适配器隔离，不暴露第三方类型给上层</li>
 *   <li>R4: 依赖 runtime-sdk-api 能力接口</li>
 *   <li>R9: 禁止适配器之间相互依赖</li>
 * </ul>
 *
 * <p>infra-adapter-kafka 是基础设施适配器层，封装 Kafka 实现细节，
 * 对外仅暴露 EventBusCapability 接口。
 *
 * @see BrixArchitectureRules#adapterProfile()
 */
@AnalyzeClasses(
    packages = "io.infra.adapter.kafka",
    importOptions = ImportOption.DoNotIncludeTests.class
)
public class ArchitectureTest {

    /**
     * 应用适配器层架构规则集。
     *
     * <p>包含以下规则：
     * <ul>
     *   <li>adaptersMustImplementCapability - 适配器必须实现能力接口</li>
     *   <li>noPluginDependency - 适配器不依赖插件</li>
     *   <li>noHostDependency - 适配器不依赖主机壳</li>
     *   <li>noAdapterCircularDependency - 适配器间无循环依赖</li>
     *   <li>publicApiMustNotLeakThirdParty - 公共API不泄露第三方类型</li>
     *   <li>configClassesMustBeInternal - 配置类必须为内部类</li>
     *   <li>noCyclicPackageDependencies - 包级无循环依赖</li>
     * </ul>
     */
    @ArchTest
    static final ArchTests adapterRules = BrixArchitectureRules.adapterProfile();
}
