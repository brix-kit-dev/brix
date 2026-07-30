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
package io.brix.platform.tenant.config;

import java.util.Optional;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.task.TaskDecorator;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.brix.platform.auth.context.SecurityContextHolder;
import io.brix.platform.auth.flow.MfaLoginSupport;
import io.brix.platform.auth.jwt.JwtValidator;
import io.brix.platform.tenant.TenantCapabilityImpl;
import io.brix.platform.tenant.bootstrap.SuperAdminBootstrapProperties;
import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.core.SnowflakeIdGenerator;
import io.brix.platform.tenant.decorator.TenantTaskDecorator;
import io.brix.platform.tenant.filter.IdentityValidationFilter;
import io.brix.platform.tenant.outbox.PlatformTenantReliableEventBusCapability;
import io.brix.platform.tenant.repository.AuditLogRepository;
import io.brix.platform.tenant.repository.BizUserProfileRepository;
import io.brix.platform.tenant.repository.BootstrapStateRepository;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.InstallationQuotaRepository;
import io.brix.platform.tenant.repository.OrganizationRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.brix.platform.tenant.repository.PlatformTenantFirstOwnerProjectionRepository;
import io.brix.platform.tenant.repository.PlatformTenantInboxRepository;
import io.brix.platform.tenant.repository.PlatformTenantOutboxRepository;
import io.brix.platform.tenant.repository.SetupTokenRepository;
import io.brix.platform.tenant.repository.TenantAuditLogRepository;
import io.brix.platform.tenant.repository.TenantConfigRepository;
import io.brix.platform.tenant.repository.TenantInvitationRepository;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantPrincipalRepository;
import io.brix.platform.tenant.repository.TenantRepository;
import io.brix.platform.tenant.service.AuditService;
import io.brix.platform.tenant.service.AuditServiceImpl;
import io.brix.platform.tenant.service.BootstrapCompletionListener;
import io.brix.platform.tenant.service.FirstOwnerProjectionWriter;
import io.brix.platform.tenant.service.FirstOwnerInvitationService;
import io.brix.platform.tenant.service.JpaFirstOwnerProjectionWriter;
import io.brix.platform.tenant.service.PlatformBootstrapAdministrationService;
import io.brix.platform.tenant.service.PlatformIdentityAdministrationService;
import io.brix.platform.tenant.service.PlatformMfaLoginSupport;
import io.brix.platform.tenant.service.TenantAdministrationService;
import io.brix.platform.tenant.service.TenantConfigCapabilityImpl;
import io.brix.platform.tenant.service.TenantFirstOwnerAcceptedProjectionService;
import io.brix.platform.tenant.service.TenantProvisioningService;
import io.brix.platform.tenant.service.TenantProvisioningServiceImpl;
import io.brix.platform.tenant.service.TenantSettingsService;
import io.brix.platform.tenant.service.TenantSettingsServiceImpl;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.JwtIssuerCapability;
import io.runtime.sdk.capability.PasswordCapability;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.TenantCapability;
import io.runtime.sdk.capability.TenantConfigCapability;
import io.runtime.sdk.capability.NotificationCapability;
import io.runtime.sdk.capability.TotpCapability;

