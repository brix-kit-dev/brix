/**
 * @file 运行时上下文抽象定义
 * @description 定义运行时上下文的核心接口（无 React 依赖）
 * @module @brix/runtime-sdk-api-web/context/RuntimeContext
 * @version 3.2.0
 *
 * 【v3.2 重构说明】
 * 将 RuntimeContext 抽象从 index.ts 拆分出来，保持契约层无 React 依赖。
 * React 相关的 Context 和 Hooks 迁移到 @brix/runtime-sdk-react 包。
 *
 * 【设计原则】
 * - 纯抽象接口，不依赖任何 UI 框架
 * - 可在 React、Vue、原生 JS 等环境中使用
 */
/**
 * 运行时上下文接口
 *
 * <p>为插件提供运行时能力访问的统一入口。</p>
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>提供模块 ID 标识</li>
 *   <li>提供租户 ID 标识</li>
 *   <li>提供能力获取方法</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * ```typescript
 * const http = context.getCapability<HttpCapability>(HttpCapabilityType);
 * const nav = context.getCapability<NavigationCapability>(NavigationCapabilityType);
 * ```
 */
export interface RuntimeContext {
    /**
     * 模块/插件 ID
     *
     * <p>当前插件的唯一标识。</p>
     */
    readonly moduleId: string;
    /**
     * 租户 ID
     *
     * <p>当前运行环境的租户标识。</p>
     */
    readonly tenantId: string;
    /**
     * 获取能力实例
     *
     * @param capabilityType 能力类型标识（Symbol）
     * @returns 能力实例，不存在时返回 undefined
     */
    getCapability<T>(capabilityType: symbol): T | undefined;
}
//# sourceMappingURL=RuntimeContext.d.ts.map