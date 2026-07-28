/*
 * Copyright 2026 Brix Authors
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
package io.brix.architecture.guard.profiles;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import io.brix.architecture.guard.rules.ConfigViaCapabilityRule;
import io.brix.architecture.guard.rules.CrossModuleViaCapabilityRule;
import io.brix.architecture.guard.rules.EventsViaCapabilityRule;
import io.brix.architecture.guard.rules.LoggingViaCapabilityRule;
import io.brix.architecture.guard.rules.NoDirectHttpClientsRule;
import io.brix.architecture.guard.rules.NoDirectPluginDependencyRule;
import io.brix.architecture.guard.rules.NoInfraAdapterRule;
import io.brix.architecture.guard.rules.NoMiddlewareClientsRule;
import io.brix.architecture.guard.rules.NoPlatformTenantRelationshipAccessRule;
import io.brix.architecture.guard.rules.NoRestControllerInCoreRule;
import io.brix.architecture.guard.rules.NoSpringContainerApiRule;
import io.brix.architecture.guard.rules.OnlyRuntimeSdkApiRule;
import io.brix.architecture.guard.rules.DataOwnershipRule;
import io.brix.architecture.guard.rules.NoCyclicDependencyRule;
import io.brix.architecture.guard.rules.OutboxConsistencyRule;
import io.brix.architecture.guard.rules.SecurityBoundaryRule;

/**
 * Plugin Layer Architecture Constraints
 *
 * <p>Enforces all 12 architecture red lines. Every plugin module
 * must pass these constraints to ensure business code only accesses
 * infrastructure through Capability interfaces.</p>
 *
 * <h2>Red Lines Covered</h2>
 * <ul>
 *   <li>Red Line 1: Plugins must not directly depend on infrastructure</li>
 *   <li>Red Line 2: Plugins must not bypass Runtime Shell</li>
 *   <li>Red Line 5: Plugins must support independent start/stop</li>
 *   <li>Red Line 8: Data isolation (Data Ownership)</li>
 *   <li>Red Line 11: No circular dependencies</li>
 *   <li>Red Line 12: Security boundary cannot be bypassed</li>
 *   <li>Red Line 13: Cross-service event consistency (Transactional Outbox)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @AnalyzeClasses(packages = "io.brix.app.booking")
 * class ArchitectureTest {
 *     @ArchTest
 *     static final ArchTests rules = BrixArchitectureRules.pluginProfile();
 * }
 * }</pre>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
public class PluginProfile {

    // ==================== Rule 1: No infra adapter dependency ====================

    @ArchTest
    static final ArchRule noInfraAdapterDependency = NoInfraAdapterRule.rule();

    // ==================== Rule 2: No middleware clients ====================

    @ArchTest
    static final ArchRule noSpringKafka = NoMiddlewareClientsRule.noSpringKafka();

    @ArchTest
    static final ArchRule noApacheKafka = NoMiddlewareClientsRule.noApacheKafka();

    @ArchTest
    static final ArchRule noSpringDataRedis = NoMiddlewareClientsRule.noSpringDataRedis();

    @ArchTest
    static final ArchRule noJedis = NoMiddlewareClientsRule.noJedis();

    @ArchTest
    static final ArchRule noLettuce = NoMiddlewareClientsRule.noLettuce();

    @ArchTest
    static final ArchRule noSpringAmqp = NoMiddlewareClientsRule.noSpringAmqp();

    // ==================== Rule 3: No HTTP clients ====================

    @ArchTest
    static final ArchRule noRestTemplate = NoDirectHttpClientsRule.noRestTemplate();

    @ArchTest
    static final ArchRule noWebClient = NoDirectHttpClientsRule.noWebClient();

    @ArchTest
    static final ArchRule noOpenFeign = NoDirectHttpClientsRule.noOpenFeign();

    @ArchTest
    static final ArchRule noOkHttp = NoDirectHttpClientsRule.noOkHttp();

    @ArchTest
    static final ArchRule noJdkHttpClient = NoDirectHttpClientsRule.noJdkHttpClient();

    // ==================== Rule 4: Cross-module via Capability ====================

    @ArchTest
    static final ArchRule crossModuleViaCapability = CrossModuleViaCapabilityRule.rule();

    /** No direct plugin-to-plugin dependencies */
    @ArchTest
    static final ArchRule noDirectPluginDependency = NoDirectPluginDependencyRule.rule();

    // ==================== Rule 5: No Spring container API ====================

    @ArchTest
    static final ArchRule noSpringContainerApi = NoSpringContainerApiRule.rule();

    // ==================== Rule 6: Config via Capability ====================

    /** No standard stream access (System.out/err/in) */
    @ArchTest
    static final ArchRule configViaCapability = ConfigViaCapabilityRule.rule();

    /** No direct System.getenv() / System.getProperty() */
    @ArchTest
    static final ArchRule noSystemEnvAccess = ConfigViaCapabilityRule.noSystemEnvAccess();

    /** No @Value annotation */
    @ArchTest
    static final ArchRule noSpringValue = ConfigViaCapabilityRule.noSpringValue();

    // ==================== Rule 7: Logging constraints ====================

    /** No generic exceptions */
    @ArchTest
    static final ArchRule loggingViaCapability = LoggingViaCapabilityRule.rule();

    /** Domain/Entity layers must not use LoggerFactory */
    @ArchTest
    static final ArchRule noLoggerInDomain = LoggingViaCapabilityRule.noLoggerInDomain();

    // ==================== Rule 8: Events via EventBusCapability ====================

    @ArchTest
    static final ArchRule eventsViaCapability = EventsViaCapabilityRule.rule();

    /** No @EventListener annotation */
    @ArchTest
    static final ArchRule noEventListenerAnnotation = EventsViaCapabilityRule.noEventListenerAnnotation();

    /** No extending ApplicationEvent */
    @ArchTest
    static final ArchRule noApplicationEventSubclass = EventsViaCapabilityRule.noApplicationEventSubclass();

    // ==================== Rule 9: No REST controllers in core modules ====================

    /** REST controllers must be in -server modules, not -core */
    @ArchTest
    static final ArchRule noRestControllerInCore = NoRestControllerInCoreRule.rule();

    // ==================== Rule 10: Core modules only depend on runtime-sdk-api ====================

    /** No dependency on runtime-sdk implementation classes */
    @ArchTest
    static final ArchRule noRuntimeSdkImpl = OnlyRuntimeSdkApiRule.noRuntimeSdkImpl();

    /** No dependency on runtime-sdk internal classes */
    @ArchTest
    static final ArchRule noRuntimeSdkInternal = OnlyRuntimeSdkApiRule.noRuntimeSdkInternal();

    /** No dependency on runtime-orchestrator (Layer 2.5) */
    @ArchTest
    static final ArchRule noRuntimeOrchestrator = OnlyRuntimeSdkApiRule.noRuntimeOrchestrator();

    // ==================== Rule 11: Data Ownership (Red Line 8) ====================

    /** Plugins must not access other plugins' repositories */
    @ArchTest
    static final ArchRule noCrossPluginRepositoryAccess = DataOwnershipRule.noCrossPluginRepositoryAccess();

    /** Plugins must not import platform tenant relationship internals */
    @ArchTest
    static final ArchRule noPlatformTenantRelationshipAccess = NoPlatformTenantRelationshipAccessRule.rule();

    // ==================== Rule 12: No Cyclic Dependencies (Red Line 11) ====================

    /** Core layer must not depend on Server layer */
    @ArchTest
    static final ArchRule coreNotDependOnServer = NoCyclicDependencyRule.coreNotDependOnServer();

    /** Core layer must not depend on Adapter layer */
    @ArchTest
    static final ArchRule coreNotDependOnAdapter = NoCyclicDependencyRule.coreNotDependOnAdapter();

    /** Domain layer must be pure */
    @ArchTest
    static final ArchRule domainNotDependOnInfrastructure = NoCyclicDependencyRule.domainNotDependOnInfrastructure();

    /** SDK API must not depend on implementation */
    @ArchTest
    static final ArchRule sdkApiNotDependOnImpl = NoCyclicDependencyRule.sdkApiNotDependOnImpl();

    // ==================== Rule 13: Security Boundary (Red Line 12) ====================

    /** Plugins must not use JWT libraries directly */
    @ArchTest
    static final ArchRule noDirectJwtUsage = SecurityBoundaryRule.noDirectJwtUsage();

    /** Plugins must not use Auth0 libraries directly */
    @ArchTest
    static final ArchRule noDirectAuth0Usage = SecurityBoundaryRule.noDirectAuth0Usage();

    /** Plugins must not use crypto libraries directly */
    @ArchTest
    static final ArchRule noDirectCryptoUsage = SecurityBoundaryRule.noDirectCryptoUsage();

    /** Plugins must not implement OAuth2 client directly */
    @ArchTest
    static final ArchRule noDirectOAuth2ClientUsage = SecurityBoundaryRule.noDirectOAuth2ClientUsage();

    // ==================== Rule 14: Transactional Outbox (Red Line 13) ====================

    /** Plugins must not use KafkaTemplate directly */
    @ArchTest
    static final ArchRule noDirectKafkaTemplateUsage = OutboxConsistencyRule.noDirectKafkaTemplateUsage();

    /** Plugins must not use RabbitTemplate directly */
    @ArchTest
    static final ArchRule noDirectRabbitTemplateUsage = OutboxConsistencyRule.noDirectRabbitTemplateUsage();

    /** Plugins must not create KafkaProducer directly */
    @ArchTest
    static final ArchRule noDirectKafkaProducerUsage = OutboxConsistencyRule.noDirectKafkaProducerUsage();

    /** Plugins must not use StreamBridge directly */
    @ArchTest
    static final ArchRule noDirectStreamBridgeUsage = OutboxConsistencyRule.noDirectStreamBridgeUsage();

    /** Plugins must not use JmsTemplate directly */
    @ArchTest
    static final ArchRule noDirectJmsTemplateUsage = OutboxConsistencyRule.noDirectJmsTemplateUsage();

    /** Plugins must not depend on Outbox or Relay implementation packages */
    @ArchTest
    static final ArchRule noDirectOutboxImplementationDependency =
            OutboxConsistencyRule.noDirectOutboxImplementationDependency();

    /** Plugins must not depend on broker SDK packages */
    @ArchTest
    static final ArchRule noBrokerSdkDependencies = OutboxConsistencyRule.noBrokerSdkDependencies();
}
