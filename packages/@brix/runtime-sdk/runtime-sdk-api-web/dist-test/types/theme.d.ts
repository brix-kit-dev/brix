/**
 * @file 主题能力类型定义
 * @description 定义主题系统的核心类型，包括主题模式、颜色配置、主题切换等
 * @module @brix/runtime-sdk-api-web/types/theme
 * @version 3.2.0
 *
 * 【v3.2.0 新增】
 * Phase 1 契约层修复：将 ThemeCapability 接口从 shell-web 提升到 runtime-sdk-api-web。
 *
 * 【设计原则】
 * - 主题变量通过 CSS 变量实现，确保运行时可切换
 * - 插件不能直接修改全局样式，只能通过 ThemeCapability
 * - 主题切换通过事件通知所有模块
 * - 禁止在组件中硬编码颜色值
 *
 * 【架构红线】
 * ❌ 禁止直接操作 document.documentElement.style
 * ❌ 禁止硬编码颜色值
 * ❌ 禁止直接使用 localStorage 存储主题偏好
 * ✅ 通过 ThemeCapability 或 useTheme hook 操作主题
 */
import type { Unsubscribe } from './event';
/**
 * 主题模式
 *
 * - 'light': 亮色模式
 * - 'dark': 暗色模式
 * - 'system': 跟随系统设置
 */
export type ThemeMode = 'light' | 'dark' | 'system';
/**
 * 主题颜色配置
 *
 * <p>定义主题的核心颜色变量。</p>
 */
export interface ThemeColors {
    /** 主色（Primary Color） - 用于主要按钮、链接、高亮元素 */
    readonly primary: string;
    /** 次要色（Secondary Color） - 用于背景、边框、辅助元素 */
    readonly secondary: string;
    /** 第三色（Tertiary Color） - 用于页面底色、卡片背景 */
    readonly tertiary?: string;
    /** 成功色 */
    readonly success: string;
    /** 警告色 */
    readonly warning: string;
    /** 错误色 */
    readonly error: string;
    /** 信息色 */
    readonly info: string;
    /** 文本主色 */
    readonly textPrimary?: string;
    /** 文本次要色 */
    readonly textSecondary?: string;
    /** 文本禁用色 */
    readonly textDisabled?: string;
    /** 默认背景色 */
    readonly backgroundDefault?: string;
    /** 纸张/卡片背景色 */
    readonly backgroundPaper?: string;
    /** 边框色 */
    readonly border?: string;
    /** 分割线色 */
    readonly divider?: string;
}
/**
 * 主题配置
 *
 * <p>完整的主题配置，包括颜色和其他样式参数。</p>
 */
export interface ThemeConfig {
    /** 颜色配置 */
    readonly colors: ThemeColors;
    /** 字体族 */
    readonly fontFamily?: string;
    /** 基础字号 */
    readonly fontSize?: number;
    /** 圆角基数 */
    readonly borderRadius?: number;
    /** 间距基数 */
    readonly spacing?: number;
    /** 阴影配置 */
    readonly shadows?: {
        readonly sm?: string;
        readonly md?: string;
        readonly lg?: string;
    };
    /** 是否使用系统字体 */
    readonly useSystemFont?: boolean;
}
/**
 * 主题预设
 *
 * <p>预定义的主题配置，用于快速切换。</p>
 */
export interface ThemePreset {
    /** 预设 ID */
    readonly id: string;
    /** 预设名称 */
    readonly name: string;
    /** 预设描述 */
    readonly description?: string;
    /** 亮色模式配置 */
    readonly light: ThemeConfig;
    /** 暗色模式配置 */
    readonly dark: ThemeConfig;
}
/**
 * 主题状态
 *
 * <p>描述当前主题的完整状态。</p>
 */
