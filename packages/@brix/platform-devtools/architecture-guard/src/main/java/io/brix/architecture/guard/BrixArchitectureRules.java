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
package io.brix.architecture.guard;

import com.tngtech.archunit.junit.ArchTests;


import io.brix.architecture.guard.profiles.AdapterProfile;
import io.brix.architecture.guard.profiles.CommonsProfile;
import io.brix.architecture.guard.profiles.HostProfile;
import io.brix.architecture.guard.profiles.PluginProfile;
import io.brix.architecture.guard.profiles.SdkProfile;

/**
 * Brix Architecture Rules Entry Point
 *
 * <p>Provides preset rule profiles covering 13 architecture red lines.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @AnalyzeClasses(packages = "io.brix.app.booking")
 * class ArchitectureTest {
 *     @ArchTest
 *     static final ArchTests rules = BrixArchitectureRules.pluginProfile();
 * }
 * }</pre>
 *
 * <h2>Rule Profiles</h2>
 * <table>
 *   <tr><th>Profile</th><th>Target Layer</th><th>Rules</th></tr>
 *   <tr><td>{@link #pluginProfile()}</td><td>Plugin (Business Modules)</td><td>42 rules (13 red lines)</td></tr>
 *   <tr><td>{@link #hostProfile()}</td><td>Host Layer</td><td>Ultra-thin shell rules</td></tr>
 *   <tr><td>{@link #adapterProfile()}</td><td>Infra Adapters</td><td>Adapter isolation rules</td></tr>
 *   <tr><td>{@link #sdkProfile()}</td><td>Runtime SDK</td><td>SDK API stability rules</td></tr>
 *   <tr><td>{@link #commonsProfile()}</td><td>Platform Commons</td><td>Utility library isolation</td></tr>
 * </table>
 *
 * <h2>Architecture Red Lines (13)</h2>
 * <ol>
 *   <li>插件不得直接依赖基础设施</li>
 *   <li>插件不得绕过 Runtime Shell</li>
 *   <li>前端 View 层不得直接调用 Repository</li>
 *   <li>Host 层必须超薄</li>
 *   <li>插件必须支持独立启停</li>
 *   <li>前端与后端物理分离</li>
 *   <li>配置必须通过 ConfigStore</li>
 *   <li>数据隔离（Data Ownership）</li>
 *   <li>API 版本兼容</li>
 *   <li>故障隔离（Fault Isolation）</li>
 *   <li>无循环依赖</li>
 *   <li>安全边界不可绕过</li>
 *   <li>跨服务事件一致性（Transactional Outbox）</li>
 * </ol>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
public final class BrixArchitectureRules {

    private BrixArchitectureRules() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Plugin layer rule profile (all 8 constraints).
     *
     * <p>For all plugin modules (e.g., booking, identity, products).
     * Enforces all architecture constraints to prevent cross-layer access.</p>
     *
     * @return ArchTests rule set
     */
    public static ArchTests pluginProfile() {
        return ArchTests.in(PluginProfile.class);
    }

    /**
     * Host layer rule profile (relaxed constraints).
     *
     * <p>For host-shell-standalone and host-shell-embedded.
     * Host layer may use infrastructure adapters and Spring APIs directly,
     * but still follows HTTP client and event publishing constraints.</p>
     *
     * @return ArchTests rule set
     */
    public static ArchTests hostProfile() {
        return ArchTests.in(HostProfile.class);
    }

    /**
     * Infrastructure adapter layer rule profile.
     *
     * <p>For infra-adapter-* modules. Enforces adapter isolation
     * and prevents third-party type leakage.</p>
     *
     * @return ArchTests rule set
     */
    public static ArchTests adapterProfile() {
        return ArchTests.in(AdapterProfile.class);
    }

    /**
     * Runtime SDK layer rule profile.
     *
     * <p>For runtime-sdk-api module. Enforces API stability
     * and prevents dependency on implementation layers.</p>
     *
     * @return ArchTests rule set
     */
    public static ArchTests sdkProfile() {
        return ArchTests.in(SdkProfile.class);
    }

    /**
     * Platform commons layer rule profile.
     *
     * <p>For platform-commons modules. Enforces utility library
     * isolation and prevents business logic dependencies.</p>
     *
     * @return ArchTests rule set
     */
    public static ArchTests commonsProfile() {
        return ArchTests.in(CommonsProfile.class);
    }
}
