/**
 * @file @brix/runtime-sdk-api-web 统一入口
 * @description UI 能力契约定义 - Web 平台（框架无关）
 * @module @brix/runtime-sdk-api-web
 * @version 3.2.1
 *
 * 【模块职责】
 * 定义 UI 运行时能力契约，供插件通过 RuntimeContext 获取并使用。
 *
 * 【能力分类】
 * - 导航能力 (Navigation): 页面跳转、路由管理
 * - 认证能力 (Auth): 用户身份、权限验证
 * - 状态能力 (State): 插件状态管理
 * - 事件能力 (EventBus): 跨插件通信
 * - 配置能力 (Config): 运行时配置读取
 * - HTTP 能力 (Http): 统一 HTTP 请求
 *
 * 【设计原则】
 * - 本模块为纯契约定义层，不包含任何具体实现
 * - 框架无关：不依赖 React/Vue/Angular 等 UI 框架
 * - 插件（Plugin）只需依赖此模块
 * - React 绑定请使用 @brix/runtime-sdk-react
 *
 * 【v3.2.1 重构说明（v3.0.4 架构红线修复）】
 * - 删除全部 963 行内联类型声明，消除双重类型导出问题
 * - 所有类型定义统一从 types/ 目录导出
 * - 上下文定义从 context/ 目录导出
 * - 移除 React 依赖，实现真正的框架无关
 *
 * 【v3.2 重构说明】
 * - 拆分为模块化类型文件（types/）
 * - 移除 React 依赖，React Hooks 迁移到 @brix/runtime-sdk-react
 * - RouteContribution.component 类型改为框架无关的 ComponentType
 */
export * from './types';
export * from './context';
//# sourceMappingURL=index.d.ts.map