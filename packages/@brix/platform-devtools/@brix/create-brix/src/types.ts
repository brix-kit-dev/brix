/**
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
 * @file types.ts
 * @description Type Definitions
 * @module @brix-sdk/create-brix
 * @version 3.0
 * 
 * v3.0 Changes:
 * - Added 'app' generation type for creating business application modules (app-*)
 * - Added AppConfig configuration interface following v3.0 Runtime Shell architecture
 * - Added AppTemplateContext template context
 */

// =====================================================
// Generation Types
// =====================================================

/**
 * Generation Type
 * 
 * Runtime Shell v3.0.10 Phase 0 freezes all current template entrypoints:
 * - plugin: legacy/migration-only
 * - service: legacy/migration-only
 * - app: legacy template entry until governed v3.0.10 templates are delivered
 */
export type GenerateType = 'plugin' | 'service' | 'app';

/**
 * Governed scaffold kind delivered by Runtime Shell v3.0.10 Phase 7.
 */
export type GovernedScaffoldKind = 'plugin' | 'operational' | 'ui';

/**
 * Plugin Type
 * - web: Web-only plugin
 * - mobile: Mobile-only plugin
 * - full: Full-platform plugin (Web + Mobile)
 */
export type PluginType = 'web' | 'mobile' | 'full';

/**
 * Backend Framework
 */
export type BackendFramework = 'spring-boot' | 'none';

/**
 * Frontend Framework
 */
export type FrontendFramework = 'react' | 'vue';

// =====================================================
// Plugin Configuration
// =====================================================

/**
 * Plugin Configuration
 */
export interface PluginConfig {
  /** Plugin name (kebab-case, starts with plugin-) */
  name: string;
  /** Display name */
  displayName: string;
  /** Description */
  description: string;
  /** Plugin type */
  type: PluginType;
  /** Backend framework */
  backend: BackendFramework;
  /** Frontend framework */
  frontend: FrontendFramework;
  /** Author */
  author: string;
  /** Version number */
  version: string;
  /** Use TypeScript */
  typescript: boolean;
  /** Include example code */
  includeExamples: boolean;
  /** Output directory */
  outputDir: string;
  
  // v2.1 additions
  /** Flyway prefix (3-digit number) */
  flywayPrefix: string;
  /** Event schema version */
  schemaVersion: string;
  /** Include Outbox table template */
  includeOutbox: boolean;
  /** Include multi-tenant support */
  includeTenantSupport: boolean;
  /** Include Kafka event support */
  includeKafka: boolean;
  /** Include Flyway migration support */
  includeFlyway: boolean;
  /** Include API module */
  withApi: boolean;
  /** Include Web frontend module */
  withWeb: boolean;
  /** Include Mobile frontend module */
  withMobile: boolean;
  
  // v2.1.2 additions - Module Federation port configuration
  /** Web frontend dev server port */
  webPort?: number;
  /** Mobile frontend dev server port */
  mobilePort?: number;
  
  /** Index signature for template rendering */
  [key: string]: unknown;
}

// =====================================================
// Service Configuration
// =====================================================

/**
 * Service Configuration
 */
export interface ServiceConfig {
  /** Service name (kebab-case, without brix-service- prefix) */
  name: string;
  /** Java package name (for generating Java code, handling reserved words) */
  javaPackageName?: string;
  /** Full service name (auto-generated) */
  fullName: string;
  /** Display name */
  displayName: string;
  /** Description */
  description: string;
  /** Service port */
  port: number;
  /** Author */
  author: string;
  /** Version number */
  version: string;
  /** Output directory */
  outputDir: string;
  
  // Dependent plugins
  /** Plugin list */
  plugins: PluginDependency[];
  
  // Docker/K8s configuration
  /** Generate Docker configuration */
  withDocker: boolean;
  /** Generate Kubernetes configuration */
  withK8s: boolean;
  
  // Base configuration
  /** Base URL */
  baseUrl: string;
  /** Heartbeat interval */
  heartbeatInterval: string;
  /** API Key */
  apiKey: string;
  /** API Secret */
  apiSecret: string;
  /** Index signature for template rendering */
  [key: string]: unknown;
}

/**
 * Plugin Dependency
 */
export interface PluginDependency {
  /** Plugin name */
  name: string;
  /** Version number */
  version: string;
  /** GroupId */
  groupId: string;
  /** ArtifactId (auto-generated: {name}-core) */
  artifactId: string;
}

// =====================================================
// Template Context
// =====================================================

/**
 * Plugin Template Context
 */
export interface PluginTemplateContext extends PluginConfig {
  /** Current date */
  date: string;
  /** Package name */
  packageName: string;
  /** Class name prefix */
  classPrefix: string;
  /** Brix SDK version used by generated manifests and packages */
  sdkVersion: string;
  /** Index signature for template rendering */
  [key: string]: unknown;
}

