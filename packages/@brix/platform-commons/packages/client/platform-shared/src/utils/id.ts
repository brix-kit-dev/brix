/**
 * @file id.ts
 * @description ID 生成工具函数集
 * @module @brix/utils/id
 * @version 3.0.0
 * 
 * 【模块说明】
 * 提供各种 ID 生成工具，包括 UUID、短 ID、NanoID、时间戳 ID、雪花 ID 等。
 * 不同场景可选择不同的 ID 生成策略。
 * 
 * 【选择建议】
 * - UUID：需要全球唯一且标准格式时使用
 * - ShortId：用于 URL、临时标识等需要短 ID 的场景
 * - NanoId：URL 安全的短 ID，比 ShortId 更随机
 * - TimestampId：需要按时间排序的场景
 * - SnowflakeId：高并发分布式系统（需配合节点 ID）
 * 
 * @license Apache-2.0
 */

// ============================================================
// UUID
// ============================================================

/**
 * 生成 UUID v4
 * 
 * 【功能说明】
 * 生成符合 RFC 4122 v4 标准的 UUID。
 * 优先使用原生 crypto.randomUUID()，降级使用随机数生成。
 * 
 * 【特点】
 * - 全球唯一
 * - 标准格式，便于跨系统交互
 * - 36字符（含连字符）
 * 
 * @returns UUID 字符串
 * 
 * @example
 * ```typescript
 * generateUUID(); // 'f47ac10b-58cc-4372-a567-0e02b2c3d479'
 * generateUUID(); // '7c9e6679-7425-40de-944b-e07fc1f90ae7'
 * ```
 */
export function generateUUID(): string {
  // 优先使用 crypto API
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }

  // 降级方案
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * 验证 UUID 格式
 * 
 * 【功能说明】
 * 验证字符串是否为有效的 UUID 格式（v1-v5）。
 * 
 * @param uuid UUID 字符串
 * @returns 是否为有效 UUID
 * 
 * @example
 * ```typescript
 * isValidUUID('f47ac10b-58cc-4372-a567-0e02b2c3d479'); // true
 * isValidUUID('not-a-uuid');                           // false
 * isValidUUID('f47ac10b58cc4372a5670e02b2c3d479');    // false（缺少连字符）
 * ```
 */
export function isValidUUID(uuid: string): boolean {
  const regex = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  return regex.test(uuid);
}

// ============================================================
// 短 ID
// ============================================================

/**
 * 生成短 ID
 * 
 * 【功能说明】
 * 生成指定长度的随机 ID，默认使用字母数字字符集。
 * 适用于 URL 路径、临时标识等场景。
 * 
 * 【碰撞概率】
 * 8位默认字符集约有 218 万亿种组合（62^8）
 * 
 * @param length ID 长度（默认 8）
 * @param charset 字符集（默认字母数字）
 * @returns 短 ID 字符串
 * 
 * @example
 * ```typescript
 * generateShortId();      // 'a1B2c3D4'
 * generateShortId(12);    // 'a1B2c3D4e5F6'
 * generateShortId(6, '0123456789'); // '123456'（纯数字）
 * ```
 */
export function generateShortId(
  length: number = 8,
  charset: string = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
): string {
  const charsetLength = charset.length;
  let result = '';

  // 优先使用 crypto API
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    const randomValues = new Uint32Array(length);
    crypto.getRandomValues(randomValues);
    for (let i = 0; i < length; i++) {
      result += charset[randomValues[i] % charsetLength];
    }
  } else {
    for (let i = 0; i < length; i++) {
      result += charset[Math.floor(Math.random() * charsetLength)];
    }
  }

  return result;
}

/**
 * 生成 NanoID 风格的 ID
 * 
 * 【功能说明】
 * 生成 NanoID 风格的随机 ID，使用 URL 安全字符集。
 * 比 UUID 更短，比 ShortId 更随机。
 * 
 * 【特点】
 * - URL 安全（无需编码）
 * - 默认21位，碰撞概率极低
 * - 比 UUID 更短
 * 
 * @param length ID 长度（默认 21）
 * @returns NanoID 字符串
 * 
 * @example
 * ```typescript
 * generateNanoId();    // 'V1StGXR8_Z5jdHi6B-myT'
 * generateNanoId(10);  // 'IRFa-VaY2b'
 * ```
 */
export function generateNanoId(length: number = 21): string {
  // URL 安全字符集
  const charset = 'useandom-26T198340PX75pxJACKVERYMINDBUSHWOLF_GQZbfghjklqvwyzrict';
  return generateShortId(length, charset);
}

// ============================================================
// 时间戳 ID
// ============================================================

/**
 * 生成基于时间戳的 ID
 * 
 * 【功能说明】
 * 生成包含时间信息的 ID，格式为：时间戳(base36) + 随机串。
 * 适用于需要按时间排序或提取时间信息的场景。
 * 
 * 【优势】
 * - 按创建时间自然排序
 * - 可以从 ID 中提取创建时间
 * - 比 UUID 更短
 * 
 * @param randomLength 随机部分长度（默认 6）
 * @returns 时间戳 ID
 * 
 * @example
 * ```typescript
 * generateTimestampId();    // 'lnjhgk4xabcdef'
 * generateTimestampId(10);  // 'lnjhgk4xabcdefghij'
 * 
 * // ID 可按时间排序
 * const ids = [id1, id2, id3].sort(); // 按创建时间排序
 * ```
 */
export function generateTimestampId(randomLength: number = 6): string {
  const timestamp = Date.now().toString(36);
  const random = generateShortId(randomLength, 'abcdefghijklmnopqrstuvwxyz0123456789');
  return `${timestamp}${random}`;
}

