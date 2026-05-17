/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.admin.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration for the Platform Admin module.
 *
 * <h3>Activation</h3>
 * <p>Automatically activated when {@code platform-admin} is on the classpath
 * and the application is a Servlet-based web application
 * ({@code ConditionalOnWebApplication.Type.SERVLET}).
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C — Implementation. Registers all controllers, services, and
 * repositories defined in the {@code io.brix.platform.admin} package tree.
 *
 * <h3>Architecture Red Lines</h3>
 * <ul>
 *   <li>R-1: This module MUST NOT depend on any {@code enterprise-*} module.</li>
 *   <li>R-2: {@code enterprise-solutions} MUST NOT depend on this module.</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ComponentScan(basePackages = "io.brix.platform.admin")
public class PlatformAdminAutoConfiguration {
    // All beans are discovered via @ComponentScan.
    // Bean-level @ConditionalOnMissingBean guards can be added here if needed in the future.
}
