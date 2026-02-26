/**
 * @file Common Type Definitions
 * @description Defines utility types and API response types
 * @module @brix/runtime-sdk-api-web/types/common
 * @version 3.2.0
 *
 * [v3.2 Changes]
 * Extracted from index.ts into a standalone type file.
 */
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
export type RequiredFields<T, K extends keyof T> = T & {
    [P in K]-?: T[P];
};
/**
 * Deep Readonly
 *
 * <p>Recursively makes all properties readonly.</p>
 */
export type DeepReadonly<T> = {
    readonly [P in keyof T]: T[P] extends object ? DeepReadonly<T[P]> : T[P];
};
/**
 * Standard API Response
 */
export interface ApiResponse<T = unknown> {
    /** Whether Successful */
    readonly success: boolean;
    /** Response Data */
    readonly data?: T;
    /** Error Information */
    readonly error?: ApiError;
    /** Timestamp */
    readonly timestamp: number;
}
/**
 * API Error
 */
export interface ApiError {
    /** Error Code */
    readonly code: string;
    /** Error Message */
    readonly message: string;
    /** Details */
    readonly details?: Record<string, unknown>;
}
/**
 * Paged Response
 */
export interface PagedResponse<T> {
    /** Data List */
    readonly items: T[];
    /** Total Count */
    readonly total: number;
    /** Current Page */
    readonly page: number;
    /** Page Size */
    readonly pageSize: number;
    /** Total Pages */
    readonly totalPages: number;
}
/**
 * Paged Request
 */
export interface PagedRequest {
    /** Current Page */
    readonly page: number;
    /** Page Size */
    readonly pageSize: number;
    /** Sort Field */
    readonly sort?: string;
    /** Sort Direction */
    readonly order?: 'asc' | 'desc';
}
//# sourceMappingURL=common.d.ts.map