/**
 * 从时间戳 ID 中提取时间
 * 
 * 【功能说明】
 * 尝试从时间戳 ID 中提取创建时间。
 * 仅适用于 generateTimestampId 生成的 ID。
 * 
 * @param id 时间戳 ID
 * @returns Date 对象，解析失败返回 null
 * 
 * @example
 * ```typescript
 * const id = generateTimestampId();
 * const date = extractTimestampFromId(id);
 * console.log(date); // 2024-01-01T12:00:00.000Z
 * 
 * extractTimestampFromId('invalid'); // null
 * ```
 */
export function extractTimestampFromId(id: string): Date | null {
  // 尝试提取 base36 时间戳部分（前8位）
  const timestampPart = id.slice(0, 8);
  try {
    const timestamp = parseInt(timestampPart, 36);
    if (isNaN(timestamp)) return null;
    return new Date(timestamp);
  } catch {
    return null;
  }
}

// ============================================================
// 序列 ID
// ============================================================

/**
 * 创建序列 ID 生成器
 * 
 * 【功能说明】
 * 创建一个自增序列 ID 生成器，每次调用返回递增的 ID。
 * 适用于单机环境下的唯一 ID 生成。
 * 
 * 【注意】
 * - 仅在单进程内唯一
 * - 重启后计数器重置
 * - 不适合分布式环境
 * 
 * @param prefix ID 前缀（可选）
 * @param start 起始值（默认 1）
 * @returns 生成器函数
 * 
 * @example
 * ```typescript
 * const genOrderId = createSequenceIdGenerator('ORD-', 1000);
 * genOrderId(); // 'ORD-1000'
 * genOrderId(); // 'ORD-1001'
 * genOrderId(); // 'ORD-1002'
 * 
 * const genId = createSequenceIdGenerator();
 * genId(); // '1'
 * genId(); // '2'
 * ```
 */
export function createSequenceIdGenerator(
  prefix: string = '',
  start: number = 1
): () => string {
  let counter = start;
  return () => `${prefix}${counter++}`;
}

/**
 * 创建带日期前缀的序列 ID 生成器
 * 
 * 【功能说明】
 * 创建按日自增的序列 ID 生成器。
 * 格式：前缀 + 日期(YYYYMMDD) + 序号(6位)。
 * 每天计数器自动重置。
 * 
 * 【适用场景】
 * - 订单号
 * - 流水号
 * - 工单号
 * 
 * @param prefix ID 前缀（可选）
 * @returns 生成器函数
 * 
 * @example
 * ```typescript
 * const genOrderId = createDailySequenceIdGenerator('ORD');
 * genOrderId(); // 'ORD20240101000001'
 * genOrderId(); // 'ORD20240101000002'
 * 
 * // 第二天
 * genOrderId(); // 'ORD20240102000001'（计数器重置）
 * ```
 */
export function createDailySequenceIdGenerator(prefix: string = ''): () => string {
  let lastDate = '';
  let counter = 0;

  return () => {
    const today = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    if (today !== lastDate) {
      lastDate = today;
      counter = 0;
    }
    return `${prefix}${today}${(++counter).toString().padStart(6, '0')}`;
  };
}

// ============================================================
// 雪花 ID (简化版)
// ============================================================

/**
 * 简化版雪花 ID 生成器
 * 
 * 【功能说明】
 * 生成类似 Twitter Snowflake 的 64 位唯一 ID。
 * 此为简化实现，不含 workerId/datacenterId。
 * 
 * 【ID 结构】
 * - 时间戳（41位）：可用约69年
 * - 序列号（12位）：每毫秒可生成 4096 个 ID
 * - 随机数（10位）：增加随机性
 * 
 * 【注意】
 * - 简化版适用于单节点或对全局唯一性要求不高的场景
 * - 生产环境分布式部署建议使用服务端生成
 * 
 * @example
 * ```typescript
 * const snowflake = new SimpleSnowflake();
 * snowflake.generate(); // '7123456789012345678'
 * snowflake.generate(); // '7123456789012345679'
 * ```
 */
export class SimpleSnowflake {
  /** 当前序列号 */
  private sequence = 0;
  /** 上次生成时间戳 */
  private lastTimestamp = -1;

  /**
   * 生成雪花 ID
   * 
   * @returns 雪花 ID 字符串
   */
  generate(): string {
    let timestamp = Date.now();

    if (timestamp === this.lastTimestamp) {
      // 同一毫秒内，序列号递增
      this.sequence = (this.sequence + 1) & 0xfff; // 12 bit
      if (this.sequence === 0) {
        // 序列号溢出，等待下一毫秒
        while (timestamp <= this.lastTimestamp) {
          timestamp = Date.now();
        }
      }
    } else {
      // 新的毫秒，重置序列号
      this.sequence = 0;
    }

    this.lastTimestamp = timestamp;

    // 使用 BigInt 组合各部分
    const random = Math.floor(Math.random() * 1024); // 10 bit
    const id =
      (BigInt(timestamp) << BigInt(22)) |
      (BigInt(this.sequence) << BigInt(10)) |
      BigInt(random);

    return id.toString();
  }
}

/**
 * 默认的雪花 ID 生成器实例
 */
const defaultSnowflake = new SimpleSnowflake();

/**
 * 生成雪花 ID
 * 
 * 【功能说明】
 * 使用默认生成器实例生成雪花 ID。
 * 便捷函数，无需手动创建实例。
 * 
 * @returns 雪花 ID 字符串
 * 
 * @example
 * ```typescript
 * generateSnowflakeId(); // '7123456789012345678'
 * generateSnowflakeId(); // '7123456789012345679'
 * 
 * // 可按数值排序（时间有序）
 * BigInt(id1) < BigInt(id2); // true（id1 先生成）
 * ```
 */
export function generateSnowflakeId(): string {
  return defaultSnowflake.generate();
}
