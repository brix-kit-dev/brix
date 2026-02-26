/**
 * @file 导航相关类型定义
 * @description 定义导航系统的核心类型，包括导航选项、路由变更监听等
 * @module @brix/runtime-sdk-api-web/types/navigation
 * @version 3.2.0
 *
 * 【v3.2 变更】
 * 从 index.ts 拆分出独立的类型文件，并从 infra-adapter-router-web 上提通用契约。
 *
 * 【v3.2.0 Phase 1 补全】
 * - 新增 NavigateResult：导航结果类型
 * - 新增 WebNavigateOptions：Web 端导航选项
 * - 新增 PageChangeEvent：页面变化事件
 * - 新增 PageChangeHandler：页面变化处理器
 *
 * 【设计原则】
 * - 定义通用的导航契约，路由适配器实现具体逻辑
 * - 支持声明式导航（PageId）和命令式导航（Path）
 */
import type { Unsubscribe } from './event';
/**
 * 导航选项
 *
 * <p>控制导航行为的配置选项。</p>
 */
export interface NavigateOptions {
    /**
     * 是否替换当前历史记录
     *
     * <p>为 true 时，新页面将替换当前页面在历史栈中的位置。</p>
     */
    replace?: boolean;
    /**
     * 路由状态
     *
     * <p>传递给目标页面的状态数据。</p>
     */
    state?: Record<string, unknown>;
}
/**
 * Web 端导航选项
 *
 * <p>相比基础 NavigateOptions，提供更丰富的导航控制。</p>
 *
 * @since 3.2.0
 */
export interface WebNavigateOptions extends NavigateOptions {
    /**
     * 是否在新窗口打开
     *
     * @default false
     */
    openInNewWindow?: boolean;
    /**
     * 跳过治理策略检查
     *
     * <p>仅限 Host 内部使用，插件设置无效。</p>
     *
     * @internal
     */
    skipGovernance?: boolean;
}
/**
 * 导航结果
 *
 * <p>描述导航请求的执行结果。在治理模式下，
 * 插件发起的导航是"请求"而非"命令"，Host 可能拒绝导航。</p>
 *
 * @since 3.2.0
 */
export interface NavigateResult {
    /**
     * 导航是否成功
     */
    readonly success: boolean;
    /**
     * 失败原因（仅 success=false 时有值）
     *
     * - 'permission_denied': 权限不足
     * - 'feature_disabled': 功能被禁用
     * - 'page_not_found': 页面不存在
     * - 'host_rejected': Host 拒绝导航
     * - 'navigation_blocked': 导航被阻止（如表单未保存）
     */
    readonly reason?: 'permission_denied' | 'feature_disabled' | 'page_not_found' | 'host_rejected' | 'navigation_blocked';
    /**
     * 详细错误消息
     */
    readonly message?: string;
}
/**
 * 页面变化事件
 *
 * <p>描述页面切换的详细信息，用于页面监听和分析。</p>
 *
 * @since 3.2.0
 */
export interface PageChangeEvent {
    /**
     * 当前页面 ID
     *
     * <p>格式：{pluginId}:{pageName}，如 'booking:detail'。</p>
     */
    readonly pageId: string;
    /**
     * 页面参数
     *
     * <p>传递给目标页面的参数对象。</p>
     */
    readonly params?: Record<string, unknown>;
    /**
     * 来源页面 ID（如果有）
     */
    readonly fromPageId?: string;
    /**
     * 导航类型
     *
     * - 'push': 正向导航（点击链接/按钮）
     * - 'pop': 回退导航（浏览器后退）
     * - 'replace': 替换导航
     */
    readonly navigationType: 'push' | 'pop' | 'replace';
}
/**
 * 页面变化处理器
 *
 * @since 3.2.0
 */
export type PageChangeHandler = (event: PageChangeEvent) => void;
/**
 * 导航能力类型标识
 */
