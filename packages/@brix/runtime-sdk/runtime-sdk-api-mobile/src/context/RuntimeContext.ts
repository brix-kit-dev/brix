/**
 * @file Runtime context abstract definition
 * @description Define core interfaces for runtime context (no React Native dependency)
 * @module @brix/runtime-sdk-api-mobile/context/RuntimeContext
 * @version 3.2.0
 *
 * [v3.2.0 Notes]
 * Maintains consistent runtime context abstraction with runtime-sdk-api-web.
 * React Native related Context and Hooks are migrated to @brix/runtime-sdk-react-native package (Phase 2).
 *
 * [Design Notes]
 * - Pure abstract interface, does not depend on any UI framework
 * - Can be used in React Native, native modules, and other environments
 */

// =========================================
// Runtime Context Interface
// =========================================

/**
 * Runtime Context Interface
 *
 * <p>Provides a unified entry point for plugins to access runtime capabilities.</p>
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Provide module ID identifier</li>
 *   <li>Provide tenant ID identifier</li>
 *   <li>Provide capability retrieval method</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const http = context.getCapability<HttpCapability>(HttpCapabilityType);
 * const nav = context.getCapability<NavigationCapability>(NavigationCapabilityType);
 * const device = context.getCapability<DeviceCapability>(DeviceCapabilityType);
 * const biometric = context.getCapability<BiometricCapability>(BiometricCapabilityType);
 * ```
 */
export interface RuntimeContext {
  /**
   * Module/Plugin ID
   *
   * <p>Unique identifier for the current plugin.</p>
   */
  readonly moduleId: string;

  /**
   * Tenant ID
   *
   * <p>Tenant identifier for the current runtime environment.</p>
   */
  readonly tenantId: string;

  /**
   * Get capability instance
   *
   * @param capabilityType Capability type identifier (Symbol)
   * @returns Capability instance, returns undefined if not found
   */
  getCapability<T>(capabilityType: symbol): T | undefined;
}
