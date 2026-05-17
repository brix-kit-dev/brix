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
package io.brix.platform.tenant.interceptor;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Configuration class for tenant SQL interceptors.
 *
 * <p>This configuration class sets up Hibernate's {@link StatementInspector} to intercept
 * all SQL statements and automatically inject tenant filtering. It provides a composite
 * interceptor that combines both the guard (validation) and the filter (modification)
 * functionalities.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer (platform-tenant module)</p>
 *
 * <h3>Interceptor Chain</h3>
 * <p>The composite interceptor executes in this order:</p>
 * <ol>
 *   <li>{@link TenantSqlGuardInterceptor} - Validates tenant isolation</li>
 *   <li>{@link TenantInterceptor} - Adds tenant_id conditions</li>
 * </ol>
 *
 * <h3>Configuration Properties</h3>
 * <table border="1">
 *   <tr>
 *     <th>Property</th>
 *     <th>Default</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td>brix.tenant.interceptor.enabled</td>
 *     <td>true</td>
 *     <td>Enable/disable SQL tenant filtering</td>
 *   </tr>
 *   <tr>
 *     <td>brix.tenant.guard.enabled</td>
 *     <td>true</td>
 *     <td>Enable/disable SQL guard validation</td>
 *   </tr>
 *   <tr>
 *     <td>brix.tenant.guard.fail-on-violation</td>
 *     <td>true</td>
 *     <td>Throw exception on violation (false = log only)</td>
 *   </tr>
 * </table>
 *
 * <h3>Environment-Specific Configuration</h3>
 * <pre>{@code
 * # Development (application-dev.yml)
 * brix:
 *   tenant:
 *     interceptor:
 *       enabled: true
 *     guard:
 *       enabled: true
 *       fail-on-violation: true  # Fail fast in development
 *
 * # Production (application-prod.yml)
 * brix:
 *   tenant:
 *     interceptor:
 *       enabled: true
 *     guard:
 *       enabled: true
 *       fail-on-violation: false  # Log only, don't crash
 * }</pre>
 *
 * <h3>How It Works</h3>
 * <p>This configuration uses Spring Boot's {@link HibernatePropertiesCustomizer} to inject
 * the composite interceptor into Hibernate's configuration. This ensures the interceptor
 * is properly initialized before any JPA operations occur.
 *
 * <h3>Testing Considerations</h3>
 * <p>For unit tests that don't need tenant isolation, set:</p>
 * <pre>{@code
 * brix.tenant.interceptor.enabled=false
 * brix.tenant.guard.enabled=false
 * }</pre>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantInterceptor
 * @see TenantSqlGuardInterceptor
 * @see HibernatePropertiesCustomizer
 */
@Configuration
public class TenantInterceptorConfig {

    private static final Logger log = LoggerFactory.getLogger(TenantInterceptorConfig.class);

    /**
     * Whether the tenant SQL interceptor is enabled.
     * When disabled, no automatic tenant filtering is applied.
     */
    @Value("${brix.tenant.interceptor.enabled:true}")
    private boolean interceptorEnabled;

    /**
     * Whether the SQL guard is enabled.
     * When disabled, SQL statements are not validated for tenant filtering.
     */
    @Value("${brix.tenant.guard.enabled:true}")
    private boolean guardEnabled;

    /**
     * Whether to throw exception on guard violation.
     * When false, violations are only logged as warnings.
     */
    @Value("${brix.tenant.guard.fail-on-violation:true}")
    private boolean failOnViolation;

    /**
     * Creates the TenantInterceptor bean.
     *
     * <p>This interceptor modifies SQL statements to add tenant_id conditions.
     *
     * @return the TenantInterceptor instance
     */
    @Bean
    public TenantInterceptor tenantInterceptor() {
        log.info("Creating TenantInterceptor: enabled={}", interceptorEnabled);
        return new TenantInterceptor();
    }

    /**
     * Creates the TenantSqlGuardInterceptor bean.
     *
     * <p>This interceptor validates SQL statements for proper tenant filtering.
     *
     * @return the TenantSqlGuardInterceptor instance
     */
    @Bean
    public TenantSqlGuardInterceptor tenantSqlGuardInterceptor() {
        log.info("Creating TenantSqlGuardInterceptor: enabled={}, failOnViolation={}", 
                 guardEnabled, failOnViolation);
        return new TenantSqlGuardInterceptor(guardEnabled, failOnViolation);
    }

    /**
     * Creates a composite StatementInspector that chains the guard and the filter.
     *
     * <p>The composite ensures that:
     * <ol>
     *   <li>Guard runs first to validate the original SQL</li>
     *   <li>Filter runs second to modify the SQL with tenant conditions</li>
     * </ol>
     *
     * @param guard  the SQL guard interceptor
     * @param filter the SQL filter interceptor
     * @return a composite StatementInspector
     */
    @Bean
    public StatementInspector compositeStatementInspector(
            TenantSqlGuardInterceptor guard,
            TenantInterceptor filter) {
        
        log.info("Creating CompositeStatementInspector: interceptorEnabled={}", interceptorEnabled);
        
        return sql -> {
            if (!interceptorEnabled) {
                return sql;
            }
            
            // Step 1: Guard validates the SQL (may throw exception)
            String guarded = guard.inspect(sql);
            
            // Step 2: Filter modifies the SQL with tenant conditions
            return filter.inspect(guarded);
        };
    }

    /**
     * Customizes Hibernate properties to register the composite StatementInspector.
     *
     * <p>This customizer runs during Spring Boot auto-configuration of JPA properties
     * and allows us to inject our StatementInspector before Hibernate initializes.
     *
     * <h4>Why HibernatePropertiesCustomizer?</h4>
     * <p>We use this approach instead of a custom {@code LocalContainerEntityManagerFactoryBean}
     * because:</p>
     * <ul>
     *   <li>It integrates cleanly with Spring Boot's auto-configuration</li>
     *   <li>It doesn't override other JPA customizations</li>
     *   <li>The StatementInspector is properly managed by Spring</li>
     * </ul>
     *
     * @param compositeInspector the composite statement inspector to register
     * @return the Hibernate properties customizer
     */
    @Bean
    public HibernatePropertiesCustomizer tenantHibernatePropertiesCustomizer(
            @Qualifier("compositeStatementInspector") StatementInspector compositeInspector) {
        
        return (Map<String, Object> hibernateProperties) -> {
            if (interceptorEnabled) {
                log.info("Registering CompositeStatementInspector with Hibernate");
                hibernateProperties.put(
                        AvailableSettings.STATEMENT_INSPECTOR, 
                        compositeInspector
                );
            } else {
                log.info("Tenant SQL interceptor disabled, skipping StatementInspector registration");
            }
        };
    }
}
