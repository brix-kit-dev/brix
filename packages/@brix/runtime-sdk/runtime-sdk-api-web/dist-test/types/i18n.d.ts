/**
 * @file 国际化能力类型定义
 * @description 定义国际化系统的核心类型，包括语言切换、翻译、日期/数字格式化等
 * @module @brix/runtime-sdk-api-web/types/i18n
 * @version 3.2.0
 *
 * 【v3.2.0 新增】
 * Phase 1 契约层修复：将 I18nCapability 接口提升到 runtime-sdk-api-web。
 *
 * 【设计原则】
 * - 插件通过 I18nCapability 获取翻译，禁止硬编码文本
 * - 翻译 key 采用命名空间格式：{模块名}:{翻译key}
 * - 语言切换通过事件通知所有模块
 * - 支持日期、数字、相对时间等本地化格式化
 *
 * 【架构红线】
 * ❌ 禁止在组件中硬编码文本
 * ❌ 禁止直接使用 i18next 等库
 * ✅ 通过 I18nCapability 或 useI18n hook 获取翻译
 */
import type { Unsubscribe } from './event';
/**
 * 语言代码
 *
 * <p>符合 BCP 47 规范的语言标签。</p>
 *
 * @example
 * - 'zh-CN': 简体中文
 * - 'zh-TW': 繁体中文
 * - 'en-US': 美式英语
 * - 'ja-JP': 日语
 */
export type LocaleCode = string;
/**
 * 语言信息
 *
 * <p>描述一种支持的语言。</p>
 */
export interface LanguageInfo {
    /** 语言代码 */
    readonly code: LocaleCode;
    /** 语言名称（本地化显示名） */
    readonly name: string;
    /** 语言的英文名称 */
    readonly englishName: string;
    /** 是否为 RTL（从右到左）语言 */
    readonly rtl?: boolean;
    /** 是否为默认语言 */
    readonly isDefault?: boolean;
}
/**
 * 翻译选项
 *
 * <p>控制翻译行为的配置参数。</p>
 */
export interface TranslateOptions {
    /**
     * 插值变量
     *
     * <p>用于替换翻译文本中的占位符。</p>
     *
     * @example
     * ```typescript
     * // 翻译模板: "欢迎，{{name}}！"
     * t('common:welcome', { name: '张三' }) // => "欢迎，张三！"
     * ```
     */
    readonly [key: string]: unknown;
    /**
     * 默认值
     *
     * <p>当翻译 key 不存在时返回此值。</p>
     */
    readonly defaultValue?: string;
    /**
     * 指定语言
     *
     * <p>使用指定语言翻译，而非当前语言。</p>
     */
    readonly lng?: LocaleCode;
    /**
     * 指定命名空间
     *
     * <p>覆盖 key 中的命名空间。</p>
     */
    readonly ns?: string;
    /**
     * 复数数量
     *
     * <p>用于复数规则选择。</p>
     */
    readonly count?: number;
}
/**
 * 语言包
 *
 * <p>一个命名空间下的翻译资源。</p>
 */
export interface LanguageBundle {
    /** 语言代码 */
    readonly locale: LocaleCode;
    /** 命名空间 */
    readonly namespace: string;
    /** 翻译资源（key-value 对） */
    readonly resources: Record<string, string>;
}
/**
 * 日期格式化选项
 *
 * <p>基于 Intl.DateTimeFormatOptions 的子集。</p>
 */
export interface DateFormatOptions {
    /** 日期样式 */
    readonly dateStyle?: 'full' | 'long' | 'medium' | 'short';
    /** 时间样式 */
    readonly timeStyle?: 'full' | 'long' | 'medium' | 'short';
    /** 时区 */
    readonly timeZone?: string;
    /** 是否使用 12 小时制 */
    readonly hour12?: boolean;
}
/**
 * 数字格式化选项
 *
 * <p>基于 Intl.NumberFormatOptions 的子集。</p>
 */
export interface NumberFormatOptions {
    /** 格式化样式 */
    readonly style?: 'decimal' | 'currency' | 'percent' | 'unit';
    /** 货币代码（style='currency' 时必需） */
    readonly currency?: string;
    /** 货币显示方式 */
    readonly currencyDisplay?: 'symbol' | 'narrowSymbol' | 'code' | 'name';
    /** 最小小数位数 */
    readonly minimumFractionDigits?: number;
    /** 最大小数位数 */
    readonly maximumFractionDigits?: number;
    /** 是否使用分组（千分位） */
    readonly useGrouping?: boolean;
}
/**
 * 相对时间格式化选项
 */
