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
 * Annotations for multi-tenant access control in Brix Platform.
 *
 * <p>This package contains annotations that control and document tenant isolation
 * behavior. These annotations work in conjunction with the SQL interceptor chain
 * to enforce or relax tenant boundaries.
 *
 * <h2>Key Annotations</h2>
 * <dl>
 *   <dt>{@link io.brix.platform.tenant.annotation.CrossTenantAccess}</dt>
 *   <dd>Marks a method or class as explicitly requiring cross-tenant data access.
 *       Used for platform-level operations like analytics, system administration,
 *       and data migration. Requires a documented reason for security auditing.</dd>
 * </dl>
 *
 * <h2>Security Model</h2>
 * <p>By default, all database operations in Brix Platform are automatically filtered
 * by the current tenant context. These annotations provide a controlled way to
 * bypass this filtering when legitimate business needs require it.
 *
 * <h2>Code Review Requirements</h2>
 * <p>Usage of {@code @CrossTenantAccess} must be reviewed for:
 * <ul>
 *   <li>Clear business justification in the reason attribute</li>
 *   <li>Minimum required scope (method vs class level)</li>
 *   <li>Additional authorization checks for write operations</li>
 *   <li>Audit logging for compliance</li>
 * </ul>
 *
 * <h2>Architecture Layer</h2>
 * <p>Layer 2C: Implementation Layer (platform-commons)</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see io.brix.platform.tenant.interceptor.TenantSqlGuardInterceptor
 */
package io.brix.platform.tenant.annotation;
