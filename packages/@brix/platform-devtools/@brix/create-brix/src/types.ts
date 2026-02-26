/**
 * @file types.ts
 * @description Type Definitions
 * @module @brix/create-brix
 * @version 3.0
 * 
 * v3.0 Changes:
 * - Added 'app' generation type for creating business application modules (shinwa-app-*)
 * - Added AppConfig configuration interface following v3.0 Runtime Shell architecture
 * - Added AppTemplateContext template context
 */

// =====================================================
// Generation Types
// =====================================================

/**
 * Generation Type
 * 
 * v3.0 architecture supports three project types:
 * - plugin: Plugin skeleton (v2.x legacy architecture, JAR package, depends only on platform-commons)
 * - service: Service skeleton (v2.x legacy architecture, runnable Spring Boot application)
 * - app: Business application module (v3.0 new architecture, follows Runtime Shell capability contracts)
 */
export type GenerateType = 'plugin' | 'service' | 'app';

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
  /** Service name (kebab-case, without shinwa-service- prefix) */
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
  /** Index signature for template rendering */
  [key: string]: unknown;
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
 *   fullName: 'shinwa-app-booking',
 *   displayName: 'Booking Management',
 *   moduleType: 'business',
 *   // ...
 * };
 * ```
 */
export interface AppConfig {
  /** Application name (kebab-case, without shinwa-app- prefix) */
  name: string;
  /** Full application name (auto-generated: shinwa-app-{name}) */
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
   * Format: com.shinwa.app.{name}
   * Example: com.shinwa.app.booking
   */
  packageName: string;
  /**
   * Java package path
   * 
   * Used for generating directory structure
   * Example: com/shinwa/app/booking
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
   * Format: @shinwa/{name}-ui-web
   * Example: @shinwa/booking-ui-web
   */
  npmPackageName: string;
  /**
   * NPM package name (UI Mobile)
   * 
   * Format: @shinwa/{name}-ui-mobile
   * Example: @shinwa/booking-ui-mobile
   */
  npmPackageNameMobile: string;
  /**
   * NPM package name (Shared)
   * 
   * Format: @shinwa/{name}-shared
   * Example: @shinwa/booking-shared
   */
  npmPackageNameShared: string;
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
