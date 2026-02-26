/**
 * @file 配置能力类型定义
 * @description 定义配置管理系统的核心类型
 * @module @brix/runtime-sdk-api-web/types/config
 * @version 3.2.0
 *
 * 【v3.2 变更】
 * 从 index.ts 拆分出独立的类型文件。
 */
/**
 * 配置能力类型标识
 */
export declare const ConfigCapabilityType: unique symbol;
/**
 * 配置能力契约
 *
 * <p>为插件提供运行时配置读取能力。</p>
 *
 * <h3>使用示例</h3>
 * ```typescript
 * const config = context.getCapability<ConfigCapability>(ConfigCapabilityType);
 * const apiBase = config.get<string>('api.baseUrl', '/api/v1');
 * const timeout = config.get<number>('http.timeout', 30000);
 * ```
 *
 * <h3>配置来源</h3>
 * <ul>
 *   <li>环境变量</li>
 *   <li>配置中心</li>
 *   <li>清单文件</li>
 * </ul>
 */
export interface ConfigCapability {
    /**
     * 获取配置项
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @returns 配置值
     */
    get<T>(key: string, defaultValue?: T): T;
    /**
     * 获取所有配置
     *
     * @returns 配置对象（支持同步/异步）
     */
    getAll<T = Record<string, unknown>>(): T | Promise<T>;
}
//# sourceMappingURL=config.d.ts.map