export interface ThemeState {
    /** 用户选择的主题模式 */
    readonly mode: ThemeMode;
    /** 实际解析后的主题模式（system 会解析为 light 或 dark） */
    readonly resolvedMode: 'light' | 'dark';
    /** 当前主题配置 */
    readonly config: ThemeConfig;
    /** 当前预设 ID（如果使用预设） */
    readonly presetId?: string;
}
/**
 * 主题变更事件
 *
 * <p>当主题模式或配置变化时触发。</p>
 */
export interface ThemeChangeEvent {
    /** 新的主题模式 */
    readonly mode: ThemeMode;
    /** 新的解析模式 */
    readonly resolvedMode: 'light' | 'dark';
    /** 旧的主题模式 */
    readonly previousMode: ThemeMode;
    /** 新的主题配置 */
    readonly config: ThemeConfig;
    /** 变更来源 */
    readonly source: 'user' | 'system' | 'api';
    /** 变更时间戳 */
    readonly timestamp: number;
}
/**
 * 主题变更处理器
 */
export type ThemeChangeHandler = (event: ThemeChangeEvent) => void;
/**
 * 主题能力类型标识
 */
export declare const ThemeCapabilityType: unique symbol;
/**
 * 主题能力契约
 *
 * <p>为插件提供主题管理能力，包括模式切换、颜色获取、主题配置等。</p>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>主题由 Host 统一控制</li>
 *   <li>插件只能读取主题或请求切换</li>
 *   <li>主题变量通过 CSS 变量自动应用</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * ```typescript
 * const theme = context.getCapability<ThemeCapability>(ThemeCapabilityType);
 *
 * // 获取当前模式
 * const mode = theme.getMode();
 *
 * // 切换模式
 * theme.setMode('dark');
 * theme.toggleMode();
 *
 * // 获取颜色
 * const primaryColor = theme.getColor('primary');
 *
 * // 监听主题变化
 * const unsubscribe = theme.onThemeChange((event) => {
 *   console.log(`主题切换到 ${event.resolvedMode}`);
 * });
 * ```
 *
 * @since 3.2.0
 */
export interface ThemeCapability {
    /**
     * 获取当前主题模式
     *
     * @returns 主题模式
     */
    getMode(): ThemeMode;
    /**
     * 获取实际解析后的主题模式
     *
     * <p>当 mode='system' 时，返回实际的 'light' 或 'dark'。</p>
     *
     * @returns 解析后的模式
     */
    getResolvedMode(): 'light' | 'dark';
    /**
     * 设置主题模式
     *
     * @param mode 目标模式
     */
    setMode(mode: ThemeMode): void;
    /**
     * 切换亮色/暗色模式
     *
     * <p>在 light 和 dark 之间切换。如果当前是 system，则切换到与当前系统相反的模式。</p>
     */
    toggleMode(): void;
    /**
     * 获取当前主题配置
     *
     * @returns 主题配置对象
     */
    getConfig(): ThemeConfig;
    /**
     * 获取指定颜色值
     *
     * @param colorKey 颜色键名
     * @returns 颜色值（CSS 格式）
     */
    getColor(colorKey: keyof ThemeColors): string;
    /**
     * 获取 CSS 变量值
     *
     * @param varName CSS 变量名（不含 --）
     * @returns 变量值
     */
    getCssVar?(varName: string): string;
    /**
     * 获取完整主题状态
     *
     * @returns 主题状态对象
     */
    getState?(): ThemeState;
    /**
     * 获取可用预设列表
     *
     * @returns 预设数组
     */
    getPresets?(): ThemePreset[];
    /**
     * 应用指定预设
     *
     * @param presetId 预设 ID
     * @returns 是否成功
     */
    applyPreset?(presetId: string): boolean;
    /**
     * 获取当前预设 ID
     *
     * @returns 预设 ID，未使用预设时返回 undefined
     */
    getCurrentPresetId?(): string | undefined;
    /**
     * 订阅主题变化事件
     *
     * @param handler 事件处理器
     * @returns 取消订阅函数
     */
    onThemeChange?(handler: ThemeChangeHandler): Unsubscribe;
}
//# sourceMappingURL=theme.d.ts.map