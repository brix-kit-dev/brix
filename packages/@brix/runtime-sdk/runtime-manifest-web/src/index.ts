/**
 * @file runtime-manifest-web Module Entry
 * @description UI runtime manifest parsing and validation module exports
 * @module @brix/runtime-manifest-web
 * @version 3.0.0
 * 
 * [Module Description]
 * This module provides complete manifest file processing capabilities:
 * - ManifestLoader: Manifest loading
 * - ManifestValidator: Manifest validation
 * - ManifestTransformer: Manifest transformation
 * - Type definitions: Manifest data structures
 * 
 * [Usage Example]
 * ```typescript
 * import {
 *   createManifestLoader,
 *   createManifestValidator,
 *   createManifestTransformer,
 * } from '@brix/runtime-manifest-web';
 * 
 * // Load manifest
 * const loader = createManifestLoader();
 * const manifest = await loader.loadAppManifest('/manifest.json');
 * 
 * // Validate manifest
 * const validator = createManifestValidator();
 * const result = validator.validateAppManifest(manifest);
 * 
 * // Transform manifest
 * const transformer = createManifestTransformer();
 * const plugins = transformer.extractPluginEntries(manifest);
 * ```
 */

// Type exports
export type {
  AppManifest,
  AppMeta,
  PluginManifest,
  PluginContributes,
  RouteContribution,
  MenuContribution,
  CommandContribution,
  CapabilityContribution,
  ConfigContribution,
  ConfigSchemaItem,
  ViewContribution,
  I18nContribution,
  ThemeContribution,
  GlobalConfig,
  BuildInfo,
} from './types/Manifest';

// Manifest Loader
export {
  ManifestLoader,
  createManifestLoader,
  type ManifestLoaderConfig,
} from './ManifestLoader';

// Manifest Validator
export {
  ManifestValidator,
  createManifestValidator,
  type ValidationResult,
  type ValidationError,
  type ValidationWarning,
  type ValidationOptions,
} from './ManifestValidator';

// Manifest Transformer
export {
  ManifestTransformer,
  createManifestTransformer,
  type TransformedRoute,
  type TransformedMenuItem,
  type TransformConfig,
} from './ManifestTransformer';
