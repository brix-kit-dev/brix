/**
 * @file ID Generator Module Entry
 * @description Standalone ID generator sub-module - provides various ID generation strategies
 * @module @brix-sdk/platform-shared/id
 * @version 3.1.0
 * 
 * ## Module Overview
 * This module was extracted from @brix-sdk/platform-shared/utils,
 * providing a standalone ID generator sub-package with better tree-shaking support.
 * 
 * ## Selection Guide
 * - UUID: Use when globally unique standard-format IDs are required
 * - ShortId: For URLs, temporary identifiers, and other scenarios requiring short IDs
 * - NanoId: URL-safe short ID, more random than ShortId
 * - TimestampId: For scenarios requiring time-based sorting
 * - SnowflakeId: For high-concurrency distributed systems (requires node ID)
 * 
 * ## Usage
 * ```typescript
 * // Recommended: Import directly from sub-package (better tree-shaking)
 * import { generateUUID, SnowflakeIdGenerator } from '@brix-sdk/platform-shared/id';
 * 
 * // Compatible: Import from main package (loads all utilities)
 * import { generateUUID } from '@brix-sdk/platform-shared';
 * ```
 * 
 * @license Apache-2.0
 */

// ============================================================================
// Re-export all ID generation functions from utils/id.ts
// ============================================================================

export {
  // UUID
  generateUUID,
  isValidUUID,
  
  // Short ID
  generateShortId,
  generateNanoId,
  
  // Timestamp ID
  generateTimestampId,
  extractTimestampFromId,
  
  // Snowflake ID
  SimpleSnowflake,
  generateSnowflakeId,
  
  // Sequence ID Generator
  createSequenceIdGenerator,
  createDailySequenceIdGenerator,
} from '../utils/id';

// ============================================================================
// Type Exports
// ============================================================================

/**
 * ID generation strategy type
 */
export type IdStrategy = 'uuid' | 'short' | 'nano' | 'timestamp' | 'snowflake';

/**
 * ID generator configuration
 */
export interface IdGeneratorConfig {
  /** ID generation strategy */
  strategy: IdStrategy;
  /** ID prefix (optional) */
  prefix?: string;
  /** Short ID length (default: 8) */
  shortIdLength?: number;
  /** NanoID length (default: 21) */
  nanoIdLength?: number;
}

/**
 * Create a configurable ID generator
 * 
 * Creates an ID generator function based on configuration, supporting multiple ID strategies.
 * 
 * @param config ID generator configuration
 * @returns ID generator function
 * 
 * @example
 * ```typescript
 * // Create a UUID generator
 * const genUUID = createIdGenerator({ strategy: 'uuid' });
 * genUUID(); // 'f47ac10b-58cc-4372-a567-0e02b2c3d479'
 * 
 * // Create a prefixed short ID generator
 * const genOrderId = createIdGenerator({ 
 *   strategy: 'short', 
 *   prefix: 'ORD-',
 *   shortIdLength: 12,
 * });
 * genOrderId(); // 'ORD-a1B2c3D4e5F6'
 * ```
 */
export function createIdGenerator(config: IdGeneratorConfig): () => string {
  const { strategy, prefix = '', shortIdLength = 8, nanoIdLength = 21 } = config;
  
  // Dynamic import to avoid circular dependency
  const {
    generateUUID,
    generateShortId,
    generateNanoId,
    generateTimestampId,
    SnowflakeIdGenerator,
  } = require('../utils/id');
  
  switch (strategy) {
    case 'uuid':
      return () => `${prefix}${generateUUID()}`;
    case 'short':
      return () => `${prefix}${generateShortId(shortIdLength)}`;
    case 'nano':
      return () => `${prefix}${generateNanoId(nanoIdLength)}`;
    case 'timestamp':
      return () => `${prefix}${generateTimestampId()}`;
    case 'snowflake': {
      const generator = new SnowflakeIdGenerator();
      return () => `${prefix}${generator.nextId()}`;
    }
    default:
      throw new Error(`Unknown ID strategy: ${strategy}`);
  }
}
