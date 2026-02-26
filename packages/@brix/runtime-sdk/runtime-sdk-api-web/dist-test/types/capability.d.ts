/**
 * @file 能力相关类型定义
 * @description 定义能力系统的核心类型，包括能力元数据、状态、注册表等
 * @module @brix/runtime-sdk-api-web/types/capability
 * @version 3.2.0
 *
 * 【v3.2 变更】
 * 从 index.ts 拆分出独立的类型文件，保持契约层纯净。
 *
 * 【设计原则】
 * - 所有类型定义均为只读属性（readonly）
 * - 使用 Symbol 作为能力唯一标识（类型安全）
 * - 支持能力的延迟初始化和作用域控制
 */
/**
 * 能力优先级枚举
 *
 * <p>用于在多个能力提供者冲突时确定使用哪个实现。</p>
 */
export declare enum CapabilityPriority {
    /** 低优先级（后备实现） */
    LOW = 0,
    /** 普通优先级（默认） */
    NORMAL = 50,
    /** 高优先级（覆盖默认实现） */
    HIGH = 100
}
/**
 * 能力元数据（简化版）
 */
export interface CapabilityMetadata {
    /** 能力名称 */
    readonly name: string;
    /** 能力版本 */
    readonly version: string;
    /** 优先级 */
    readonly priority: CapabilityPriority;
    /** 是否必需 */
    readonly required: boolean;
}
/**
 * 能力 ID 类型
 *
 * <p>支持字符串或 Symbol，推荐使用 Symbol.for() 创建以确保唯一性。</p>
 */
export type CapabilityId = string | symbol;
/**
 * 能力元信息（完整版）
 *
 * <p>包含能力的完整元数据，用于能力注册和发现。</p>
 */
export interface CapabilityMeta {
    /** 能力唯一标识 */
    readonly id: CapabilityId;
    /** 能力名称（人类可读） */
    readonly name: string;
    /** 能力描述 */
    readonly description?: string;
    /** 能力版本 */
    readonly version?: string;
    /** 依赖的能力 ID 列表 */
    readonly dependencies?: CapabilityId[];
    /** 标签（用于分类筛选） */
    readonly tags?: string[];
}
/**
 * 能力状态
 *
 * <p>描述能力在生命周期中的当前状态。</p>
 */
export type CapabilityStatus = 'registered' | 'initializing' | 'ready' | 'error' | 'disposed';
/**
 * 能力类型标识（泛型接口）
 *
 * <p>用于标识和创建能力实例，包含能力的元数据信息。
 * 使用幻像属性 `_phantom` 进行类型推断。</p>
 *
 * @example
 * ```typescript
 * interface MyCapability {
 *   doSomething(): void;
 * }
 *
 * const MyCapabilityType = createCapabilityType<MyCapability>({
 *   id: 'my-capability',
 *   name: 'My Capability',
 * });
 * ```
 */
export interface CapabilityType<T = unknown> extends CapabilityMeta {
    /**
     * 幻像属性，用于类型推断
     * 实际运行时不存在，仅用于 TypeScript 类型系统
     */
    readonly _phantom?: T;
}
/**
 * 创建能力类型标识
 *
 * @param meta - 能力元信息
 * @returns 能力类型标识对象
 */
export declare function createCapabilityType<T>(meta: Omit<CapabilityMeta, 'id'> & {
    id: string;
}): CapabilityType<T>;
/**
 * 能力提供者接口
 *
 * <p>封装能力实例的创建和销毁逻辑。</p>
 */
export interface CapabilityProvider<T = unknown> {
    /** 获取能力实例 */
    provide(): T;
    /** 销毁能力实例（可选） */
    dispose?(): void;
}
/**
 * 简单能力提供者
 *
 * <p>可以是能力实例本身，或返回能力实例的工厂函数。</p>
 */
export type SimpleCapabilityProvider<T> = T | (() => T);
/**
 * 能力注册选项
 */
export interface CapabilityRegisterOptions {
    /**
     * 是否覆盖已存在的能力
     */
    override?: boolean;
    /**
     * 能力优先级
     */
    priority?: CapabilityPriority;
    /**
     * 能力作用域
     * - 'global': 全局作用域，所有模块共享
     * - 'module': 模块作用域，仅当前模块可用
     * @default 'global'
     */
    scope?: 'global' | 'module';
    /**
     * 延迟初始化
     * 为 true 时，能力在首次使用时才实例化
     * @default false
     */
    lazy?: boolean;
}
/**
 * 能力运行时信息
 *
 * <p>描述能力在运行时的详细状态。</p>
 */
export interface CapabilityRuntimeInfo {
    /** 能力元数据 */
    readonly meta: CapabilityMeta;
    /** 当前状态 */
    readonly status: CapabilityStatus;
    /** 注册时间戳 */
    readonly registeredAt: number;
    /** 初始化时间戳 */
    readonly initializedAt?: number;
    /** 错误信息（状态为 error 时） */
    readonly error?: Error;
    /** 调用次数统计 */
    readonly invocationCount: number;
}
/**
 * 能力注册表接口
 *
 * <p>管理能力的注册、获取、初始化和销毁。</p>
 */
export interface CapabilityRegistry {
    /** 获取能力实例 */
    get<T>(capabilityType: CapabilityType<T>): T | undefined;
    /** 获取必需能力（不存在时抛出异常） */
    getRequired<T>(capabilityType: CapabilityType<T>): T;
    /** 注册能力 */
    register<T>(capabilityType: CapabilityType<T>, provider: CapabilityProvider<T>, options?: CapabilityRegisterOptions): void;
    /** 注销能力 */
    unregister<T>(capabilityType: CapabilityType<T>): boolean;
    /** 检查能力是否已注册 */
    has<T>(capabilityType: CapabilityType<T>): boolean;
    /** 检查能力是否就绪 */
    isReady<T>(capabilityType: CapabilityType<T>): boolean;
    /** 获取能力运行时信息 */
    getInfo<T>(capabilityType: CapabilityType<T>): CapabilityRuntimeInfo | undefined;
    /** 获取所有已注册的能力 ID */
    getRegisteredIds(): CapabilityId[];
    /** 获取所有能力的运行时信息 */
    getAllInfo(): Map<CapabilityId, CapabilityRuntimeInfo>;
    /** 按状态筛选能力 */
    getByStatus(status: CapabilityStatus): CapabilityId[];
    /** 按标签筛选能力 */
    getByTag(tag: string): CapabilityId[];
    /** 初始化所有已注册的能力 */
    initializeAll(): Promise<boolean>;
    /** 销毁所有能力 */
    disposeAll(): Promise<void>;
    /** 重置注册表 */
    reset(): void;
}
//# sourceMappingURL=capability.d.ts.map