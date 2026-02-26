/**
 * @file 状态相关类型定义
 * @description 定义状态管理系统的核心类型，包括状态变更事件、监听器等
 * @module @brix/runtime-sdk-api-web/types/state
 * @version 3.2.0
 *
 * 【v3.2 变更】
 * 从 index.ts 拆分出独立的类型文件，并从 infra-adapter-state-web 上提通用契约。
 *
 * 【v3.2.0 Phase 1 契约层修复】
 * 补全 PluginStateCapabilityImpl 依赖的类型：
 * - StatePersistenceOptions: 状态持久化选项
 * - PluginStateChangeEvent: 插件状态变化事件
 * - PluginStateSubscribeOptions: 插件状态订阅选项
 * - PluginStateCapability 接口扩展：get/set 新方法签名
 *
 * 【设计原则】
 * - 定义通用的状态管理契约，状态适配器实现具体存储逻辑
 * - 支持命名空间隔离（每个插件独立状态空间）
 * - 支持状态变更订阅
 */
/**
 * 插件状态存储结构
 *
 * <p>顶层按插件 ID 分区，每个插件有独立的状态命名空间。</p>
 *
 * <h3>命名空间隔离机制</h3>
 * <ul>
 *   <li>booking 插件：state.booking.*</li>
 *   <li>identity 插件：state.identity.*</li>
 * </ul>
 * <p>插件 A 无法直接读写插件 B 的状态。</p>
 */
export interface PluginStoreState {
    /** 按插件 ID 分区的状态 */
    [pluginId: string]: Record<string, unknown>;
}
/**
 * 状态变更事件
 *
 * <p>描述状态变更的详细信息，用于可观测性和调试。</p>
 */
export interface StateChangeEvent {
    /** 插件 ID */
    readonly pluginId: string;
    /** 状态键 */
    readonly key: string;
    /** 旧值 */
    readonly oldValue: unknown;
    /** 新值 */
    readonly newValue: unknown;
    /** 时间戳 */
    readonly timestamp?: number;
}
/**
 * 状态变更监听器
 */
export type StateChangeListener = (event: StateChangeEvent) => void;
/**
 * 状态持久化选项
 *
 * <p>控制状态的持久化行为。</p>
 *
 * @since 3.2.0
 */
export interface StatePersistenceOptions {
    /**
     * 是否持久化到本地存储
     *
     * @default false
     */
    readonly persist?: boolean;
    /**
     * 存储位置
     *
     * - 'localStorage': 持久存储，浏览器关闭仍保留
     * - 'sessionStorage': 会话存储，浏览器关闭后清除
     *
     * @default 'localStorage'
     */
    readonly storage?: 'localStorage' | 'sessionStorage';
    /**
     * 过期时间（毫秒）
     *
     * <p>设置后状态将在指定时间后自动清除。</p>
     */
    readonly ttl?: number;
    /**
     * 序列化器名称
     *
     * <p>用于自定义序列化/反序列化逻辑。</p>
     */
    readonly serializer?: string;
}
/**
 * 插件状态变化事件
 *
 * <p>描述插件状态变更的详细信息。与 StateChangeEvent 类似，
 * 但专门用于插件状态订阅回调。</p>
 *
 * @since 3.2.0
 */
export interface PluginStateChangeEvent<T = unknown> {
    /** 状态键（不含命名空间前缀） */
    readonly key: string;
    /** 完整状态键（含命名空间前缀） */
    readonly fullKey: string;
    /** 插件 ID */
    readonly pluginId: string;
    /** 新值 */
    readonly value: T;
    /** 旧值 */
    readonly previousValue?: T;
    /** 变更时间戳 */
    readonly timestamp: number;
}
/**
 * 插件状态订阅选项
 *
 * <p>控制状态订阅行为的配置参数。</p>
 *
 * @since 3.2.0
 */
export interface PluginStateSubscribeOptions {
    /**
     * 是否立即触发一次回调
     *
     * <p>如果为 true，订阅时会立即调用一次回调函数，传入当前值。</p>
     *
     * @default false
     */
    readonly fireImmediately?: boolean;
    /**
     * 比较函数
     *
     * <p>用于判断新旧值是否相等，返回 true 表示相等（不触发回调）。</p>
     */
    readonly equalityFn?: (a: unknown, b: unknown) => boolean;
}
/**
 * 插件状态能力类型标识
 */