/**
 * Service Template Context
 */
export interface ServiceTemplateContext extends ServiceConfig {
  /** Current date */
  date: string;
  /** Package name */
  packageName: string;
  /** Class name prefix */
  classPrefix: string;
  /** Service name (for Spring) */
  springServiceName: string;
  /** Brix SDK version used by generated manifests and packages */
  sdkVersion: string;
  /** Index signature for template rendering */
  [key: string]: unknown;
}

// =====================================================
// Runtime Shell v3.0.10 Phase 7 Governed Scaffolding
// =====================================================

/**
 * Configuration for a governed Phase 7 scaffold.
 */
export interface GovernedScaffoldConfig {
  /** Scaffold kind */
  kind: GovernedScaffoldKind;
  /** Kebab-case module name without a repository path */
  name: string;
  /** Display name */
  displayName: string;
  /** Description */
  description: string;
  /** Owning team or module owner */
  owner: string;
  /** Vendor id used by descriptors */
  vendor: string;
  /** SPDX license id */
  license: string;
  /** Scaffolded module version */
  version: string;
  /** Exact Runtime/L2A version used at build time */
  runtimeVersion: string;
  /** Brix Range v1 runtime support range */
  runtimeRange: string;
  /** Output directory */
  outputDir: string;
  /** Endpoint permission id */
  permissionId: string;
  /** HTTP endpoint path */
  endpointPath: string;
  /** HTTP endpoint method */
  endpointMethod: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  /** Include canonical reliable message descriptor sections */
  includeReliableMessaging: boolean;
  /** Write an accompanying migration plan */
  writeMigrationPlan: boolean;
  /** Index signature for template rendering */
  [key: string]: unknown;
}

/**
 * Template context for governed Phase 7 scaffolds.
 */
export interface GovernedScaffoldTemplateContext extends GovernedScaffoldConfig {
  /** Current date */
  date: string;
  /** PascalCase class or component prefix */
  classPrefix: string;
  /** Java package name */
  packageName: string;
  /** Java package path */
  packagePath: string;
  /** NPM package name for UI artifacts */
  npmPackageName: string;
  /** Module id written to descriptors */
  moduleId: string;
  /** Stable endpoint id */
  endpointId: string;
  /** Stable handler id */
  handlerId: string;
  /** Storage id using root blueprint storage-id constraints */
  storageId: string;
  /** Descriptor resource path */
  descriptorPath: string;
  /** Index signature for template rendering */
  [key: string]: unknown;
}

/**
 * Legacy scanner finding.
 */
export interface LegacyScanFinding {
  /** Stable finding id */
  id: string;
  /** Severity */
  severity: 'blocking' | 'warning';
  /** Relative path */
  path: string;
  /** Matched rule label */
  rule: string;
  /** Migration guidance */
  guidance: string;
}

/**
 * Legacy scanner report.
 */
export interface LegacyScanReport {
  /** Scanner version */
  apiVersion: 'brix.io/phase7-legacy-scan/v1';
  /** Scanned root path */
  root: string;
  /** ISO timestamp */
  generatedAt: string;
  /** Findings */
  findings: LegacyScanFinding[];
  /** Module migration batches */
  migrationBatches: readonly string[];
}

// =====================================================
// v3.0 Business Application Configuration (App)
// =====================================================

/**
 * Module Type
 * 
 * Module classification in v3.0 Runtime Shell architecture:
 * - business: Business modules (e.g., booking, identity, messenger)
 * - infrastructure: Infrastructure modules (e.g., gateway, identity-provider)
 */
export type ModuleType = 'business' | 'infrastructure';

/**
 * Business Application Configuration (v3.0 Architecture)
 * 
 * Following v3.0 Runtime Shell Architecture Design Blueprint, business application modules:
 * - Only depend on io.runtime:runtime-sdk (capability contracts)
 * - Do not directly depend on infrastructure like Kafka/Redis/HTTP
 * - Declare events and capabilities through module-manifest.yaml
 * 
 * @example
 * ```typescript
 * const config: AppConfig = {
 *   name: 'booking',
 *   fullName: 'app-booking',
 *   displayName: 'Booking Management',
 *   moduleType: 'business',
 *   // ...
 * };
 * ```
 */
export interface AppConfig {
  /** Application name (kebab-case, without app- prefix) */
  name: string;
  /** Full application name (auto-generated: app-{name}) */
  fullName: string;
  /** Display name (English or Chinese) */
  displayName: string;
  /** Description */
  description: string;
  /** Module type */
  moduleType: ModuleType;
  /** Author */
  author: string;
  /** Version number */
  version: string;
  /** Output directory */
  outputDir: string;
  
