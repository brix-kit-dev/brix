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
package io.brix.platform.tenant.resolver;

import io.brix.platform.common.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Tenant resolver that extracts tenant ID from HTTP request headers.
 * 
 * <p>This resolver looks for the tenant ID in the X-Tenant-ID header.
 * It provides a simple mechanism for clients to specify the tenant context
 * when JWT-based authentication is not used or when explicit override is needed.</p>
 * 
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer (platform-tenant module)</p>
 * 
 * <h3>HTTP Header</h3>
 * <pre>{@code
 * X-Tenant-ID: tenant-123
 * }</pre>
 * 
 * <h3>Use Cases</h3>
 * <ul>
 *   <li><b>Internal Service Calls:</b> Microservices propagating tenant context</li>
 *   <li><b>API Gateway:</b> Gateway injecting tenant ID after authentication</li>
 *   <li><b>Development:</b> Testing tenant-specific functionality</li>
 *   <li><b>Fallback:</b> When JWT doesn't contain tenant information</li>
 * </ul>
 * 
 * <h3>Security Considerations</h3>
 * <ul>
 *   <li>Header-based tenant resolution should be used with caution</li>
 *   <li>In production, prefer JWT-based resolution for external requests</li>
 *   <li>Use header resolution only for trusted internal service-to-service calls</li>
 *   <li>Always validate header tenant against authenticated user's tenants</li>
 * </ul>
 * 
 * <h3>Priority</h3>
 * <p>Priority 100 (medium) - Lower than JWT but higher than subdomain/path.
 * When both JWT and header specify tenant, {@link TenantResolverChain} performs
 * conflict detection to prevent spoofing attacks.</p>
 * 
 * <h3>Configuration</h3>
 * <pre>{@code
 * // Default: uses X-Tenant-ID header
 * HeaderTenantResolver resolver = new HeaderTenantResolver();
 * 
 * // Custom: uses X-Custom-Tenant header
 * HeaderTenantResolver resolver = new HeaderTenantResolver("X-Custom-Tenant");
 * }</pre>
 * 
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantResolver
 * @see TenantResolverChain
 * @see TenantContext#TENANT_HEADER
 */
public class HeaderTenantResolver implements TenantResolver {

    private static final Logger log = LoggerFactory.getLogger(HeaderTenantResolver.class);

    /**
     * The HTTP header name to extract tenant ID from.
     */
    private final String headerName;

    /**
     * Creates a HeaderTenantResolver using the default header name (X-Tenant-ID).
     */
    public HeaderTenantResolver() {
        this(TenantContext.TENANT_HEADER);
    }

    /**
     * Creates a HeaderTenantResolver with a custom header name.
     * 
     * @param headerName the HTTP header name to extract tenant ID from
     * @throws IllegalArgumentException if headerName is null or blank
     */
    public HeaderTenantResolver(String headerName) {
        if (headerName == null || headerName.isBlank()) {
            throw new IllegalArgumentException("Header name cannot be null or blank");
        }
        this.headerName = headerName;
    }

    /**
     * Resolves tenant ID from HTTP header.
     * 
     * <p>The header value is trimmed to remove any surrounding whitespace.
     * Empty or blank header values are treated as absent.</p>
     * 
     * @param request the HTTP servlet request
     * @return Optional containing tenant ID if found in header, empty otherwise
     */
    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        String headerValue = request.getHeader(headerName);
        
        if (headerValue == null || headerValue.isBlank()) {
            log.debug("No tenant ID found in header '{}'", headerName);
            return Optional.empty();
        }

        String tenantId = headerValue.trim();
        
        // Additional validation: reject obviously invalid tenant IDs
        if (tenantId.length() > 255) {
            log.warn("Tenant ID in header '{}' exceeds maximum length (255 chars)", headerName);
            return Optional.empty();
        }

        log.debug("Resolved tenant '{}' from header '{}'", tenantId, headerName);
        return Optional.of(tenantId);
    }

    /**
     * Checks if the request contains the tenant header.
     * 
     * @param request the HTTP servlet request
     * @return true if the header is present and non-blank, false otherwise
     */
    @Override
    public boolean supports(HttpServletRequest request) {
        String headerValue = request.getHeader(headerName);
        return headerValue != null && !headerValue.isBlank();
    }

    /**
     * Returns medium priority (100) for header-based resolution.
     * 
     * <p>Lower than JWT (0) but higher than subdomain/path resolution (200+).</p>
     * 
     * @return 100 (medium priority)
     */
    @Override
    public int getOrder() {
        return 100;
    }

    /**
     * Returns the resolver name for logging.
     * 
     * @return "HeaderTenantResolver"
     */
    @Override
    public String getName() {
        return "HeaderTenantResolver[header=" + headerName + "]";
    }

    /**
     * Returns the header name this resolver is configured to use.
     * 
     * @return the HTTP header name
     */
    public String getHeaderName() {
        return headerName;
    }
}
