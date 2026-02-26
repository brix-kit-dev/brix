/**
 * @file 类型定义统一导出
 * @description 从各分类文件重新导出所有类型定义
 * @module @brix/runtime-sdk-api-web/types
 * @version 3.2.0
 *
 * 【v3.2 重构说明】
 * 将原 index.ts 中的 1000+ 行代码拆分为以下模块：
 * - capability.ts: 能力系统类型
 * - plugin.ts: 插件系统类型
 * - navigation.ts: 导航系统类型
 * - state.ts: 状态管理类型
 * - event.ts: 事件系统类型
 * - module.ts: 模块系统类型
 * - http.ts: HTTP 客户端能力类型
 * - auth.ts: 认证能力类型
 * - config.ts: 配置能力类型
 * - common.ts: 通用工具类型和 API 响应类型
 *
 * 【v3.2.0 Phase 1 契约层修复】
 * 新增以下能力接口类型文件：
 * - i18n.ts: 国际化能力类型（I18nCapability）
 * - theme.ts: 主题能力类型（ThemeCapability）
 * - layout.ts: 布局能力类型（LayoutCapability）
 *
 * 【设计原则】
 * - 每个文件职责单一
 * - 便于按需导入
 * - 便于维护和扩展
 */
export * from './capability';
export * from './plugin';
export * from './navigation';
export * from './state';
export * from './event';
export * from './module';
export * from './http';
export * from './auth';
export * from './config';
export * from './common';
export * from './i18n';
export * from './theme';
export * from './layout';
//# sourceMappingURL=index.d.ts.map