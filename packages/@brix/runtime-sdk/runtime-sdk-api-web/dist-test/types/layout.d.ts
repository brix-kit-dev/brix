/**
 * @file 布局能力类型定义
 * @description 定义布局系统的核心类型，包括侧边栏、头部、全屏等布局控制
 * @module @brix/runtime-sdk-api-web/types/layout
 * @version 3.2.0
 *
 * 【v3.2.0 新增】
 * Phase 1 契约层修复：将 LayoutCapability 接口从 shell-web 提升到 runtime-sdk-api-web。
 *
 * 【设计原则】
 * - 布局完全由 Host 控制
 * - 插件只能"请求"布局行为，Host 决定是否响应
 * - 所有请求都会经过治理策略检查
 *
 * 【架构红线】
 * ❌ 禁止直接操作 document.body
 * ❌ 禁止创建全局 Portal 到 body
 * ❌ 禁止修改全局 CSS（如 overflow）
 * ✅ 通过 LayoutCapability 或 useLayout hook 请求布局变更
 */
import type { Unsubscribe } from './event';
/**
 * 布局模式
 *
 * - 'console': 控制台布局（带侧边栏和头部）
 * - 'portal': 门户布局（简化的头部，无侧边栏）
 * - 'minimal': 极简布局（仅内容区）
 */
export type LayoutMode = 'console' | 'portal' | 'minimal';
/**
 * 布局状态
 *
 * <p>描述当前布局的完整状态。</p>
 */
export interface LayoutState {
    /** 是否全屏 */
    readonly fullscreen: boolean;
    /** 侧边栏是否可见 */
    readonly sidebarVisible: boolean;
    /** 侧边栏是否折叠 */
    readonly sidebarCollapsed: boolean;
    /** 头部是否可见 */
    readonly headerVisible: boolean;
    /** 底部是否可见 */
    readonly footerVisible: boolean;
    /** 当前布局模式 */
    readonly layoutMode: LayoutMode;
    /** 当前断点 */
    readonly breakpoint: 'xs' | 'sm' | 'md' | 'lg' | 'xl' | 'xxl';
    /** 是否为移动端视图 */
    readonly isMobile: boolean;
    /** 侧边栏宽度（展开状态） */
    readonly sidebarWidth: number;
    /** 侧边栏宽度（折叠状态） */
    readonly sidebarCollapsedWidth: number;
    /** 头部高度 */
    readonly headerHeight: number;
    /** 内容区可用宽度 */
    readonly contentWidth?: number;
    /** 内容区可用高度 */
    readonly contentHeight?: number;
}
/**
 * 布局请求结果
 *
 * <p>描述布局变更请求的执行结果。</p>
 */
export interface LayoutRequestResult {
    /**
     * 请求是否成功
     */
    readonly success: boolean;
    /**
     * 失败原因
     *
     * - 'policy_denied': 治理策略拒绝
     * - 'not_supported': 当前布局模式不支持此操作
     * - 'already_applied': 已经是目标状态
     */
    readonly reason?: 'policy_denied' | 'not_supported' | 'already_applied';
    /**
     * 详细消息
     */
    readonly message?: string;
}
/**
 * 布局变更事件
 *
 * <p>当布局状态发生变化时触发。</p>
 */
export interface LayoutChangeEvent {
    /** 变更类型 */
    readonly type: 'fullscreen' | 'sidebar' | 'header' | 'footer' | 'breakpoint' | 'mode';
    /** 新状态 */
    readonly state: LayoutState;
    /** 旧状态 */
    readonly previousState: LayoutState;
    /** 变更来源 */
    readonly source: 'user' | 'plugin' | 'system';
    /** 发起变更的插件 ID（如果来源是 plugin） */
    readonly pluginId?: string;
    /** 变更时间戳 */
    readonly timestamp: number;
}
/**
 * 布局变更处理器
 */
export type LayoutChangeHandler = (event: LayoutChangeEvent) => void;
/**
 * 布局能力类型标识
 */
