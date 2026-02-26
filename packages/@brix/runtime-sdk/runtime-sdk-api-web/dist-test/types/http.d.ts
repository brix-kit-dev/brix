/**
 * @file HTTP 客户端能力类型定义
 * @description 定义 HTTP 客户端能力契约，替代直接使用 fetch/axios
 * @module @brix/runtime-sdk-api-web/types/http
 * @version 3.2.0
 *
 * 【v3.2 变更】
 * 从 index.ts 拆分出独立的类型文件。
 *
 * 【架构说明 - 红线 R3】
 * 插件层禁止直接使用 axios / fetch 等 HTTP 客户端库，
 * 需通过 HttpCapability 统一发起请求。
 */
/**
 * HTTP 客户端能力类型标识
 */
export declare const HttpCapabilityType: unique symbol;
/**
 * HTTP 请求配置选项
 */
export interface HttpRequestConfig {
    /** 请求 URL（相对于 baseURL 或绝对路径） */
    readonly url: string;
    /** HTTP 方法 */
    readonly method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'HEAD' | 'OPTIONS';
    /** 请求头 */
    readonly headers?: Record<string, string>;
    /** URL 查询参数 */
    readonly params?: Record<string, unknown>;
    /** 请求体 */
    readonly data?: unknown;
    /** 超时时间（毫秒） */
    readonly timeout?: number;
    /** 基础 URL */
    readonly baseURL?: string;
}
/**
 * HTTP 响应结构
 */
export interface HttpResponse<T = unknown> {
    /** 响应数据 */
    readonly data: T;
    /** HTTP 状态码 */
    readonly status: number;
    /** 状态文本 */
    readonly statusText: string;
    /** 响应头 */
    readonly headers: Record<string, string>;
}
/**
 * HTTP 客户端能力契约
 *
 * <p>为插件层提供统一的 HTTP 请求抽象，替代 axios / fetch 直接调用。</p>
 *
 * <h3>使用示例</h3>
 * ```typescript
 * const http = runtimeContext.getCapability<HttpCapability>(HttpCapabilityType);
 * const products = await http.get<Product[]>('/api/v1/products');
 * const created = await http.post<Product>('/api/v1/products', newProduct);
 * ```
 *
 * <h3>架构说明</h3>
 * <ul>
 *   <li>Shell 层提供实现（可基于 fetch / axios，对插件透明）</li>
 *   <li>自动注入认证 Token、租户 ID 等上下文头</li>
 *   <li>统一错误处理和重试策略</li>
 * </ul>
 */
export interface HttpCapability {
    /**
     * 发送通用请求
     *
     * @param config 请求配置
     * @returns 响应结果
     */
    request<T = unknown>(config: HttpRequestConfig): Promise<HttpResponse<T>>;
    /**
     * GET 请求
     *
     * @param url 请求 URL
     * @param params 查询参数
     * @returns 响应数据
     */
    get<T = unknown>(url: string, params?: Record<string, unknown>): Promise<T>;
    /**
     * POST 请求
     *
     * @param url 请求 URL
     * @param data 请求体
     * @returns 响应数据
     */
    post<T = unknown>(url: string, data?: unknown): Promise<T>;
    /**
     * PUT 请求
     *
     * @param url 请求 URL
     * @param data 请求体
     * @returns 响应数据
     */
    put<T = unknown>(url: string, data?: unknown): Promise<T>;
    /**
     * DELETE 请求
     *
     * @param url 请求 URL
     * @returns 响应数据
     */
    delete<T = unknown>(url: string): Promise<T>;
    /**
     * PATCH 请求
     *
     * @param url 请求 URL
     * @param data 请求体
     * @returns 响应数据
     */
    patch<T = unknown>(url: string, data?: unknown): Promise<T>;
}
//# sourceMappingURL=http.d.ts.map