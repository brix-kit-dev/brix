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
package io.brix.platform.gateway;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * API Gateway Auto-Configuration
 * <p>
 * v3.1: Changed from @SpringBootApplication (with main()) to @AutoConfiguration.
 * main() entry point is Host layer responsibility (ShellApplication), this class only provides configuration.
 * </p>
 * <p>
 * Gateway doesn't need database, excludes JPA/DataSource auto-configuration.
 * </p>
 *
 * @author Brix Platform Authors
 * @version 3.1.0
 */
@AutoConfiguration
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
public class GatewayApplication {
    // v3.1: main() removed — Host layer ShellApplication is the only startup entry point
}
