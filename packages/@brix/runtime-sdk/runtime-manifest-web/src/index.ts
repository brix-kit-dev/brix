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
 * @file runtime-manifest-web Module Entry
 * @description UI runtime manifest parsing and validation module exports
 * @module @brix-sdk/runtime-manifest-web
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
 * } from '@brix-sdk/runtime-manifest-web';
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
