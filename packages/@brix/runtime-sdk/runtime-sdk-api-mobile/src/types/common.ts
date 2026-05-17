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
 * @file Common type definitions
 * @description Define common utility types and API response types
 * @module @brix-sdk/runtime-sdk-api-mobile/types/common
 * @version 3.2.0
 *
 * [v3.2.0 Notes]
 * Maintains consistent common type definitions with runtime-sdk-api-web.
 */

// =========================================
// Utility Types
// =========================================

/**
 * Optional Fields
 *
 * <p>Makes specified fields optional.</p>
 */
export type Optional<T, K extends keyof T> = Omit<T, K> & Partial<Pick<T, K>>;

/**
 * Required Fields
 *
 * <p>Makes specified fields required. Named to avoid conflict with built-in Required.</p>
 */
export type RequiredFields<T, K extends keyof T> = T & { [P in K]-?: T[P] };

/**
 * Deep Readonly
 *
 * <p>Recursively makes all properties readonly.</p>
 */
export type DeepReadonly<T> = {
  readonly [P in keyof T]: T[P] extends object ? DeepReadonly<T[P]> : T[P];
};

// =========================================
// API Response Types
// =========================================

/**
 * Standard API Response
 */
export interface ApiResponse<T = unknown> {
  /** Whether successful */
  readonly success: boolean;
  /** Response data */
  readonly data?: T;
  /** Error info */
  readonly error?: ApiError;
  /** Timestamp */
  readonly timestamp: number;
}

/**
 * API Error
 */
export interface ApiError {
  /** Error code */
  readonly code: string;
  /** Error message */
  readonly message: string;
  /** Details */
  readonly details?: Record<string, unknown>;
}

// =========================================
// Pagination Types
// =========================================

/**
 * Paged Response
 */
export interface PagedResponse<T> {
  /** Data list */
  readonly items: T[];
  /** Total count */
  readonly total: number;
  /** Current page */
  readonly page: number;
  /** Page size */
  readonly pageSize: number;
  /** Total pages */
  readonly totalPages: number;
}

/**
 * Paged Request
 */
export interface PagedRequest {
  /** Current page */
  readonly page: number;
  /** Page size */
  readonly pageSize: number;
  /** Sort field */
  readonly sort?: string;
  /** Sort order */
  readonly order?: 'asc' | 'desc';
}

// =========================================
// Mobile Common Types
// =========================================

/**
 * Subscription Object
 * 
 * <p>Used to manage event subscription lifecycle.</p>
 */
export interface Subscription {
  /** Unsubscribe */
  unsubscribe(): void;
}
