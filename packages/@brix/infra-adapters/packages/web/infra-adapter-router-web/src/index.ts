/**
 * @file infra-adapter-router-web module entry
 * @description Brix UI Router Adapter - react-router based routing implementation
 * @module @brix/infra-adapter-router-web
 * @version 3.1.0
 * 
 * [Module Description]
 * This module is the router adapter layer in the v3.0 Runtime Shell architecture.
 * It encapsulates react-router-dom to provide routing capabilities for the Host layer.
 * 
 * [Architectural Position]
 * - This module is an internal dependency of the Host layer
 * - Plugins should not use this module directly
 * - Plugins navigate through NavigationCapability
 * 
 * [v3.0 Architectural Constraints (Red Lines)]
 * ❌ Plugins MUST NOT use react-router directly
 * ❌ Plugins MUST NOT register routes
 * ❌ Plugins MUST NOT navigate using URL paths
 * ✅ Plugins request navigation using PageId
 * ✅ Host decides whether to allow navigation
 * 
 * [v3.1 Architecture Compliance Updates]
 * - Removed direct re-export of third-party types (Router, Location, RouterProvider)
 * - All react-router types are now encapsulated within adapter-owned abstractions
 * - Third-party library isolation principle: Public API contains zero third-party types
 * 
 * 【架构合规更新】
 * - 移除第三方类型的直接重新导出（Router, Location, RouterProvider）
 * - 所有 react-router 类型现在封装在 adapter 自有抽象中
 * - 第三方库隔离原则：公共 API 不包含任何第三方类型
 * 
 * [Usage] (Host layer only)
 * ```typescript
 * import { ReactRouterAdapter, BrixRouterProvider } from '@brix/infra-adapter-router-web';
 * 
 * const adapter = new ReactRouterAdapter({ basename: '/app' });
 * adapter.registerPages(pages);
 * const router = adapter.getRouter();
 * 
 * // In JSX (use BrixRouterProvider instead of react-router-dom's RouterProvider)
 * <BrixRouterProvider router={router} />
 * ```
 */

export { 
  ReactRouterAdapter,
  type PageConfig,
  type NavigateOptions,
  type RouterType,
  type ReactRouterAdapterOptions,
} from './ReactRouterAdapter';

// ============================================================
// Encapsulated Router Provider (wraps react-router-dom's RouterProvider)
// ============================================================

import { RouterProvider } from 'react-router-dom';
import type { ReactNode } from 'react';
import React from 'react';

/**
 * Adapter-owned Router instance type.
 * 
 * This is an opaque type that hides the react-router-dom internal types.
 * The actual router instance is created by ReactRouterAdapter.getRouter().
 * 
 * 【架构合规】
 * 此类型是适配器自有的不透明类型，隐藏了 react-router-dom 内部类型。
 * 实际 router 实例由 ReactRouterAdapter.getRouter() 创建。
 */
export type BrixRouter = object;

/**
 * Props for BrixRouterProvider component.
 * 
 * This type is adapter-owned and does not expose react-router-dom types.
 * 
 * 【架构合规】
 * 此类型为适配器自有，不暴露 react-router-dom 类型。
 */
export interface BrixRouterProviderProps {
  /**
   * Router instance created by ReactRouterAdapter.getRouter()
   */
  readonly router: BrixRouter;
  
  /**
   * Fallback UI to show during hydration (optional)
   */
  readonly fallbackElement?: ReactNode;
  
  /**
   * Future flags for upcoming react-router features (optional)
   */
  readonly future?: {
    v7_startTransition?: boolean;
  };
}

/**
 * Route change listener callback type.
 * 
 * Uses adapter-owned NavigationLocation type instead of react-router's Location.
 * 
 * 【架构合规】
 * 使用适配器自有的 NavigationLocation 类型，而非 react-router 的 Location。
 */
export type RouteChangeListener = (
  location: NavigationLocation, 
  action: 'PUSH' | 'POP' | 'REPLACE'
) => void;

/**
 * Brix Router Provider Component.
 * 
 * Encapsulates react-router-dom's RouterProvider for architectural compliance.
 * Host layer should use this instead of directly importing from react-router-dom.
 * 
 * This component provides type isolation by using adapter-owned types (BrixRouterProviderProps)
 * instead of exposing react-router-dom internal types.
 * 
 * 【架构说明】
 * 此组件封装了 react-router-dom 的 RouterProvider，确保架构合规性。
 * Host 层应使用此组件而不是直接从 react-router-dom 导入。
 * 通过使用适配器自有类型 (BrixRouterProviderProps) 而非暴露 react-router-dom 内部类型来实现类型隔离。
 * 
 * @example
 * ```typescript
 * import { ReactRouterAdapter, BrixRouterProvider } from '@brix/infra-adapter-router-web';
 * 
 * const adapter = new ReactRouterAdapter();
 * adapter.registerPages(pages);
 * 
 * function App() {
 *   const router = adapter.getRouter();
 *   if (!router) return null;
 *   return <BrixRouterProvider router={router} />;
 * }
 * ```
 */
export function BrixRouterProvider(props: BrixRouterProviderProps): React.JSX.Element {
  // Type cast is safe because BrixRouter is created by ReactRouterAdapter.getRouter()
  // which returns the actual react-router Router instance
  return React.createElement(RouterProvider, {
    router: props.router as Parameters<typeof RouterProvider>[0]['router'],
    fallbackElement: props.fallbackElement,
    future: props.future,
  });
}

// ============================================================
// Encapsulated Location Type (adapter-owned abstraction)
// ============================================================

/**
 * Navigation Location.
 * 
 * Adapter-owned location type that encapsulates react-router's Location.
 * Provides a stable, framework-agnostic interface for the Host layer.
 * 
 * 【架构说明】
 * 适配器自有的 Location 类型，封装 react-router 的 Location。
 * 为 Host 层提供稳定的、框架无关的接口。
 */
export interface NavigationLocation {
  /** Current pathname */
  readonly pathname: string;
  /** Search query string */
  readonly search: string;
  /** Hash fragment */
  readonly hash: string;
  /** State object */
  readonly state: unknown;
  /** Location key */
  readonly key: string;
}

// ========== Version Info ==========
export const VERSION = '3.1.0';