export declare const NavigationCapabilityType: unique symbol;
/**
 * 导航能力契约
 *
 * <p>为插件提供页面导航能力，替代直接使用 react-router。</p>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>插件只感知 PageId，不感知 URL</li>
 *   <li>所有导航都是"请求"，Host 可以拒绝</li>
 *   <li>治理策略支持权限检查、插件隔离、Feature Flag</li>
 * </ul>
 *
 * <h3>基础用法（命令式导航）</h3>
 * ```typescript
 * const nav = context.getCapability<NavigationCapability>(NavigationCapabilityType);
 * nav.navigate('/booking/list');
 * nav.goBack();
 * ```
 *
 * <h3>高级用法（请求式导航）</h3>
 * ```typescript
 * const nav = context.getCapability<NavigationCapability>(NavigationCapabilityType);
 *
 * // 请求导航到页面（可能被拒绝）
 * const result = await nav.requestNavigate('booking:detail', { id: '123' });
 * if (!result.success) {
 *   console.error('导航失败:', result.reason, result.message);
 * }
 *
 * // 订阅页面变化
 * const unsubscribe = nav.onPageChange((event) => {
 *   console.log('页面切换:', event.pageId);
 * });
 * ```
 *
 * @since 3.0.0
 */
export interface NavigationCapability {
    /**
     * 导航到指定路径
     *
     * <p>命令式导航，直接执行。如需受控导航，请使用 requestNavigate。</p>
     *
     * @param path 目标路径
     * @param options 导航选项
     */
    navigate(path: string, options?: NavigateOptions): void;
    /**
     * 返回上一页
     */
    goBack(): void;
    /**
     * 获取当前路径
     *
     * @returns 当前 URL 路径
     */
    getCurrentPath(): string;
    /**
     * 请求导航到指定页面
     *
     * <p>这是"请求"而非"命令"，Host 可以基于治理策略拒绝导航。</p>
     *
     * @param pageId 目标页面 ID（格式：{pluginId}:{pageName}）
     * @param params 页面参数
     * @param options 导航选项
     * @returns 导航结果
     *
     * @since 3.2.0
     */
    requestNavigate?(pageId: string, params?: Record<string, unknown>, options?: WebNavigateOptions): Promise<NavigateResult>;
    /**
     * 检查是否可以导航到指定页面
     *
     * <p>预检查，不执行实际导航。</p>
     *
     * @param pageId 目标页面 ID
     * @returns 是否可以导航
     *
     * @since 3.2.0
     */
    canNavigate?(pageId: string): boolean;
    /**
     * 获取当前页面 ID
     *
     * @returns 当前页面 ID，无法识别时返回空字符串
     *
     * @since 3.2.0
     */
    getCurrentPageId?(): string;
    /**
     * 获取当前页面参数
     *
     * @typeParam T 参数类型
     * @returns 页面参数对象
     *
     * @since 3.2.0
     */
    getPageParams?<T extends Record<string, unknown> = Record<string, unknown>>(): T;
    /**
     * 请求返回上一页
     *
     * @returns 导航结果
     *
     * @since 3.2.0
     */
    requestGoBack?(): Promise<NavigateResult>;
    /**
     * 订阅页面变化事件
     *
     * @param handler 页面变化处理器
     * @returns 取消订阅函数
     *
     * @since 3.2.0
     */
    onPageChange?(handler: PageChangeHandler): Unsubscribe;
}
/**
 * 导航选项（兼容别名）
 */
export type NavigationOptions = NavigateOptions;
/**
 * 路由变更监听器
 *
 * <p>用于监听路由变化事件。</p>
 */
export type RouteChangeListener = (path: string) => void;
/**
 * Router 能力类型标识（兼容别名）
 *
 * @deprecated 请使用 NavigationCapabilityType。
 *             此别名将在 v4.0.0 中移除。
 *
 * @since 3.0.0
 * @see NavigationCapabilityType
 */
export declare const RouterCapabilityType: symbol;
/**
 * Router 能力（兼容别名）
 *
 * @deprecated 请使用 NavigationCapability。
 *             此别名将在 v4.0.0 中移除。
 *
 * @since 3.0.0
 * @see NavigationCapability
 */
export type RouterCapability = NavigationCapability;
//# sourceMappingURL=navigation.d.ts.map