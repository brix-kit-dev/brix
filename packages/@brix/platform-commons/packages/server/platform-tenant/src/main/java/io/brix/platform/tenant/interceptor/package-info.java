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

/**
 * SQL Interceptors for automatic tenant isolation in Brix Platform.
 *
 * <p>This package contains Hibernate {@link org.hibernate.resource.jdbc.spi.StatementInspector}
 * implementations that enforce row-level tenant isolation by automatically modifying SQL
 * statements to include tenant_id conditions.
 *
 * <h2>Interceptor Chain</h2>
 * <p>The interceptors execute in this order:
 * <ol>
 *   <li>{@link io.brix.platform.tenant.interceptor.TenantSqlGuardInterceptor} - Validates
 *       that SQL statements include proper tenant filtering (or are whitelisted)</li>
 *   <li>{@link io.brix.platform.tenant.interceptor.TenantInterceptor} - Modifies SQL
 *       statements to add tenant_id conditions</li>
 * </ol>
 *
 * <h2>Key Components</h2>
 * <dl>
 *   <dt>{@link io.brix.platform.tenant.interceptor.TenantInterceptor}</dt>
 *   <dd>Core interceptor that modifies SELECT, UPDATE, DELETE statements to include
 *       tenant_id filtering. Uses ThreadLocal-based TenantContext for tenant ID.</dd>
 *
 *   <dt>{@link io.brix.platform.tenant.interceptor.TenantSqlGuardInterceptor}</dt>
 *   <dd>Security guard that validates SQL statements. In development mode, throws
 *       exceptions for missing tenant filters. In production, logs warnings.</dd>
 *
 *   <dt>{@link io.brix.platform.tenant.interceptor.TenantInterceptorConfig}</dt>
 *   <dd>Spring configuration class that registers interceptors with Hibernate via
 *       {@link org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer}.</dd>
 * </dl>
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * brix:
 *   tenant:
 *     interceptor:
 *       enabled: true              # Enable/disable SQL tenant filtering
 *     guard:
 *       enabled: true              # Enable/disable SQL guard validation
 *       fail-on-violation: true    # Throw exception or just log
 * }</pre>
 *
 * <h2>Architecture Layer</h2>
 * <p>Layer 2C: Implementation Layer (platform-commons)</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see io.brix.platform.tenant.core.TenantWhitelist
 * @see io.brix.platform.common.tenant.TenantContext
 */
package io.brix.platform.tenant.interceptor;
