/**
 * @file Plugin related type definitions
 * @description Define core types for the plugin system, including manifest, instance, lifecycle, etc.
 * @module @brix/runtime-sdk-api-mobile/types/plugin
 * @version 3.2.0
 *
 * [v3.2.0 Notes]
 * Maintains consistent plugin system type definitions with runtime-sdk-api-web.
 *
 * [Design Notes]
 * - Define generic plugin contracts, adapters (MF/Iframe/Native) can extend
 * - Use generics to support different manifest and instance types
 * - Framework-agnostic: Does not depend on React/React Native or other UI frameworks
 */

/**
 * Framework-agnostic Component Type
 * 
 * <p>v3.0.4 Architecture red line fix: Contract layer does not depend on any UI framework.
 * Actual component types are defined by specific adapters or React Native binding layer.</p>
 * 
 * <p>Usage:</p>
 * <ul>
 *   <li>In React Native projects, use type definitions from @brix/runtime-sdk-react-native</li>
 *   <li>In framework-agnostic scenarios, use unknown and handle at runtime</li>
 * </ul>
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type ComponentType = unknown;

// =========================================
// Plugin Status
// =========================================

/**
 * Plugin Status
 *
 * <p>Describes the current state of a plugin in its lifecycle.</p>
 */
export type PluginStatus =
  | 'registered'    // Registered
  | 'loading'       // Loading
  | 'loaded'        // Loaded
  | 'activating'    // Activating
  | 'active'        // Active
  | 'deactivating'  // Deactivating
  | 'inactive'      // Inactive
  | 'error';        // Error state

// =========================================
// Plugin Manifest (Generic Base Contract)
// =========================================

/**
 * Plugin Manifest Base Interface
 *
 * <p>All adapter manifests (MF, Iframe, Native) must extend this interface.</p>
 */
export interface PluginManifest {
  /** Plugin unique identifier */
  readonly id: string;
  /** Plugin name */
  readonly name: string;
  /** Plugin version */
  readonly version: string;
  /** Whether enabled */
  readonly enabled?: boolean;
}

/**
 * Plugin Metadata
 *
 * <p>Describes detailed plugin information, including required capabilities, published/subscribed events, etc.</p>
 */
export interface PluginMetadata {
  /** Version */
  readonly version: string;
  /** Name */
  readonly name: string;
  /** Description */
  readonly description?: string;
  /** Required capability list */
  readonly requiredCapabilities?: string[];
  /** Published event list */
  readonly publishedEvents?: string[];
  /** Subscribed event list */
  readonly subscribedEvents?: string[];
}

// =========================================
// Plugin Instance (Generic Base Contract)
// =========================================

/**
 * Plugin Instance Base Interface
 *
 * <p>All adapter instances must extend this interface.</p>
 *
 * @template M Manifest type
 */
export interface PluginInstance<M extends PluginManifest = PluginManifest> {
  /** Plugin ID */
  readonly id: string;
  /** Corresponding manifest */
  readonly manifest: M;
  /** Current status */
  status: string;
  /** Error information */
  readonly error?: Error;
}

// =========================================
// Plugin Loader (Generic Contract)
// =========================================

/**
 * Plugin Loader Interface
 *
 * <p>All adapters (MFPluginLoader, IframePluginLoader, NativePluginLoader)
 * must implement this interface.</p>
 *
 * @template M Manifest type
 * @template I Instance type
 */
export interface PluginLoader<
  M extends PluginManifest = PluginManifest,
  I extends PluginInstance<M> = PluginInstance<M>
> {
  /** Load single plugin */
  load(manifest: M): Promise<I>;
  /** Unload plugin */
  unload(pluginId: string): void;
  /** Preload multiple plugins */
  preload?(manifests: M[]): Promise<void>;
  /** Get loaded plugin list */
  getLoaded(): I[];
  /** Check if plugin is loaded */
  isLoaded(pluginId: string): boolean;
}

// =========================================
// Plugin Load Error
// =========================================

/**
 * Plugin Load Error
 *
 * <p>Encapsulates errors that occur during plugin loading, including error phase information.</p>
 */
export class PluginLoadError extends Error {
  constructor(
    message: string,
    public readonly pluginId: string,
    public readonly phase: 'script' | 'init' | 'module' | 'component' | 'iframe' | 'bridge' | 'native',
    public readonly cause?: Error
  ) {
    super(message);
    this.name = 'PluginLoadError';
  }
}

// =========================================
// Plugin Dependency Declaration
// =========================================

/**
 * Plugin Dependency Declaration
 *
 * <p>Describes plugin dependencies on other plugins, used for manifest parsing and dependency validation.</p>
 */
export interface PluginDependency {
  /** Dependent plugin name */
  readonly name: string;
  /** Dependent version */
  readonly version: string;
  /** Maven GroupId */
  readonly groupId: string;
  /** Maven ArtifactId (auto-generated: {name}-core) */
  readonly artifactId: string;
}

// =========================================
// Plugin Entry Configuration
// =========================================

/**
 * Plugin Entry Configuration
 *
 * <p>Defines plugin loading entry and basic information.</p>
 */
export interface PluginEntry {
  /** Plugin unique identifier */
  readonly id: string;
  /** Plugin name */
  readonly name: string;
  /** Plugin version */
  readonly version: string;
  /** Entry URL (Web) or module name (Native) */
  readonly entry: string;
  /** Whether enabled */
  readonly enabled?: boolean;
}