  // ===== Module Structure Configuration (v3.0.4 aligned with production structure) =====
  /** Include API module ({name}-api, for other modules to depend on) */
  withApi: boolean;
  /** Include Core module ({name}-core, business logic) */
  withCore: boolean;
  /** Include Server module ({name}-server, REST exposure layer + OpenAPI) */
  withServer: boolean;
  /** Include Shared module ({name}-shared, frontend-backend shared types) */
  withShared: boolean;
  /** Include UI Web module ({name}-ui-web, Web frontend interface) */
  withUiWeb: boolean;
  /** Include UI Mobile module ({name}-ui-mobile, Mobile frontend interface) */
  withUiMobile: boolean;
  /** Include App module ({name}-app, independently runnable startup module) */
  withApp: boolean;
  /** Legacy config compatibility: Include UI module (equivalent to withUiWeb) */
  withUi: boolean;
  
  // ===== UI Module Configuration =====
  /** Web frontend dev server port (allocated according to port planning scheme) */
  webPort?: number;
  /** Mobile frontend dev server port */
  mobilePort?: number;
  
  // ===== Contract Testing Configuration =====
  /** Enable Pact Consumer-Driven Contract Testing */
  withPact: boolean;
  
  // ===== Capability Dependency Declaration =====
  /**
   * Required capabilities list
   * 
   * Corresponds to capabilities.required in module-manifest.yaml
   * Common capabilities: event-bus, state-store, auth-context, observability
   */
  requiredCapabilities: string[];
  /**
   * Optional capabilities list
   * 
   * Corresponds to capabilities.optional in module-manifest.yaml
   */
  optionalCapabilities: string[];
  
  // ===== Event Declaration =====
  /**
   * List of published event types
   * 
   * Format: Event type name (e.g., ReservationCreatedEvent)
   */
  publishesEvents: string[];
  /**
   * List of subscribed event types
   */
  subscribesEvents: string[];
  
  // ===== Startup Order =====
  /**
   * Startup priority (lower number starts earlier, default 100)
   * 
   * Recommended values:
   * - 10: Infrastructure modules (gateway)
   * - 50: Core business modules (identity)
   * - 100: Normal business modules (default)
   */
  startupOrder: number;
  
  // ===== Dependent Modules =====
  /**
   * List of dependent module IDs
   * 
   * Declares modules that must be started before this module
   */
  dependsOn: string[];
  
  // ===== Docker/K8s Configuration =====
  /** Generate Docker configuration */
  withDocker: boolean;
  /** Generate Kubernetes configuration */
  withK8s: boolean;
  
  /** Index signature for template rendering */
  [key: string]: unknown;
}

/**
 * Business Application Template Context
 */
export interface AppTemplateContext extends AppConfig {
  /** Current date */
  date: string;
  /** 
   * Java package name
   * 
   * Format: io.brix.app.{name}
   * Example: io.brix.app.booking
   */
  packageName: string;
  /**
   * Java package path
   * 
   * Used for generating directory structure
   * Example: io/brix/app/booking
   */
  packagePath: string;
  /** 
   * Class name prefix
   * 
   * Converted from kebab-case to PascalCase
   * Example: booking -> Booking, user-auth -> UserAuth
   */
  classPrefix: string;
  /**
   * NPM package name (UI Web)
   * 
   * Format: @brix/{name}-ui-web
   * Example: @brix/booking-ui-web
   */
  npmPackageName: string;
  /**
   * NPM package name (UI Mobile)
   * 
   * Format: @brix/{name}-ui-mobile
   * Example: @brix/booking-ui-mobile
   */
  npmPackageNameMobile: string;
  /**
   * NPM package name (Shared)
   * 
   * Format: @brix/{name}-shared
   * Example: @brix/booking-shared
   */
  npmPackageNameShared: string;
  /** Brix SDK version used by generated manifests and packages */
  sdkVersion: string;
  /** Index signature for template rendering */
  [key: string]: unknown;
}

/**
 * Default Capability List
 * 
 * Common core capabilities in v3.0 Runtime Shell architecture
 */
export const DEFAULT_REQUIRED_CAPABILITIES = [
  'event-bus',       // Event bus capability
  'auth-context',    // Authentication context capability
  'observability',   // Observability capability
] as const;

export const DEFAULT_OPTIONAL_CAPABILITIES = [
  'state-store',     // State storage capability
  'config-store',    // Configuration storage capability
  'scheduling',      // Scheduled task capability
  'lock',            // Distributed lock capability
  'resilience',      // Resilience capability (circuit breaker/rate limiting)
] as const;