/**
 * Auto-configuration for multi-tenant infrastructure.
 *
 * <p>This configuration class sets up all the core beans required for multi-tenancy
 * support in the Brix Platform. It is designed to work as a Spring Boot auto-configuration,
 * automatically activating when the platform-tenant module is on the classpath.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer - Platform Commons Auto-Configuration</p>
 *
 * <h3>Provided Beans</h3>
 * <ul>
 *   <li>{@link IdGenerator} - Snowflake-based distributed ID generator</li>
 *   <li>{@link TenantProvisioningService} - Tenant lifecycle management</li>
 *   <li>{@link AuditService} - Audit event logging</li>
 *   <li>{@link TaskDecorator} - Tenant context propagation for async tasks</li>
 *   <li>{@link Executor} - Configured thread pool with tenant context support</li>
 *   <li>SQL Interceptors - Automatic tenant filtering (via TenantInterceptorConfig)</li>
 * </ul>
 *
 * <h3>Configuration Properties</h3>
 * <ul>
 *   <li>{@code brix.tenant.worker-id} - Snowflake worker ID (default: 1)</li>
 *   <li>{@code brix.tenant.datacenter-id} - Snowflake datacenter ID (default: 1)</li>
 *   <li>{@code brix.tenant.async.core-pool-size} - Async executor core pool size (default: 10)</li>
 *   <li>{@code brix.tenant.async.max-pool-size} - Async executor max pool size (default: 50)</li>
 *   <li>{@code brix.tenant.async.queue-capacity} - Async executor queue capacity (default: 100)</li>
 *   <li>{@code brix.tenant.interceptor.enabled} - Enable SQL tenant filtering (default: true)</li>
 *   <li>{@code brix.tenant.guard.enabled} - Enable SQL guard validation (default: true)</li>
 *   <li>{@code brix.tenant.guard.fail-on-violation} - Throw exception on violation (default: true)</li>
 * </ul>
 *
 * <h3>Conditional Bean Creation</h3>
 * <p>All beans use {@code @ConditionalOnMissingBean} to allow custom implementations
 * to override defaults. For example, a Kubernetes deployment might provide a custom
 * IdGenerator that derives worker ID from pod ordinal.
 *
 * <h3>Component Scanning</h3>
 * <p>This configuration enables:
 * <ul>
 *   <li>JPA repositories in {@code io.brix.platform.tenant.repository}</li>
 *   <li>Entity scanning in {@code io.brix.platform.tenant.entity}</li>
 *   <li>Component scanning for services, validation, and interceptors</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <p>This auto-configuration is activated automatically when platform-tenant
 * is on the classpath. No explicit configuration is required for basic usage.
 *
 * <pre>{@code
 * // In application.yml (optional customization):
 * brix:
 *   tenant:
 *     worker-id: ${HOSTNAME:1}
 *     datacenter-id: 1
 *     async:
 *       core-pool-size: 20
 *       max-pool-size: 100
 *       queue-capacity: 500
 *     interceptor:
 *       enabled: true
 *     guard:
 *       enabled: true
 *       fail-on-violation: true
 * }</pre>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantProvisioningService
 * @see AuditService
 * @see IdGenerator
 * @see TenantTaskDecorator
 * @see io.brix.platform.tenant.interceptor.TenantInterceptorConfig
 */
