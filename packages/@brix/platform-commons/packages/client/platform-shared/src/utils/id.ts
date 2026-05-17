/**
 * @file id.ts
 * @description ID generation utility functions
 * @module @brix-sdk/utils/id
 * @version 3.0.0
 * 
 * ## Module Overview
 * Provides various ID generation utilities including UUID, ShortId, NanoID, TimestampId, SnowflakeId, etc.
 * Different scenarios can use different ID generation strategies.
 * 
 * ## Selection Guide
 * - UUID: When globally unique and standard format is required
 * - ShortId: For URLs, temporary identifiers, and other short ID scenarios
 * - NanoId: URL-safe short ID, more random than ShortId
 * - TimestampId: When time-sorted ordering is needed
 * - SnowflakeId: High-concurrency distributed systems (requires node ID)
 * 
 * @license Apache-2.0
 */

// ============================================================
// UUID
// ============================================================

/**
 * Generate UUID v4
 * 
 * Generates a UUID conforming to the RFC 4122 v4 standard.
 * Prefers native crypto.randomUUID(), falls back to random number generation.
 * 
 * Characteristics:
 * - Globally unique
 * - Standard format, facilitates cross-system interoperability
 * - 36 characters (including hyphens)
 * 
 * @returns UUID string
 * 
 * @example
 * ```typescript
 * generateUUID(); // 'f47ac10b-58cc-4372-a567-0e02b2c3d479'
 * generateUUID(); // '7c9e6679-7425-40de-944b-e07fc1f90ae7'
 * ```
 */
export function generateUUID(): string {
  // Prefer crypto API
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }

  // Fallback implementation
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * Validate UUID format
 * 
 * Validates whether a string is a valid UUID format (v1-v5).
 * 
 * @param uuid - UUID string
 * @returns Whether it is a valid UUID
 * 
 * @example
 * ```typescript
 * isValidUUID('f47ac10b-58cc-4372-a567-0e02b2c3d479'); // true
 * isValidUUID('not-a-uuid');                           // false
 * isValidUUID('f47ac10b58cc4372a5670e02b2c3d479');    // false (missing hyphens)
 * ```
 */
export function isValidUUID(uuid: string): boolean {
  const regex = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  return regex.test(uuid);
}

// ============================================================
// Short ID
// ============================================================

/**
 * Generate short ID
 * 
 * Generates a random ID of specified length, using alphanumeric character set by default.
 * Suitable for URL paths, temporary identifiers, etc.
 * 
 * Collision probability:
 * 8-char default charset has ~218 trillion combinations (62^8)
 * 
 * @param length - ID length (default: 8)
 * @param charset - Character set (default: alphanumeric)
 * @returns Short ID string
 * 
 * @example
 * ```typescript
 * generateShortId();      // 'a1B2c3D4'
 * generateShortId(12);    // 'a1B2c3D4e5F6'
 * generateShortId(6, '0123456789'); // '123456' (digits only)
 * ```
 */