export declare const PluginStateCapabilityType: unique symbol;
/**
 * 插件状态能力契约
 *
 * <p>为插件提供隔离的状态管理能力，替代直接使用 localStorage/sessionStorage/zustand。</p>
 *
 * <h3>使用示例</h3>
 * ```typescript
 * const state = context.getCapability<PluginStateCapability>(PluginStateCapabilityType);
 * state.set('selectedDate', new Date());
 * const date = state.get<Date>('selectedDate');
 *
 * // 订阅状态变更
 * const unsubscribe = state.subscribe('selectedDate', (value) => {
 *   console.log('Date changed:', value);
 * });
 * ```
 *
 * <h3>架构说明</h3>
 * <ul>
 *   <li>每个插件的状态在独立的命名空间下</li>
 *   <li>跨插件状态共享通过 EventBus</li>
 *   <li>禁止直接访问其他插件的状态</li>
 * </ul>
 *
 * @since 3.2.0 扩展方法：get/set, delete, has, getOrDefault, update, reset, keys, getAll, setMany
 */
export interface PluginStateCapability {
    /**
     * 获取状态值
     *
     * @param key 状态键
     * @returns 状态值，不存在时返回 undefined
     */
    get<T>(key: string): T | undefined;
    /**
     * 设置状态值
     *
     * @param key 状态键
     * @param value 状态值
     * @param options 持久化选项
     */
    set<T>(key: string, value: T, options?: StatePersistenceOptions): void;
    /**
     * 删除状态
     *
     * @param key 状态键
     * @returns 是否删除成功
     * @since 3.2.0
     */
    delete?(key: string): boolean;
    /**
     * 检查状态是否存在
     *
     * @param key 状态键
     * @returns 是否存在
     * @since 3.2.0
     */
    has?(key: string): boolean;
    /**
     * 获取状态值（带默认值）
     *
     * @param key 状态键
     * @param defaultValue 默认值
     * @returns 状态值或默认值
     * @since 3.2.0
     */
    getOrDefault?<T>(key: string, defaultValue: T): T;
    /**
     * 更新状态值
     *
     * @param key 状态键
     * @param updater 更新函数
     * @since 3.2.0
     */
    update?<T>(key: string, updater: (currentValue: T | undefined) => T): void;
    /**
     * 重置插件所有状态
     *
     * @since 3.2.0
     */
    reset?(): void;
    /**
     * 获取所有状态键
     *
     * @returns 状态键数组
     * @since 3.2.0
     */
    keys?(): string[];
    /**
     * 获取所有状态
     *
     * @returns 状态对象
     * @since 3.2.0
     */
    getAll?<T extends Record<string, unknown> = Record<string, unknown>>(): T;
    /**
     * 批量设置状态
     *
     * @param states 状态对象
     * @since 3.2.0
     */
    setMany?(states: Record<string, unknown>): void;
    /**
     * 订阅状态变更
     *
     * @param key 状态键
     * @param listener 变更监听器
     * @param options 订阅选项
     * @returns 取消订阅函数
     */
    subscribe<T>(key: string, listener: (value: T, event?: PluginStateChangeEvent<T>) => void, options?: PluginStateSubscribeOptions): () => void;
    /**
     * 选择性订阅状态变更
     *
     * @param selector 选择器函数
     * @param listener 变化监听器
     * @returns 取消订阅函数
     * @since 3.2.0
     */
    select?<T>(selector: (state: Record<string, unknown>) => T, listener: (value: T, previousValue: T | undefined) => void): () => void;
    /**
     * 销毁能力实例
     *
     * @since 3.2.0
     */
    destroy?(): void;
}
/**
 * 状态存储能力类型标识（兼容别名）
 *
 * <p>与 Java 端 StateStoreCapability 名称对齐的别名。
 * 建议新代码使用 PluginStateCapability，此别名用于迁移兼容。</p>
 *
 * @since 3.2.0
 * @see PluginStateCapabilityType
 */
export declare const StateStoreCapabilityType: symbol;
/**
 * 状态存储能力（兼容别名）
 *
 * <p>与 Java 端 StateStoreCapability 名称对齐的别名。
 * 建议新代码使用 PluginStateCapability，此别名用于迁移兼容。</p>
 *
 * @since 3.2.0
 * @see PluginStateCapability
 */
export type StateStoreCapability = PluginStateCapability;
//# sourceMappingURL=state.d.ts.map