export declare const LayoutCapabilityType: unique symbol;
/**
 * 布局能力契约
 *
 * <p>为插件提供布局控制能力，包括全屏、侧边栏、头部等。</p>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>布局完全由 Host 控制</li>
 *   <li>插件只能"请求"布局行为，Host 决定是否响应</li>
 *   <li>所有请求都会经过治理策略检查</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * ```typescript
 * const layout = context.getCapability<LayoutCapability>(LayoutCapabilityType);
 *
 * // 请求全屏
 * const success = await layout.requestFullscreen();
 *
 * // 获取当前状态
 * const state = layout.getState();
 * if (state.isMobile) {
 *   // 移动端处理...
 * }
 *
 * // 监听布局变化
 * const unsubscribe = layout.onLayoutChange((event) => {
 *   console.log(`布局变更: ${event.type}`);
 * });
 * ```
 *
 * @since 3.2.0
 */
export interface LayoutCapability {
    /**
     * 获取当前布局状态
     *
     * @returns 布局状态对象
     */
    getState(): LayoutState;
    /**
     * 检查是否全屏
     *
     * @returns 是否全屏
     */
    isFullscreen(): boolean;
    /**
     * 检查侧边栏是否可见
     *
     * @returns 是否可见
     */
    isSidebarVisible(): boolean;
    /**
     * 检查侧边栏是否折叠
     *
     * @returns 是否折叠
     */
    isSidebarCollapsed(): boolean;
    /**
     * 请求进入全屏模式
     *
     * @returns 是否成功
     */
    requestFullscreen(): Promise<boolean>;
    /**
     * 请求退出全屏模式
     *
     * @returns 是否成功
     */
    requestExitFullscreen(): Promise<boolean>;
    /**
     * 请求隐藏侧边栏
     *
     * @returns 是否成功
     */
    requestHideSidebar(): Promise<boolean>;
    /**
     * 请求显示侧边栏
     *
     * @returns 是否成功
     */
    requestShowSidebar(): Promise<boolean>;
    /**
     * 请求折叠侧边栏
     *
     * @returns 是否成功
     */
    requestCollapseSidebar?(): Promise<boolean>;
    /**
     * 请求展开侧边栏
     *
     * @returns 是否成功
     */
    requestExpandSidebar?(): Promise<boolean>;
    /**
     * 请求切换侧边栏折叠状态
     *
     * @returns 是否成功
     */
    requestToggleSidebar?(): Promise<boolean>;
    /**
     * 请求隐藏头部
     *
     * @returns 是否成功
     */
    requestHideHeader?(): Promise<boolean>;
    /**
     * 请求显示头部
     *
     * @returns 是否成功
     */
    requestShowHeader?(): Promise<boolean>;
    /**
     * 请求隐藏底部
     *
     * @returns 是否成功
     */
    requestHideFooter?(): Promise<boolean>;
    /**
     * 请求显示底部
     *
     * @returns 是否成功
     */
    requestShowFooter?(): Promise<boolean>;
    /**
     * 请求布局变更
     *
     * <p>可同时变更多个布局参数。</p>
     *
     * @param changes 布局变更参数
     * @returns 请求结果
     */
    requestLayoutChange?(changes: Partial<{
        fullscreen: boolean;
        sidebarVisible: boolean;
        sidebarCollapsed: boolean;
        headerVisible: boolean;
        footerVisible: boolean;
    }>): Promise<LayoutRequestResult>;
    /**
     * 切换布局模式
     *
     * @param mode 目标布局模式
     * @returns 是否成功
     */
    setLayoutMode?(mode: LayoutMode): Promise<boolean>;
    /**
     * 获取当前断点
     *
     * @returns 断点名称
     */
    getBreakpoint?(): 'xs' | 'sm' | 'md' | 'lg' | 'xl' | 'xxl';
    /**
     * 检查是否为移动端视图
     *
     * @returns 是否为移动端
     */
    isMobileView?(): boolean;
    /**
     * 订阅布局变化事件
     *
     * @param handler 事件处理器
     * @returns 取消订阅函数
     */
    onLayoutChange?(handler: LayoutChangeHandler): Unsubscribe;
}
//# sourceMappingURL=layout.d.ts.map