export function generateShortId(
  length: number = 8,
  charset: string = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
): string {
  const charsetLength = charset.length;
  let result = '';

  // Prefer crypto API
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
 * Generate NanoID-style ID
 * 
 * Generates a NanoID-style random ID using a URL-safe character set.
 * Shorter than UUID, more random than ShortId.
 * 
 * Characteristics:
 * - URL safe (no encoding needed)
 * - Default 21 characters, extremely low collision probability
 * - Shorter than UUID
 * 
 * @param length - ID length (default: 21)
 * @returns NanoID string
 * 
 * @example
 * ```typescript
 * generateNanoId();    // 'V1StGXR8_Z5jdHi6B-myT'
 * generateNanoId(10);  // 'IRFa-VaY2b'
 * ```
 */
export function generateNanoId(length: number = 21): string {
  // URL-safe character set
  const charset = 'useandom-26T198340PX75pxJACKVERYMINDBUSHWOLF_GQZbfghjklqvwyzrict';
  return generateShortId(length, charset);
}

// ============================================================
// Timestamp ID
// ============================================================

/**
 * Generate timestamp-based ID
 * 
 * Generates an ID containing time information in the format: timestamp(base36) + random string.
 * Suitable for scenarios requiring time-based sorting or time extraction from IDs.
 * 
 * Advantages:
 * - Naturally sorted by creation time
 * - Creation time extractable from ID
 * - Shorter than UUID
 * 
 * @param randomLength - Random part length (default: 6)
 * @returns Timestamp ID
 * 
 * @example
 * ```typescript
 * generateTimestampId();    // 'lnjhgk4xabcdef'
 * generateTimestampId(10);  // 'lnjhgk4xabcdefghij'
 * 
 * // IDs can be sorted by time
 * const ids = [id1, id2, id3].sort(); // sorted by creation time
 * ```
 */
export function generateTimestampId(randomLength: number = 6): string {
  const timestamp = Date.now().toString(36);
  const random = generateShortId(randomLength, 'abcdefghijklmnopqrstuvwxyz0123456789');
  return `${timestamp}${random}`;
}

/**
 * Extract time from timestamp ID
 * 
 * Attempts to extract the creation time from a timestamp ID.
 * Only works with IDs generated by generateTimestampId.
 * 
 * @param id - Timestamp ID
 * @returns Date object, or null on parse failure
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
  // Attempt to extract base36 timestamp part (first 8 chars)
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
// Sequence ID
// ============================================================

/**
 * Create sequence ID generator
 * 
 * Creates an auto-incrementing sequence ID generator that returns an incrementing ID on each call.
 * Suitable for single-machine unique ID generation.
 * 
 * Note:
 * - Only unique within a single process
 * - Counter resets on restart
 * - Not suitable for distributed environments
 * 
 * @param prefix - ID prefix (optional)
 * @param start - Starting value (default: 1)
 * @returns Generator function
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
 * Create daily sequence ID generator
 * 
 * Creates a daily auto-incrementing sequence ID generator.
 * Format: prefix + date(YYYYMMDD) + sequence(6 digits).
 * Counter resets automatically each day.
 * 
 * Use cases:
 * - Order numbers
 * - Transaction numbers
 * - Work order numbers
 * 
 * @param prefix - ID prefix (optional)
 * @returns Generator function
 * 
 * @example
 * ```typescript
 * const genOrderId = createDailySequenceIdGenerator('ORD');
 * genOrderId(); // 'ORD20240101000001'
 * genOrderId(); // 'ORD20240101000002'
 * 
 * // Next day
 * genOrderId(); // 'ORD20240102000001' (counter reset)
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
// Snowflake ID (Simplified)
// ============================================================

/**
 * Simplified Snowflake ID generator
 * 
 * Generates 64-bit unique IDs similar to Twitter Snowflake.
 * This is a simplified implementation without workerId/datacenterId.
 * 
 * ID structure:
 * - Timestamp (41 bits): usable for ~69 years
 * - Sequence number (12 bits): up to 4096 IDs per millisecond
 * - Random number (10 bits): adds randomness
 * 
 * Note:
 * - Simplified version suitable for single-node or non-critical uniqueness scenarios
 * - For distributed production deployment, use server-side generation
 * 
 * @example
 * ```typescript
 * const snowflake = new SimpleSnowflake();
 * snowflake.generate(); // '7123456789012345678'
 * snowflake.generate(); // '7123456789012345679'
 * ```
 */
export class SimpleSnowflake {
  /** Current sequence number */
  private sequence = 0;
  /** Last generation timestamp */
  private lastTimestamp = -1;

  /**
   * Generate snowflake ID
   * 
   * @returns Snowflake ID string
   */
  generate(): string {
    let timestamp = Date.now();

    if (timestamp === this.lastTimestamp) {
      // Same millisecond, increment sequence number
      this.sequence = (this.sequence + 1) & 0xfff; // 12 bit
      if (this.sequence === 0) {
        // Sequence overflow, wait for next millisecond
        while (timestamp <= this.lastTimestamp) {
          timestamp = Date.now();
        }
      }
    } else {
      // New millisecond, reset sequence number
      this.sequence = 0;
    }

    this.lastTimestamp = timestamp;

    // Combine parts using BigInt
    const random = Math.floor(Math.random() * 1024); // 10 bit
    const id =
      (BigInt(timestamp) << BigInt(22)) |
      (BigInt(this.sequence) << BigInt(10)) |
      BigInt(random);

    return id.toString();
  }
}

/**
 * Default snowflake ID generator instance
 */
const defaultSnowflake = new SimpleSnowflake();

/**
 * Generate snowflake ID
 * 
 * Generates a snowflake ID using the default generator instance.
 * Convenience function that eliminates the need to manually create an instance.
 * 
 * @returns Snowflake ID string
 * 
 * @example
 * ```typescript
 * generateSnowflakeId(); // '7123456789012345678'
 * generateSnowflakeId(); // '7123456789012345679'
 * 
 * // IDs can be sorted numerically (time-ordered)
 * BigInt(id1) < BigInt(id2); // true (id1 generated first)
 * ```
 */
export function generateSnowflakeId(): string {
  return defaultSnowflake.generate();
}