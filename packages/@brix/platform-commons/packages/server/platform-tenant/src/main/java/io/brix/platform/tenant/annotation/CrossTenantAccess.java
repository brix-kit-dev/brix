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
package io.brix.platform.tenant.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or class as explicitly requiring cross-tenant data access.
 *
 * <p>By default, all database operations in Brix Platform are automatically filtered
 * by the current tenant context. This annotation is used to explicitly bypass this
 * automatic filtering for legitimate cross-tenant scenarios such as:
 *
 * <ul>
 *   <li>Platform-level analytics and reporting</li>
 *   <li>System administration operations</li>
 *   <li>Cross-tenant data migration</li>
 *   <li>Billing and usage aggregation</li>
 * </ul>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer (platform-tenant module)</p>
 *
 * <h3>Security Implications</h3>
 * <p><b>WARNING:</b> Using this annotation bypasses tenant isolation, which is a
 * critical security boundary. Every usage must:
 * <ol>
 *   <li>Be reviewed by a security-conscious team member</li>
 *   <li>Have a documented reason in the {@link #reason()} attribute</li>
 *   <li>Be logged to the audit trail</li>
 *   <li>Be limited to the minimum required scope</li>
 * </ol>
 *
 * <h3>How It Works</h3>
 * <p>When the {@code TenantSqlGuardInterceptor} detects a SQL query without
 * tenant filtering, it checks if the calling method or class has this annotation.
 * If present, the query is allowed to proceed; otherwise, an exception is thrown
 * in development mode or a warning is logged in production mode.
 *
 * <h3>Usage Examples</h3>
 *
 * <h4>Method-level annotation (preferred)</h4>
 * <pre>{@code
 * @Service
 * public class PlatformAnalyticsService {
 *
 *     @CrossTenantAccess(
 *         reason = "Platform-level tenant usage statistics",
 *         approval = "SEC-ARCH-2026-tenant-analytics"
 *     )
 *     public List<TenantUsageStats> getAllTenantStats() {
 *         // Aggregates usage across all tenants
 *         return statsRepository.findAllTenantStats();
 *     }
 * }
 * }</pre>
 *
 * <h4>Class-level annotation (use sparingly)</h4>
 * <pre>{@code
 * @Repository
 * @CrossTenantAccess(
 *     reason = "System table repository - no tenant filtering required",
 *     approval = "SEC-ARCH-2026-system-config"
 * )
 * public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {
 *     // All methods in this repository bypass tenant filtering
 * }
 * }</pre>
 *
 * <h3>Code Review Checklist</h3>
 * <p>When reviewing code with this annotation, verify:</p>
 * <ul>
 *   <li>The reason clearly explains why cross-tenant access is needed</li>
 *   <li>The scope is as narrow as possible (method vs class)</li>
 *   <li>The operation is read-only when possible</li>
 *   <li>Write operations have additional authorization checks</li>
 *   <li>Audit logging captures the cross-tenant access</li>
 * </ul>
 *
 * <h3>Audit Trail</h3>
 * <p>All cross-tenant operations should be logged with:
 * <ul>
 *   <li>The user performing the operation</li>
 *   <li>The method/class with the annotation</li>
 *   <li>The stated reason</li>
 *   <li>The timestamp and result</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see io.brix.platform.tenant.interceptor.TenantSqlGuardInterceptor
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface CrossTenantAccess {

    /**
     * Documents the reason for requiring cross-tenant access.
     *
     * <p>This reason is mandatory and will be:
     * <ul>
     *   <li>Logged in audit trail when the annotated code is executed</li>
     *   <li>Reviewed during code review to validate the business justification</li>
     *   <li>Used for security audits and compliance reporting</li>
     * </ul>
     *
     * <p><b>Good examples:</b></p>
     * <ul>
     *   <li>"Platform-level billing calculation across all tenants"</li>
     *   <li>"System health dashboard showing all tenant status"</li>
     *   <li>"Data migration from legacy single-tenant to multi-tenant"</li>
     * </ul>
     *
     * <p><b>Bad examples (will fail code review):</b></p>
     * <ul>
     *   <li>"Needed for query" (too vague)</li>
     *   <li>"Fix tenant issue" (unclear scope)</li>
     *   <li>"" (empty reason)</li>
     * </ul>
     *
     * @return the documented reason for cross-tenant access
     */
    String reason();

    /**
     * Documents the approval or architecture decision record that authorizes
     * this cross-tenant access path.
     *
     * <p>This value is mandatory. It must point to a reviewable approval source
     * such as a security review ticket, architecture decision record, or
     * compliance exception identifier.</p>
     *
     * @return approval reference for this cross-tenant access
     */
    String approval();

    /**
     * Optional: specifies which tenant IDs are allowed to be accessed.
     *
     * <p>If specified, only the listed tenants can be accessed. If empty (default),
     * all tenants are accessible. This provides an additional layer of control
     * for operations that only need access to specific tenants.
     *
     * <p>Example:</p>
     * <pre>{@code
     * @CrossTenantAccess(
     *     reason = "Merge data from subsidiary tenants to parent",
     *     allowedTenants = {"tenant-parent", "tenant-subsidiary-1", "tenant-subsidiary-2"}
     * )
     * public void consolidateSubsidiaryData() {
     *     // Only accesses the specified tenants
     * }
     * }</pre>
     *
     * @return array of allowed tenant codes/IDs, or empty for all tenants
     */
    String[] allowedTenants() default {};

    /**
     * Optional: indicates if the operation is read-only.
     *
     * <p>Read-only cross-tenant operations are generally lower risk than write
     * operations. Setting this to true enables additional optimizations and
     * reduces audit log verbosity.
     *
     * <p>If set to true but the operation performs writes, an exception will
     * be thrown at runtime.
     *
     * @return true if this operation only reads data, false if it may write
     */
    boolean readOnly() default false;
}
