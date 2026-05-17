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
package io.brix.platform.auth.reactive;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot auto-configuration for the reactive OAuth2 login module.
 *
 * <p>Activation conditions (all must be met):
 * <ul>
 *   <li>The application is a reactive (WebFlux) web application.</li>
 *   <li>The property {@code platform.oauth2.enabled} is set to {@code true}.</li>
 * </ul>
 *
 * <p>When active, this auto-configuration component-scans the
 * {@code io.brix.platform.auth.reactive.oauth2} package, which registers:
 * <ul>
 *   <li>{@link io.brix.platform.auth.reactive.oauth2.OAuth2Config} — WebClient.Builder bean</li>
 *   <li>{@link io.brix.platform.auth.reactive.oauth2.OAuth2LoginController} — REST endpoints</li>
 *   <li>{@link io.brix.platform.auth.reactive.oauth2.OAuth2UserService} — Token exchange + user info</li>
 * </ul>
 *
 * <h3>Architecture Note</h3>
 * <p>This auto-configuration is exclusively for reactive (WebFlux) hosts.
 * Servlet-based hosts should use {@code platform-auth-servlet} instead.
 * The two modules are mutually exclusive at runtime — including both on the
 * classpath will not cause conflicts because of the
 * {@code @ConditionalOnWebApplication} guards.
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since P112
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "platform.oauth2", name = "enabled", havingValue = "true")
@ComponentScan(basePackages = "io.brix.platform.auth.reactive.oauth2")
public class OAuth2ReactiveAutoConfiguration {
    // Marker configuration — all beans are discovered via @ComponentScan.
}
