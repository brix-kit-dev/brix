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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.brix.platform.tenant.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Path-based tenant resolver — extracts tenant code from the URL path.
 *
 * <p>Resolves tenant identity by matching the request URI against the pattern
 * {@code /t/{tenant_code}/...}. This is designed for environments where
 * subdomain-based routing is unavailable, such as mini-programs (WeChat),
 * embedded WebViews, or single-domain deployments.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer (platform-tenant module)</p>
 *
 * <h3>Activation</h3>
 * <p>Conditionally registered when {@code brix.tenant.resolver.path.enabled=true}.
 * Disabled by default to avoid false matches on standard API routes.</p>
 *
 * <h3>URL Pattern</h3>
 * <pre>
 * /t/{tenant_code}/...
 * /t/{tenant_code}
 * </pre>
 * <p>Examples:</p>
 * <ul>
 *   <li>{@code /t/acme-corp/api/v1/bookings} → tenant code "acme-corp"</li>
 *   <li>{@code /t/demo/dashboard} → tenant code "demo"</li>
 * </ul>
 *
 * <h3>Security</h3>
 * <ul>
 *   <li>Tenant code validated against strict alphanumeric + hyphen pattern</li>
 *   <li>Priority 250: lowest trust among active resolvers</li>
 *   <li>Conflict detection in {@link TenantResolverChain} prevents spoofing</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <pre>{@code
 * brix:
 *   tenant:
 *     resolver:
 *       path:
 *         enabled: true
 * }</pre>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see TenantResolver
 * @see TenantResolverChain
 */
@Component
@ConditionalOnProperty(name = "brix.tenant.resolver.path.enabled", havingValue = "true")
public class PathTenantResolver implements TenantResolver {

    private static final Logger log = LoggerFactory.getLogger(PathTenantResolver.class);

    /**
     * Pattern matching {@code /t/{tenant_code}} optionally followed by more path segments.
     * Capture group 1 = tenant code (lowercase alphanumeric + hyphens).
     */
    private static final Pattern PATH_PATTERN = Pattern.compile("^/t/([a-z0-9][a-z0-9-]{0,48}[a-z0-9])(/.*)?$");

    private final TenantRepository tenantRepository;

    /**
     * Creates a PathTenantResolver.
     *
     * @param tenantRepository repository for tenant code → ID lookup
     */
    public PathTenantResolver(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
        log.info("PathTenantResolver initialized for /t/{code}/... pattern");
    }

    /**
     * Resolves tenant ID from the request URI path.
     *
     * @param request the HTTP servlet request
     * @return tenant ID if path matches {@code /t/{code}/...} and code maps to a tenant, empty otherwise
     */
    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return Optional.empty();
        }

        Matcher matcher = PATH_PATTERN.matcher(path);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String tenantCode = matcher.group(1);
        return tenantRepository.findByCode(tenantCode)
                .map(tenant -> {
                    String tenantId = String.valueOf(tenant.getId());
                    log.debug("Path '/t/{}' resolved to tenant ID {}", tenantCode, tenantId);
                    return tenantId;
                });
    }

    /**
     * Checks whether the request URI starts with {@code /t/}.
     *
     * @param request the HTTP request
     * @return true if the URI begins with the tenant path prefix
     */
    @Override
    public boolean supports(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/t/");
    }

    /**
     * Returns the priority order of this resolver.
     *
     * <p>Priority 250: lowest among standard resolvers. Subdomain (200) takes precedence.</p>
     *
     * @return 250
     */
    @Override
    public int getOrder() {
        return 250;
    }

    @Override
    public String getName() {
        return "PathTenantResolver";
    }
}
