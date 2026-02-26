/**
 * @file 模块相关类型定义
 * @description 定义模块系统的核心类型，包括模块元数据、状态、生命周期事件等
 * @module @brix/runtime-sdk-api-web/types/module
 * @version 3.2.0
 *
 * 【v3.2 变更】
 * 从 index.ts 拆分出独立的类型文件。
 */
/**
 * 模块元数据
 *
 * <p>描述模块的基本信息。</p>
 */
export interface ModuleMetadata {
    /** 模块 ID */
    readonly moduleId: string;
    /** 模块名称 */
    readonly name: string;
    /** 模块版本 */
    readonly version: string;
    /** 模块描述 */
    readonly description?: string;
    /** 作者 */
    readonly author?: string;
    /** 依赖模块列表 */
    readonly dependencies?: string[];
}
/**
 * 模块状态枚举
 */
export declare enum ModuleState {
    /** 未加载 */
    UNLOADED = "UNLOADED",
    /** 加载中 */
    LOADING = "LOADING",
    /** 已加载 */
    LOADED = "LOADED",
    /** 已激活 */
    ACTIVE = "ACTIVE",
    /** 错误状态 */
    ERROR = "ERROR"
}
/**
 * 模块生命周期事件枚举
 */
export declare enum ModuleLifecycleEvent {
    /** 加载前 */
    BEFORE_LOAD = "BEFORE_LOAD",
    /** 加载后 */
    AFTER_LOAD = "AFTER_LOAD",
    /** 激活前 */
    BEFORE_ACTIVATE = "BEFORE_ACTIVATE",
    /** 激活后 */
    AFTER_ACTIVATE = "AFTER_ACTIVATE",
    /** 停用前 */
    BEFORE_DEACTIVATE = "BEFORE_DEACTIVATE",
    /** 停用后 */
    AFTER_DEACTIVATE = "AFTER_DEACTIVATE",
    /** 错误 */
    ERROR = "ERROR"
}
//# sourceMappingURL=module.d.ts.map