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
package io.brix.platform.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import jakarta.annotation.PostConstruct;

/**
 * CORS Configuration — Production-hardened whitelist enforcement.
 *
 * <p>Configures global CORS (Cross-Origin Resource Sharing) policies,
 * allowing frontend applications to access gateway APIs across origins.
 * All configuration options can be externalized via application.yml.</p>
 *
 * <h3>Phase 5.5 Production Hardening Enhancements</h3>
 * <ul>
 *   <li>Startup validation: blocks wildcard origins in production</li>
 *   <li>Security audit logging: logs all CORS configuration changes</li>
 *   <li>Exposed headers: includes {@code X-Trace-Id}, {@code X-Request-Id},
 *       {@code X-Tenant-Id} for observability correlation</li>
 *   <li>{@code Vary: Origin} header: automatically set by Spring's
 *       {@link CorsWebFilter} for proper CDN/proxy caching behavior</li>
 * </ul>
 *
 * <h3>OWASP Compliance</h3>
 * <p>Follows OWASP CORS guidelines: explicit origin whitelist, restricted
 * methods and headers, credentials limited to whitelisted origins.</p>
 *
 * @author Brix Platform Team
 * @version 2.0.0 (Phase 5.5 — Production Hardening)
 * @see CorsProperties CORS configuration properties
 * @see CorsWebFilter Spring WebFlux reactive CORS filter
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    /**
     * Security warning message template
     */
    private static final String SECURITY_WARNING = """

        ╔══════════════════════════════════════════════════════════╗
        ║                  ⚠️  CORS Security Warning  ⚠️            ║
        ╠══════════════════════════════════════════════════════════╣
        ║ Current CORS config allows all origins (allowed-origin-patterns: *)  ║
        ║ This should only be used in development - serious security risk in production!  ║
        ╠══════════════════════════════════════════════════════════╣
        ║ For production, configure specific domain whitelist in application.yml:  ║
        ║                                                          ║
        ║   gateway:                                                ║
        ║     cors:                                                 ║
        ║       allowed-origin-patterns:                            ║
        ║         - "https://www.your-domain.com"                   ║
        ║         - "https://*.your-domain.com"                     ║
        ║       block-wildcard-in-production: true                  ║
        ╚══════════════════════════════════════════════════════════╝
        """;

    /**
     * Credentials conflict warning message
     */
    private static final String CREDENTIALS_WARNING = """
        [CORS] Configuration warning: wildcard origin "*" should not be used with allowCredentials=true
        Browsers will reject cross-origin requests with credentials. Please configure specific domain whitelist.
        """;

    private final CorsProperties corsProperties;
    private final Environment environment;

    public CorsConfig(CorsProperties corsProperties, Environment environment) {
        this.corsProperties = corsProperties;
        this.environment = environment;
    }

    /**
     * Security check at startup.
     *
     * <p>Validates CORS configuration for security compliance,
     * outputs warnings or prevents application startup as appropriate.
     */
    @PostConstruct
    public void validateCorsConfiguration() {
        log.info("[CORS] Configuration loaded: {}", corsProperties);

        // Check whether configuration contains wildcard origin pattern
        if (corsProperties.hasWildcardOrigin()) {
            // Check if current environment is production
            boolean isProduction = isProductionEnvironment();

            // Production environment block check
            if (isProduction && corsProperties.isBlockWildcardInProduction()) {
                log.error("[CORS] Wildcard CORS configuration is forbidden in production! Please configure specific domain whitelist.");
                throw new IllegalStateException(
                    "CORS security check failed: Wildcard origin configuration is not allowed in production. " +
                    "Please configure specific allowed-origin-patterns domain whitelist in application.yml."
                );
            }

            // Output security warning
            if (corsProperties.isWarnOnWildcard()) {
                log.warn(SECURITY_WARNING);
            }

            // Credentials conflict check
            if (corsProperties.isAllowCredentials()) {
                log.warn(CREDENTIALS_WARNING);
            }
        } else {
            log.info("[CORS] Configuration check passed, allowed origins: {}", corsProperties.getAllowedOriginPatterns());
        }

        // Security audit: log the full CORS configuration for compliance
        logCorsSecurityAudit();
    }

    /**
     * Logs a security audit summary of the active CORS configuration.
     *
     * <p>Emitted at INFO level on every startup so that operations teams can
     * confirm the effective whitelist without inspecting YAML files.</p>
     */
    private void logCorsSecurityAudit() {
        log.info("[CORS-AUDIT] Effective CORS whitelist: origins={}, methods={}, headers={}, credentials={}, maxAge={}s",
            corsProperties.getAllowedOriginPatterns(),
            corsProperties.getAllowedMethods(),
            corsProperties.getAllowedHeaders(),
            corsProperties.isAllowCredentials(),
            corsProperties.getMaxAge()
        );
    }

    /**
     * Check if current environment is production.
     *
     * @return true if any active profile contains "prod"
     */
    private boolean isProductionEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if (profile.toLowerCase().contains("prod")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create CORS filter bean.
     *
     * <p>Creates a CORS filter based on {@link CorsProperties} configuration,
     * applied to all requests passing through the gateway.
     *
     * @return CorsWebFilter reactive CORS filter instance
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Configure allowed origin patterns
        config.setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns());

        // Configure allowed HTTP methods
        config.setAllowedMethods(corsProperties.getAllowedMethods());

        // Configure allowed request headers
        config.setAllowedHeaders(corsProperties.getAllowedHeaders());

        // Configure response headers exposed to clients
        if (corsProperties.getExposedHeaders() != null && !corsProperties.getExposedHeaders().isEmpty()) {
            config.setExposedHeaders(corsProperties.getExposedHeaders());
        }

        // Configure whether credentials are allowed
        config.setAllowCredentials(corsProperties.isAllowCredentials());

        // Configure preflight request cache duration
        config.setMaxAge(corsProperties.getMaxAge());

        // Create URL-based CORS configuration source
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // Apply CORS configuration to all paths
        source.registerCorsConfiguration("/**", config);

        log.debug("[CORS] Filter created, configuration: allowedOriginPatterns={}, allowedMethods={}, allowCredentials={}",
            corsProperties.getAllowedOriginPatterns(),
            corsProperties.getAllowedMethods(),
            corsProperties.isAllowCredentials()
        );

        return new CorsWebFilter(source);
    }
}
