/**
 * @file ID 生成器模块入口
 * @description 独立的 ID 生成器子包 - 提供各种 ID 生成策略
 * @module @brix/platform-shared/id
 * @version 3.1.0
 * 
 * 【模块说明】
 * 本模块从 @brix/platform-shared/utils 拆分而来，
 * 提供独立的 ID 生成器子包，支持更好的 tree-shaking。
 * 
 * 【选择建议】
 * - UUID：需要全球唯一且标准格式时使用
 * - ShortId：用于 URL、临时标识等需要短 ID 的场景
 * - NanoId：URL 安全的短 ID，比 ShortId 更随机
 * - TimestampId：需要按时间排序的场景
 * - SnowflakeId：高并发分布式系统（需配合节点 ID）
 * 
 * 【使用方式】
 * ```typescript
 * // 推荐：直接从子包导入（更好的 tree-shaking）
 * import { generateUUID, SnowflakeIdGenerator } from '@brix/platform-shared/id';
 * 
 * // 兼容：从主包导入（会加载所有工具）
 * import { generateUUID } from '@brix/platform-shared';
 * ```
 * 
 * @license Apache-2.0
 */

// ============================================================================
// 从 utils/id.ts 重新导出所有 ID 生成相关功能
// ============================================================================

export {
  // UUID
  generateUUID,
  isValidUUID,
  
  // 短 ID
  generateShortId,
  generateNanoId,
  
  // 时间戳 ID
  generateTimestampId,
  extractTimestampFromId,
  
  // 雪花 ID
  SimpleSnowflake,
  generateSnowflakeId,
  
  // 序列 ID 生成器
  createSequenceIdGenerator,
  createDailySequenceIdGenerator,
} from '../utils/id';

// ============================================================================
// 类型导出
// ============================================================================

/**
 * ID 生成策略类型
 */
export type IdStrategy = 'uuid' | 'short' | 'nano' | 'timestamp' | 'snowflake';

/**
 * ID 生成器配置
 */
export interface IdGeneratorConfig {
  /** ID 生成策略 */
  strategy: IdStrategy;
  /** ID 前缀（可选） */
  prefix?: string;
  /** 短 ID 长度（默认 8） */
  shortIdLength?: number;
  /** NanoID 长度（默认 21） */
  nanoIdLength?: number;
}

/**
 * 创建配置化的 ID 生成器
 * 
 * 【功能说明】
 * 根据配置创建 ID 生成器函数，支持多种 ID 策略。
 * 
 * @param config ID 生成器配置
 * @returns ID 生成函数
 * 
 * @example
 * ```typescript
 * // 创建 UUID 生成器
 * const genUUID = createIdGenerator({ strategy: 'uuid' });
 * genUUID(); // 'f47ac10b-58cc-4372-a567-0e02b2c3d479'
 * 
 * // 创建带前缀的短 ID 生成器
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
  
  // 动态导入避免循环依赖
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
