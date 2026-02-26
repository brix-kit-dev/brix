/**
 * @file 插件相关类型定义
 * @description 定义插件系统的核心类型，包括清单、实例、生命周期等
 * @module @brix/runtime-sdk-api-web/types/plugin
 * @version 3.2.1
 *
 * 【v3.2.1 变更】
 * - 移除 React 依赖，使用框架无关的 ComponentType 定义（v3.0.4 架构红线修复）
 *
 * 【v3.2 变更】
 * 从 index.ts 拆分出独立的类型文件，并从 infra-adapters 上提通用契约。
 *
 * 【设计原则】
 * - 定义通用的插件契约，适配器（MF/Iframe/Native）可扩展
 * - 使用泛型支持不同的清单和实例类型
 * - 框架无关：不依赖 React/Vue/Angular 等 UI 框架
 */
import type { CapabilityRegistry } from './capability';
/**
 * 框架无关的组件类型
 *
 * <p>v3.0.4 架构红线修复：契约层不依赖任何 UI 框架。
 * 实际的组件类型由具体的适配器或 React 绑定层定义。</p>
 *
 * <p>使用方式：</p>
 * <ul>
 *   <li>在 React 项目中，使用 @brix/runtime-sdk-react 的类型定义</li>
 *   <li>在 Vue 项目中，使用 @brix/runtime-sdk-vue 的类型定义</li>
 *   <li>在框架无关场景，使用 unknown 并在运行时处理</li>
 * </ul>
 */
export type ComponentType = unknown;
/**
 * 插件状态
 *
 * <p>描述插件在生命周期中的当前状态。</p>
 */
export type PluginStatus = 'registered' | 'loading' | 'loaded' | 'activating' | 'active' | 'deactivating' | 'inactive' | 'error';
/**
 * 插件清单基础接口
 *
 * <p>各适配器（MF、Iframe、Native）的 Manifest 均须扩展此接口。</p>
 */
export interface PluginManifest {
    /** 插件唯一标识 */
    readonly id: string;
    /** 插件名称 */
    readonly name: string;
    /** 插件版本 */
    readonly version: string;
    /** 是否启用 */
    readonly enabled?: boolean;
}
/**
 * 插件元数据
 *
 * <p>描述插件的详细信息，包括依赖的能力、发布/订阅的事件等。</p>
 */
export interface PluginMetadata {
    /** 版本 */
    readonly version: string;
    /** 名称 */
    readonly name: string;
    /** 描述 */
    readonly description?: string;
    /** 所需能力列表 */
    readonly requiredCapabilities?: string[];
    /** 发布的事件列表 */
    readonly publishedEvents?: string[];
    /** 订阅的事件列表 */
    readonly subscribedEvents?: string[];
}
/**
 * 插件实例基础接口
 *
 * <p>各适配器的 Instance 均须扩展此接口。</p>
 *
 * @template M 清单类型
 */
export interface PluginInstance<M extends PluginManifest = PluginManifest> {
    /** 插件 ID */
    readonly id: string;
    /** 对应清单 */
    readonly manifest: M;
    /** 当前状态 */
    status: string;
    /** 错误信息 */
    readonly error?: Error;
}
/**
 * 插件加载器接口
 *
 * <p>各适配器（MFPluginLoader、IframePluginLoader、NativePluginLoader）
 * 均须实现此接口。</p>
 *
 * @template M 清单类型
 * @template I 实例类型
 */
export interface PluginLoader<M extends PluginManifest = PluginManifest, I extends PluginInstance<M> = PluginInstance<M>> {
    /** 加载单个插件 */
    load(manifest: M): Promise<I>;
    /** 卸载插件 */
    unload(pluginId: string): void;
    /** 预加载多个插件 */
    preload?(manifests: M[]): Promise<void>;
    /** 获取已加载插件列表 */
    getLoaded(): I[];
    /** 检查插件是否已加载 */
    isLoaded(pluginId: string): boolean;
}
/**
 * 插件加载错误
 *
 * <p>封装插件加载过程中发生的错误，包含错误阶段信息。</p>
 */
export declare class PluginLoadError extends Error {
    readonly pluginId: string;
    readonly phase: 'script' | 'init' | 'module' | 'component' | 'iframe' | 'bridge';
    readonly cause?: Error | undefined;
    constructor(message: string, pluginId: string, phase: 'script' | 'init' | 'module' | 'component' | 'iframe' | 'bridge', cause?: Error | undefined);
}
/**
 * 插件依赖声明
 *
 * <p>描述插件对其他插件的依赖关系，用于清单解析和依赖校验。</p>
 */
export interface PluginDependency {
    /** 依赖插件名称 */
    readonly name: string;
    /** 依赖版本号 */
    readonly version: string;
    /** Maven GroupId */
    readonly groupId: string;
    /** Maven ArtifactId（自动生成：{name}-core） */
    readonly artifactId: string;
}
/**
 * 插件入口配置
 *
 * <p>定义插件的加载入口和基本信息。</p>
 */
export interface PluginEntry {
    /** 插件唯一标识 */
    readonly id: string;
    /** 插件名称 */
    readonly name: string;
    /** 插件版本 */
    readonly version: string;
    /** 插件加载函数 */
    readonly loader: () => Promise<PluginLifecycle>;
    /** 依赖的插件 ID 列表 */
    readonly dependencies?: string[];
    /** 插件配置 */
    readonly config?: Record<string, unknown>;
}
/**
 * 插件生命周期接口
 *
 * <p>定义插件激活和停用时的回调方法。</p>
 */
export interface PluginLifecycle {
    /**
     * 插件激活时调用
     *
     * <p>在此方法中初始化插件资源、注册能力、贡献路由等。</p>
     *
     * @param context 插件上下文
     */
    activate(context: PluginContext): void | Promise<void>;
    /**
     * 插件停用时调用
     *
     * <p>在此方法中清理插件资源、取消订阅等。</p>
     */
    deactivate?(): void | Promise<void>;
}
/**
 * 插件上下文
 *
 * <p>提供给插件的运行时上下文，包含能力注册表和贡献方法。</p>
 */
export interface PluginContext {
    /** 插件 ID */
    readonly pluginId: string;
    /** 能力注册表 */
    readonly registry: CapabilityRegistry;
    /** 贡献路由 */
    contributeRoutes?(routes: RouteContribution[]): void;
    /** 贡献菜单 */
    contributeMenus?(menus: MenuContribution[]): void;
}
/**
 * 路由贡献
 *
 * <p>插件向 Host 贡献的路由配置。</p>
 */
export interface RouteContribution {
    /** 路由路径 */
    path: string;
    /** 路由组件 */
    component: ComponentType;
    /** 是否精确匹配 */
    exact?: boolean;
}
/**
 * 菜单贡献
 *
 * <p>插件向 Host 贡献的菜单配置。</p>
 */
export interface MenuContribution {
    /** 菜单 ID */
    id: string;
    /** 菜单标签 */
    label: string;
    /** 菜单图标 */
    icon?: string;
    /** 菜单路径 */
    path?: string;
    /** 子菜单 */
    children?: MenuContribution[];
}
/**
 * 路由页面配置
 *
 * <p>适配器使用的页面注册格式。</p>
 */
export interface PageConfig {
    /** 页面 ID（格式: pluginId:pageName） */
    readonly pageId: string;
    /** URL 路径 */
    readonly path: string;
    /** 页面组件 */
    readonly component: ComponentType;
}
//# sourceMappingURL=plugin.d.ts.map