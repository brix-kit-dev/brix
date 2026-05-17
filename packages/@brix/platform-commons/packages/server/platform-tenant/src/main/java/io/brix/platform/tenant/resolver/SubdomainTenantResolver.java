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

import io.brix.platform.tenant.repository.TenantRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Subdomain-based tenant resolver — extracts tenant code from the Host header.
 *
 * <p>Resolves tenant identity by matching the request's {@code Host} header against
 * a configurable subdomain pattern. The default pattern extracts the first subdomain
 * segment from hosts matching {@code {code}.console.*} (e.g., {@code acme.console.brix.com}).
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer (platform-tenant module)</p>
 *
 * <h3>Activation</h3>
 * <p>This resolver is conditionally registered when the property
 * {@code brix.tenant.resolver.subdomain.enabled=true} is set.
 * It is <b>not</b> active by default to avoid interfering with
 * localhost-based development environments.</p>
 *
 * <h3>Resolution Flow</h3>
 * <ol>
 *   <li>Read {@code Host} header (via {@code getServerName()})</li>
 *   <li>Match against compiled subdomain pattern</li>
 *   <li>Validate extracted code format (alphanumeric + hyphens, 2-50 chars)</li>
 *   <li>Look up tenant ID by code via {@link TenantRepository#findByCode}</li>
 *   <li>Return tenant ID as string, or empty if not found</li>
 * </ol>
 *
 * <h3>Security</h3>
 * <ul>
 *   <li>Tenant code is validated against a strict regex before DB lookup</li>
 *   <li>Only alphanumeric characters and hyphens are permitted</li>
 *   <li>Priority 200: lower trust than JWT (0) and Header (100)</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <pre>{@code
 * brix:
 *   tenant:
 *     resolver:
 *       subdomain:
 *         enabled: true
 *         pattern: "^([a-z0-9-]+)\\.console\\."
 * }</pre>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see TenantResolver
 * @see TenantResolverChain
 */
@Component
@ConditionalOnProperty(name = "brix.tenant.resolver.subdomain.enabled", havingValue = "true")
public class SubdomainTenantResolver implements TenantResolver {

    private static final Logger log = LoggerFactory.getLogger(SubdomainTenantResolver.class);

    /**
     * Strict validation pattern for tenant codes.
     * Must start and end with alphanumeric, allow hyphens in between, 2-50 chars.
     */
    private static final Pattern CODE_VALIDATION = Pattern.compile("^[a-z0-9][a-z0-9-]{0,48}[a-z0-9]$");

    /**
     * Default subdomain pattern: captures the first subdomain segment before {@code .console.}.
     */
    private static final String DEFAULT_PATTERN = "^([a-z0-9-]+)\\.console\\.";

    private final TenantRepository tenantRepository;
    private final String subdomainPatternStr;
    private Pattern compiledPattern;

    /**
     * Creates a SubdomainTenantResolver with configurable pattern.
     *
     * @param tenantRepository repository for tenant code → ID lookup
     * @param subdomainPattern regex pattern with one capture group for the tenant code;
     *                         falls back to default if blank
     */
    public SubdomainTenantResolver(
            TenantRepository tenantRepository,
            @org.springframework.beans.factory.annotation.Value(
                "${brix.tenant.resolver.subdomain.pattern:" + DEFAULT_PATTERN + "}"
            ) String subdomainPattern) {
        this.tenantRepository = tenantRepository;
        this.subdomainPatternStr = (subdomainPattern != null && !subdomainPattern.isBlank())
                ? subdomainPattern : DEFAULT_PATTERN;
    }

    /**
     * Compiles the subdomain regex pattern after construction.
     */
    @PostConstruct
    void init() {
        this.compiledPattern = Pattern.compile(subdomainPatternStr);
        log.info("SubdomainTenantResolver initialized with pattern: {}", subdomainPatternStr);
    }

    /**
     * Resolves tenant ID from the request's {@code Host} header.
     *
     * @param request the HTTP servlet request
     * @return tenant ID if subdomain matches a known tenant, empty otherwise
     */
    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        String host = request.getServerName();
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = compiledPattern.matcher(host);
        if (!matcher.find()) {
            log.trace("Host '{}' does not match subdomain pattern", host);
            return Optional.empty();
        }

        String tenantCode = matcher.group(1);
        if (tenantCode == null || !CODE_VALIDATION.matcher(tenantCode).matches()) {
            log.debug("Extracted subdomain '{}' failed code validation", tenantCode);
            return Optional.empty();
        }

        return tenantRepository.findByCode(tenantCode)
                .map(tenant -> {
                    String tenantId = String.valueOf(tenant.getId());
                    log.debug("Subdomain '{}' resolved to tenant ID {}", tenantCode, tenantId);
                    return tenantId;
                });
    }

    /**
     * Checks whether the request has a {@code Host} header that could contain a subdomain.
     *
     * @param request the HTTP request
     * @return true if the host is present and non-empty
     */
    @Override
    public boolean supports(HttpServletRequest request) {
        String host = request.getServerName();
        return host != null && !host.isBlank();
    }

    /**
     * Returns the priority order of this resolver.
     *
     * <p>Priority 200: request-based source, lower trust than JWT (0) and Header (100).</p>
     *
     * @return 200
     */
    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public String getName() {
        return "SubdomainTenantResolver";
    }
}