export interface RelativeTimeFormatOptions {
    /** 相对时间单位 */
    readonly unit?: 'year' | 'quarter' | 'month' | 'week' | 'day' | 'hour' | 'minute' | 'second';
    /** 显示样式 */
    readonly style?: 'long' | 'short' | 'narrow';
    /** 数值显示方式 */
    readonly numeric?: 'always' | 'auto';
}
/**
 * 语言切换事件
 *
 * <p>当语言切换时触发的事件。</p>
 */
export interface LocaleChangeEvent {
    /** 新语言代码 */
    readonly locale: LocaleCode;
    /** 旧语言代码 */
    readonly previousLocale: LocaleCode;
    /** 切换时间戳 */
    readonly timestamp: number;
}
/**
 * 语言切换监听器
 */
export type LocaleChangeListener = (event: LocaleChangeEvent) => void;
/**
 * 国际化能力类型标识
 */
export declare const I18nCapabilityType: unique symbol;
/**
 * 国际化能力契约
 *
 * <p>为插件提供多语言支持能力，包括翻译、日期/数字格式化等。</p>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>翻译 key 采用命名空间格式：{模块名}:{翻译key}</li>
 *   <li>语言包在模块加载时注册</li>
 *   <li>语言切换通过事件通知所有模块</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * ```typescript
 * const i18n = context.getCapability<I18nCapability>(I18nCapabilityType);
 *
 * // 获取翻译
 * const greeting = i18n.t('booking:greeting', { name: '张三' });
 *
 * // 格式化日期
 * const dateStr = i18n.formatDate(new Date(), { dateStyle: 'long' });
 *
 * // 切换语言
 * await i18n.setLocale('en-US');
 *
 * // 监听语言变化
 * const unsubscribe = i18n.onLocaleChange((event) => {
 *   console.log(`语言从 ${event.previousLocale} 切换到 ${event.locale}`);
 * });
 * ```
 *
 * @since 3.2.0
 */
export interface I18nCapability {
    /**
     * 获取翻译文本
     *
     * @param key 翻译 key（格式：{命名空间}:{key}）
     * @param options 翻译选项（插值变量等）
     * @returns 翻译后的文本
     *
     * @example
     * ```typescript
     * // 简单翻译
     * i18n.t('booking:pageTitle') // => "预约管理"
     *
     * // 带插值
     * i18n.t('booking:welcome', { name: '张三' }) // => "欢迎，张三！"
     *
     * // 带复数
     * i18n.t('booking:itemCount', { count: 5 }) // => "5 个项目"
     * ```
     */
    t(key: string, options?: TranslateOptions): string;
    /**
     * 检查翻译 key 是否存在
     *
     * @param key 翻译 key
     * @param options 选项
     * @returns 是否存在
     */
    exists(key: string, options?: {
        lng?: LocaleCode;
        ns?: string;
    }): boolean;
    /**
     * 获取当前语言
     *
     * @returns 当前语言代码
     */
    getLocale(): LocaleCode;
    /**
     * 设置当前语言
     *
     * <p>切换语言后会触发 LocaleChangeEvent。</p>
     *
     * @param locale 目标语言代码
     * @returns 是否切换成功
     */
    setLocale(locale: LocaleCode): Promise<boolean>;
    /**
     * 获取支持的语言列表
     *
     * @returns 语言信息数组
     */
    getSupportedLocales(): LanguageInfo[];
    /**
     * 注册语言包
     *
     * @param bundle 语言包定义
     */
    addResourceBundle(bundle: LanguageBundle): void;
    /**
     * 批量注册语言包
     *
     * @param bundles 语言包数组
     */
    addResourceBundles?(bundles: LanguageBundle[]): void;
    /**
     * 格式化日期
     *
     * @param date 日期对象或时间戳
     * @param options 格式化选项
     * @returns 格式化后的日期字符串
     */
    formatDate?(date: Date | number, options?: DateFormatOptions): string;
    /**
     * 格式化数字
     *
     * @param value 数值
     * @param options 格式化选项
     * @returns 格式化后的数字字符串
     */
    formatNumber?(value: number, options?: NumberFormatOptions): string;
    /**
     * 格式化货币
     *
     * @param value 金额
     * @param currency 货币代码（如 'CNY', 'USD'）
     * @param options 格式化选项
     * @returns 格式化后的货币字符串
     */
    formatCurrency?(value: number, currency: string, options?: Omit<NumberFormatOptions, 'style' | 'currency'>): string;
    /**
     * 格式化相对时间
     *
     * @param date 日期对象或时间戳
     * @param options 格式化选项
     * @returns 相对时间字符串（如"3天前"）
     */
    formatRelativeTime?(date: Date | number, options?: RelativeTimeFormatOptions): string;
    /**
     * 订阅语言变化事件
     *
     * @param listener 事件监听器
     * @returns 取消订阅函数
     */
    onLocaleChange?(listener: LocaleChangeListener): Unsubscribe;
}
//# sourceMappingURL=i18n.d.ts.map