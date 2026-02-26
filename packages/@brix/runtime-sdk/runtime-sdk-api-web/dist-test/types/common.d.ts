/**
 * @file 通用类型定义
 * @description 定义通用的工具类型和 API 响应类型
 * @module @brix/runtime-sdk-api-web/types/common
 * @version 3.2.0
 *
 * 【v3.2 变更】
 * 从 index.ts 拆分出独立的类型文件。
 */
/**
 * 可选字段
 *
 * <p>将指定字段变为可选。</p>
 */
export type Optional<T, K extends keyof T> = Omit<T, K> & Partial<Pick<T, K>>;
/**
 * 必填字段
 *
 * <p>将指定字段变为必填。命名避免与内置 Required 冲突。</p>
 */
export type RequiredFields<T, K extends keyof T> = T & {
    [P in K]-?: T[P];
};
/**
 * 深度只读
 *
 * <p>递归地将所有属性变为只读。</p>
 */
export type DeepReadonly<T> = {
    readonly [P in keyof T]: T[P] extends object ? DeepReadonly<T[P]> : T[P];
};
/**
 * 标准 API 响应
 */
export interface ApiResponse<T = unknown> {
    /** 是否成功 */
    readonly success: boolean;
    /** 响应数据 */
    readonly data?: T;
    /** 错误信息 */
    readonly error?: ApiError;
    /** 时间戳 */
    readonly timestamp: number;
}
/**
 * API 错误
 */
export interface ApiError {
    /** 错误代码 */
    readonly code: string;
    /** 错误消息 */
    readonly message: string;
    /** 详细信息 */
    readonly details?: Record<string, unknown>;
}
/**
 * 分页响应
 */
export interface PagedResponse<T> {
    /** 数据列表 */
    readonly items: T[];
    /** 总记录数 */
    readonly total: number;
    /** 当前页码 */
    readonly page: number;
    /** 每页大小 */
    readonly pageSize: number;
    /** 总页数 */
    readonly totalPages: number;
}
/**
 * 分页请求
 */
export interface PagedRequest {
    /** 当前页码 */
    readonly page: number;
    /** 每页大小 */
    readonly pageSize: number;
    /** 排序字段 */
    readonly sort?: string;
    /** 排序方向 */
    readonly order?: 'asc' | 'desc';
}
//# sourceMappingURL=common.d.ts.map