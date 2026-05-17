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
package io.brix.platform.tenant.exception;

/**
 * Exception thrown when tenant identity conflicts are detected.
 * 
 * <p>This exception indicates a potential security issue where different sources
 * (JWT, headers, etc.) specify different tenant identifiers. This typically
 * indicates a tenant spoofing attempt and should result in HTTP 403 Forbidden.</p>
 * 
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer (platform-tenant module)</p>
 * 
 * <h3>Security Implications</h3>
 * <p>When this exception is thrown, it indicates:</p>
 * <ul>
 *   <li>Potential tenant spoofing attack</li>
 *   <li>Misconfigured client application</li>
 *   <li>Stale cached tenant header</li>
 *   <li>Incorrect service-to-service propagation</li>
 * </ul>
 * 
 * <h3>HTTP Response</h3>
 * <p>This exception should be mapped to HTTP 403 (Forbidden) by the global
 * exception handler. The response should NOT reveal specific tenant IDs
 * in production to prevent information leakage.</p>
 * 
 * <h3>Logging</h3>
 * <p>The exception contains detailed information for security auditing:
 * both tenant IDs and their sources. This should be logged at WARN level
 * for security monitoring.</p>
 * 
 * <h3>Example Scenario</h3>
 * <pre>{@code
 * // JWT contains: {"tid": "tenant-abc"}
 * // Header contains: X-Tenant-ID: tenant-xyz
 * // Result: TenantMismatchException thrown
 * 
 * try {
 *     tenantResolverChain.resolve(request);
 * } catch (TenantMismatchException e) {
 *     // Log for security audit
 *     securityLog.warn("Tenant mismatch: {}", e.getMessage());
 *     // Return 403 without details
 *     return ResponseEntity.status(403).body("Access denied");
 * }
 * }</pre>
 * 
 * @author Brix Platform Team
 * @since 3.1.0
 * @see io.brix.platform.tenant.resolver.TenantResolverChain
 */
public class TenantMismatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Error code for HTTP response.
     */
    public static final String ERROR_CODE = "TENANT_MISMATCH";

    /**
     * The tenant ID from the primary (trusted) source.
     */
    private final String primaryTenant;

    /**
     * The name of the primary source (e.g., "JwtTenantResolver").
     */
    private final String primarySource;

    /**
     * The conflicting tenant ID from another source.
     */
    private final String conflictingTenant;

    /**
     * The name of the conflicting source (e.g., "HeaderTenantResolver").
     */
    private final String conflictingSource;

    /**
     * Creates a TenantMismatchException with details about the conflict.
     * 
     * @param primaryTenant the tenant ID from the trusted source
     * @param primarySource the name of the trusted source
     * @param conflictingTenant the conflicting tenant ID
     * @param conflictingSource the name of the conflicting source
     */
    public TenantMismatchException(
            String primaryTenant, 
            String primarySource,
            String conflictingTenant, 
            String conflictingSource) {
        super(buildMessage(primaryTenant, primarySource, conflictingTenant, conflictingSource));
        this.primaryTenant = primaryTenant;
        this.primarySource = primarySource;
        this.conflictingTenant = conflictingTenant;
        this.conflictingSource = conflictingSource;
    }

    /**
     * Creates a simple TenantMismatchException with generic message.
     * 
     * @param message the exception message
     */
    public TenantMismatchException(String message) {
        super(message);
        this.primaryTenant = null;
        this.primarySource = null;
        this.conflictingTenant = null;
        this.conflictingSource = null;
    }

    /**
     * Builds a detailed message for logging.
     */
    private static String buildMessage(
            String primaryTenant, 
            String primarySource,
            String conflictingTenant, 
            String conflictingSource) {
        return String.format(
            "Tenant mismatch detected: %s='%s' conflicts with %s='%s'",
            primarySource, primaryTenant,
            conflictingSource, conflictingTenant
        );
    }

    /**
     * Returns the tenant ID from the primary (trusted) source.
     * 
     * @return the primary tenant ID, or null if not available
     */
    public String getPrimaryTenant() {
        return primaryTenant;
    }

    /**
     * Returns the name of the primary source.
     * 
     * @return the primary source name, or null if not available
     */
    public String getPrimarySource() {
        return primarySource;
    }

    /**
     * Returns the conflicting tenant ID.
     * 
     * @return the conflicting tenant ID, or null if not available
     */
    public String getConflictingTenant() {
        return conflictingTenant;
    }

    /**
     * Returns the name of the conflicting source.
     * 
     * @return the conflicting source name, or null if not available
     */
    public String getConflictingSource() {
        return conflictingSource;
    }

    /**
     * Returns the error code for HTTP response.
     * 
     * @return "TENANT_MISMATCH"
     */
    public String getErrorCode() {
        return ERROR_CODE;
    }

    /**
     * Returns a sanitized message suitable for HTTP response.
     * 
     * <p>This message omits specific tenant IDs to prevent
     * information leakage in API responses.</p>
     * 
     * @return a user-safe error message
     */
    public String getSanitizedMessage() {
        return "Tenant identity conflict detected. Access denied.";
    }
}