@AutoConfiguration
@EnableJpaRepositories(basePackages = "io.brix.platform.tenant.repository")
@EntityScan(basePackages = "io.brix.platform.tenant.entity")
@EnableConfigurationProperties(SuperAdminBootstrapProperties.class)
@ComponentScan(basePackages = {
    "io.brix.platform.tenant.aspect",
    "io.brix.platform.tenant.controller",
    "io.brix.platform.tenant.service",
    "io.brix.platform.tenant.validation",
    "io.brix.platform.tenant.interceptor",
    "io.brix.platform.tenant.filter"
})
public class TenantAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TenantAutoConfiguration.class);

    // ========================================================================
    // Configuration Properties
    // ========================================================================

    /**
     * Snowflake worker ID for ID generation.
     * Should be unique across all instances in the distributed system.
     * Valid range: 0-1023.
     */
    @Value("${brix.tenant.worker-id:1}")
    private long workerId;

    /**
     * Async executor core pool size.
     */
    @Value("${brix.tenant.async.core-pool-size:10}")
    private int corePoolSize;

    /**
     * Async executor maximum pool size.
     */
    @Value("${brix.tenant.async.max-pool-size:50}")
    private int maxPoolSize;

    /**
     * Async executor queue capacity.
     */
    @Value("${brix.tenant.async.queue-capacity:100}")
    private int queueCapacity;

    // ========================================================================
    // Core Infrastructure Beans
    // ========================================================================

    /**
     * Creates the Snowflake ID generator bean.
     *
     * <p>The Snowflake algorithm generates time-ordered, globally unique 64-bit IDs.
     * The worker ID and datacenter ID should be configured based on deployment topology.
     *
     * <h4>Kubernetes Deployment</h4>
     * <p>For Kubernetes deployments, consider deriving worker ID from:
     * <ul>
     *   <li>StatefulSet pod ordinal (recommended)</li>
     *   <li>Hash of pod name or IP</li>
     *   <li>External ID assignment service</li>
     * </ul>
     *
     * @return configured IdGenerator instance
     */
    @Bean
    @ConditionalOnMissingBean(IdGenerator.class)
    public IdGenerator idGenerator() {
        log.info("Initializing SnowflakeIdGenerator: workerId={}", workerId);
        return new SnowflakeIdGenerator(workerId);
    }

    /**
     * Creates the TenantCapability bean (SDK contract implementation).
     *
     * <p>Bridges the Runtime SDK {@link TenantCapability} contract to the
     * platform-tenant infrastructure via {@link TenantCapabilityImpl}, which
     * delegates to {@link io.brix.platform.common.tenant.TenantContext} ThreadLocal.</p>
     *
     * <p>Services should inject {@code TenantCapability} instead of calling
     * {@code TenantContext} directly, to comply with the capability contract model.</p>
     *
     * @return configured TenantCapability implementation
     */
    @Bean
    @ConditionalOnMissingBean(TenantCapability.class)
    public TenantCapability tenantCapability(
            @Value("${brix.tenant.default-id:default}") String defaultTenantId,
            ObjectProvider<SecurityContextHolder> securityContextHolderProvider) {
        log.info("Registering TenantCapabilityImpl: defaultTenantId={}", defaultTenantId);
        return new TenantCapabilityImpl(defaultTenantId, securityContextHolderProvider.getIfAvailable());
    }

    /**
     * Creates the TenantTaskDecorator bean for async context propagation.
     *
     * <p>This decorator ensures tenant context (tenant ID, user ID) is properly
     * propagated when tasks are executed asynchronously via Spring's @Async
     * annotation or TaskExecutor.
     *
     * @return TenantTaskDecorator instance
     */
    @Bean
    @ConditionalOnMissingBean(name = "tenantTaskDecorator")
    public TaskDecorator tenantTaskDecorator() {
        log.debug("Registering TenantTaskDecorator for async context propagation");
        return new TenantTaskDecorator();
    }

    /**
     * Creates the tenant-aware async executor.
     *
     * <p>This executor is configured with TenantTaskDecorator to ensure tenant
     * context is propagated to async threads. Use this executor for all async
     * operations that require tenant context.
     *
     * <h4>Configuration</h4>
     * <ul>
     *   <li>Core pool size: Number of threads kept alive even when idle</li>
     *   <li>Max pool size: Maximum number of threads allowed</li>
     *   <li>Queue capacity: Number of tasks queued before creating new threads</li>
     *   <li>Thread name prefix: For easier debugging and monitoring</li>
     * </ul>
     *
     * @param tenantTaskDecorator the decorator for context propagation
     * @return configured ThreadPoolTaskExecutor
     */
    @Bean(name = "tenantAwareExecutor")
    @ConditionalOnMissingBean(name = "tenantAwareExecutor")
    public Executor tenantAwareExecutor(TaskDecorator tenantTaskDecorator) {
        log.info("Initializing tenant-aware async executor: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                corePoolSize, maxPoolSize, queueCapacity);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("tenant-async-");
        executor.setTaskDecorator(tenantTaskDecorator);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        return executor;
    }

    // ========================================================================
    // Service Beans
    // Note: Services are also registered via @Service annotation and component
    // scanning. These explicit beans serve as documentation and allow easy
    // customization through @ConditionalOnMissingBean.
    // ========================================================================

    /**
     * Creates the TenantProvisioningService bean.
     *
     * <p>This service handles complete tenant lifecycle including creation
     * (with member and organization), suspension, and activation.
     *
     * @param tenantRepository repository for tenant operations
     * @param tenantMemberRepository repository for member operations
     * @param organizationRepository repository for organization operations
     * @param identityRepository repository for identity validation
     * @param idGenerator ID generator for new entities
     * @return configured TenantProvisioningService
     */
    @Bean
    @ConditionalOnMissingBean(TenantProvisioningService.class)
    public TenantProvisioningService tenantProvisioningService(
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository,
            InstallationQuotaRepository installationQuotaRepository,
            OrganizationRepository organizationRepository,
            BizUserProfileRepository bizUserProfileRepository,
            IdentityRepository identityRepository,
            IdGenerator idGenerator) {
        log.debug("Registering TenantProvisioningService");
        return new TenantProvisioningServiceImpl(
            tenantRepository,
            tenantMemberRepository,
            installationQuotaRepository,
            organizationRepository,
            bizUserProfileRepository,
            identityRepository,
            idGenerator
        );
    }

    /**
     * Creates the FIRST_OWNER invitation workflow service.
     *
     * @param invitationRepository tenant invitation repository
     * @param tenantRepository tenant repository
     * @param tenantMemberRepository tenant member repository
     * @param installationQuotaRepository installation quota repository
     * @param bizUserProfileRepository profile repository
     * @param platformTenantEventBusCapability owner-scoped reliable EventBus
     * @param notificationCapabilityProvider managed notification capability provider
     * @param idGenerator ID generator
     * @return configured FIRST_OWNER invitation service
     */
    @Bean
    @ConditionalOnMissingBean(FirstOwnerInvitationService.class)
    public FirstOwnerInvitationService firstOwnerInvitationService(
            TenantInvitationRepository invitationRepository,
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository,
            InstallationQuotaRepository installationQuotaRepository,
            BizUserProfileRepository bizUserProfileRepository,
            @Qualifier("platformTenantEventBusCapability") EventBusCapability platformTenantEventBusCapability,
            TenantAuditLogRepository auditLogRepository,
            ObjectProvider<NotificationCapability> notificationCapabilityProvider,
            IdGenerator idGenerator,
            ObjectMapper objectMapper) {
        return new FirstOwnerInvitationService(
            invitationRepository,
            tenantRepository,
            tenantMemberRepository,
            installationQuotaRepository,
            bizUserProfileRepository,
            platformTenantEventBusCapability,
            auditLogRepository,
            Optional.ofNullable(notificationCapabilityProvider.getIfAvailable()),
            idGenerator,
            objectMapper);
    }

    /**
     * Creates the owner-scoped reliable EventBus for platform-tenant producers.
     *
     * @param outboxRepository owner canonical outbox repository
     * @param objectMapper JSON mapper
     * @return owner-scoped EventBus capability
     */
    @Bean(name = "platformTenantEventBusCapability")
    @ConditionalOnMissingBean(name = "platformTenantEventBusCapability")
    public EventBusCapability platformTenantEventBusCapability(
            PlatformTenantOutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        return new PlatformTenantReliableEventBusCapability(outboxRepository, objectMapper);
    }

    /**
     * Creates the FIRST_OWNER accepted projection writer.
     *
     * @param projectionRepository projection side-effect repository
     * @return projection writer
     */
    @Bean
    @ConditionalOnMissingBean(FirstOwnerProjectionWriter.class)
    public FirstOwnerProjectionWriter firstOwnerProjectionWriter(
            PlatformTenantFirstOwnerProjectionRepository projectionRepository) {
        return new JpaFirstOwnerProjectionWriter(projectionRepository);
    }

    /**
     * Creates the Consumer Owner handler for {@code TenantFirstOwnerAccepted}.
     *
     * @param inboxRepository canonical Inbox repository
     * @param projectionWriter projection side-effect writer
     * @return Consumer handler service
     */
    @Bean
    @ConditionalOnMissingBean(TenantFirstOwnerAcceptedProjectionService.class)
    public TenantFirstOwnerAcceptedProjectionService tenantFirstOwnerAcceptedProjectionService(
            PlatformTenantInboxRepository inboxRepository,
            FirstOwnerProjectionWriter projectionWriter) {
        return new TenantFirstOwnerAcceptedProjectionService(inboxRepository, projectionWriter);
    }

    /**
     * Registers the TenantAdministration internal contract implementation owned
     * by platform-tenant.
     *
     * @param tenantProvisioningService tenant provisioning service
     * @param firstOwnerInvitationService FIRST_OWNER invitation service
     * @return internal contract implementation
     */
    @Bean
    @ConditionalOnMissingBean(io.brix.platform.tenant.internal.TenantAdministration.class)
    public io.brix.platform.tenant.internal.TenantAdministration tenantAdministration(
            TenantProvisioningService tenantProvisioningService,
            FirstOwnerInvitationService firstOwnerInvitationService) {
        return new TenantAdministrationService(
            tenantProvisioningService,
            firstOwnerInvitationService);
    }

    /**
     * Registers the Bootstrap administration internal contract implementation.
     *
     * @return internal contract implementation
     */
    @Bean
    @ConditionalOnMissingBean(io.brix.platform.tenant.internal.PlatformBootstrapAdministration.class)
    public io.brix.platform.tenant.internal.PlatformBootstrapAdministration platformBootstrapAdministration(
            SuperAdminBootstrapProperties properties,
            BootstrapStateRepository bootstrapStateRepository,
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            SetupTokenRepository setupTokenRepository,
            IdGenerator idGenerator,
            ObjectProvider<JwtIssuerCapability> jwtIssuerCapabilityProvider,
            ObjectProvider<NotificationCapability> notificationCapabilityProvider,
            AuditService auditService) {
        return new PlatformBootstrapAdministrationService(
                properties,
                bootstrapStateRepository,
                identityRepository,
                platformAdminRepository,
                setupTokenRepository,
                idGenerator,
                Optional.ofNullable(jwtIssuerCapabilityProvider.getIfAvailable()),
                Optional.ofNullable(notificationCapabilityProvider.getIfAvailable()),
                auditService);
    }

    /**
     * Registers the platform identity setup internal contract implementation.
     *
     * @return internal contract implementation
     */
    @Bean
    @ConditionalOnMissingBean(io.brix.platform.tenant.internal.PlatformIdentityAdministration.class)
    public io.brix.platform.tenant.internal.PlatformIdentityAdministration platformIdentityAdministration(
            SetupTokenRepository setupTokenRepository,
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            ObjectProvider<PasswordCapability> passwordCapabilityProvider,
            ObjectProvider<TotpCapability> totpCapabilityProvider,
            ObjectProvider<SecretEncryptionCapability> secretEncryptionCapabilityProvider,
            BootstrapCompletionListener bootstrapCompletionListener,
            ApplicationEventPublisher eventPublisher,
            AuditService auditService) {
        return new PlatformIdentityAdministrationService(
                setupTokenRepository,
                identityRepository,
                platformAdminRepository,
                Optional.ofNullable(passwordCapabilityProvider.getIfAvailable()),
                Optional.ofNullable(totpCapabilityProvider.getIfAvailable()),
                Optional.ofNullable(secretEncryptionCapabilityProvider.getIfAvailable()),
                bootstrapCompletionListener,
                eventPublisher,
                auditService);
    }

    @Bean
    @ConditionalOnMissingBean(MfaLoginSupport.class)
    public MfaLoginSupport platformMfaLoginSupport(
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            ObjectProvider<JwtValidator> jwtValidatorProvider,
            ObjectProvider<JwtIssuerCapability> jwtIssuerCapabilityProvider,
            ObjectProvider<TotpCapability> totpCapabilityProvider,
            ObjectProvider<SecretEncryptionCapability> secretEncryptionCapabilityProvider) {
        return new PlatformMfaLoginSupport(
                identityRepository,
                platformAdminRepository,
                Optional.ofNullable(jwtValidatorProvider.getIfAvailable()),
                Optional.ofNullable(jwtIssuerCapabilityProvider.getIfAvailable()),
                Optional.ofNullable(totpCapabilityProvider.getIfAvailable()),
                Optional.ofNullable(secretEncryptionCapabilityProvider.getIfAvailable()));
    }

    /**
     * Creates the AuditService bean.
     *
     * <p>This service handles synchronous audit logging to the biz_audit_log table.
     * Uses REQUIRES_NEW transaction propagation to ensure logs are committed
     * independently of the calling transaction.
     *
     * @param auditLogRepository repository for audit log persistence
     * @param idGenerator ID generator for audit log entries
     * @return configured AuditService
     */
    @Bean
    @ConditionalOnMissingBean(AuditService.class)
    public AuditService auditService(
            AuditLogRepository auditLogRepository,
            IdGenerator idGenerator) {
        log.debug("Registering AuditService");
        return new AuditServiceImpl(auditLogRepository, idGenerator);
    }

    /**
     * Phase 2 / C-4 — Registers the {@link ViewModeCapability} bean.
     *
     * <p>Wires the capability contract to the platform-auth + audit
     * infrastructure so platform admins may explicitly switch view
     * perspective and have every switch recorded for compliance.</p>
     *
     * @param securityContextHolder    thread-local security context
     * @param jwtIssuerCapability      issues platform-admin viewing tokens
     * @param identityTenantCapability looks up the originating identity record
     * @param auditService             records the view-mode switch
     * @return configured ViewModeCapability implementation
     */
    @Bean
    @ConditionalOnMissingBean(io.runtime.sdk.capability.ViewModeCapability.class)
    @ConditionalOnBean({
            io.runtime.sdk.capability.JwtIssuerCapability.class,
            io.runtime.sdk.capability.IdentityTenantCapability.class
    })
    public io.runtime.sdk.capability.ViewModeCapability viewModeCapability(
            SecurityContextHolder securityContextHolder,
            io.runtime.sdk.capability.JwtIssuerCapability jwtIssuerCapability,
            io.runtime.sdk.capability.IdentityTenantCapability identityTenantCapability,
            AuditService auditService) {
        log.info("Registering ViewModeCapabilityImpl (Phase 2 / C-4)");
        return new io.brix.platform.tenant.service.ViewModeCapabilityImpl(
                securityContextHolder,
                jwtIssuerCapability,
                identityTenantCapability,
                auditService);
    }

    /**
     * Creates the TenantSettingsService bean.
     *
     * <p>This service handles tenant settings, user preferences,
     * branding, and namespace configuration with three-layer merge.</p>
     *
     * @param tenantRepository         repository for tenant entities
     * @param tenantConfigRepository   repository for tenant config entries
     * @param bizUserProfileRepository repository for user profiles (preferences)
     * @param idGenerator              ID generator for new config entries
     * @param objectMapper             JSON serializer for JSONB fields
     * @return configured TenantSettingsService
     */
    @Bean
    @ConditionalOnMissingBean(TenantSettingsService.class)
    public TenantSettingsService tenantSettingsService(
            TenantRepository tenantRepository,
            TenantConfigRepository tenantConfigRepository,
            BizUserProfileRepository bizUserProfileRepository,
            IdGenerator idGenerator,
            ObjectMapper objectMapper) {
        log.debug("Registering TenantSettingsService");
        return new TenantSettingsServiceImpl(
            tenantRepository,
            tenantConfigRepository,
            bizUserProfileRepository,
            idGenerator,
            objectMapper
        );
    }

    /**
     * Creates the TenantConfigCapability bean (SDK contract implementation).
     *
     * <p>Bridges the Runtime SDK {@link TenantConfigCapability} contract to
     * the platform-tenant {@link TenantSettingsService}. Resolves current
     * tenant/user from TenantContext ThreadLocal.</p>
     *
     * @param settingsService the tenant settings service
     * @param objectMapper    JSON serializer for value conversion
     * @return configured TenantConfigCapability implementation
     */
    @Bean
    @ConditionalOnMissingBean(TenantConfigCapability.class)
    public TenantConfigCapability tenantConfigCapability(
            TenantSettingsService settingsService,
            ObjectMapper objectMapper) {
        log.debug("Registering TenantConfigCapabilityImpl");
        return new TenantConfigCapabilityImpl(settingsService, objectMapper);
    }

    // ========================================================================
    // Security Filters
    // ========================================================================

    /**
     * Creates the IdentityValidationFilter bean.
     *
     * <p>P2-8 (R16.14): Validates that JWT mid/pid claims reference real records
     * in sys_tenant_member / sys_tenant_principal. Runs after SecurityContextFilter
     * to verify identity existence post-JWT-crypto-validation.</p>
     *
     * <p>Conditionally enabled via {@code brix.tenant.identity-validation.enabled}
     * (default: true). Disable for development environments where the database
     * may not have all member/principal records populated.</p>
     *
     * @param securityContextHolder    thread-local security context
     * @param tenantMemberRepository   repository for member lookups
     * @param tenantPrincipalRepository repository for principal lookups
     * @return configured IdentityValidationFilter
     */
    @Bean
    @ConditionalOnMissingBean(IdentityValidationFilter.class)
    @ConditionalOnProperty(
            prefix = "brix.tenant.identity-validation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public IdentityValidationFilter identityValidationFilter(
            SecurityContextHolder securityContextHolder,
            TenantMemberRepository tenantMemberRepository,
            TenantPrincipalRepository tenantPrincipalRepository) {
        log.info("Registering IdentityValidationFilter (P2-8: mid/pid dual-branch verification)");
        return new IdentityValidationFilter(
                securityContextHolder,
                tenantMemberRepository,
                tenantPrincipalRepository);
    }

}
