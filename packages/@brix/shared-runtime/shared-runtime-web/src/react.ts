/**
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @fileoverview React Runtime Re-exports
 *
 * This module serves as the single source of truth for React in the Brix Platform.
 * All modules (Host, Plugins, Adapters) MUST import React from this module or
 * from '@brix/shared-runtime-web/react' to ensure singleton behavior in Module
 * Federation environments.
 *
 * @module @brix/shared-runtime-web/react
 *
 * ## Architecture Context (Layer 2B - Shared Runtime)
 *
 * According to v3.0.7 Architecture Blueprint Constraint 8:
 * - All frontend runtime dependencies MUST be obtained from @brix/shared-runtime-web
 * - Plugins MUST NOT directly declare React in their dependencies
 * - This prevents multiple React instances which would break Hooks
 *
 * ## Usage
 *
 * ```typescript
 * // Correct: Import from shared-runtime-web
 * import { useState, useEffect, React } from '@brix/shared-runtime-web/react';
 *
 * // Incorrect: Direct import (will cause Hooks issues in MF)
 * import { useState } from 'react'; // DO NOT USE
 * ```
 *
 * ## Why Re-export Instead of Direct Import?
 *
 * In Module Federation, each remote (plugin) can potentially bundle its own
 * copy of React if not properly configured. Even with shared configuration,
 * version mismatches can cause runtime errors. By having all code import from
 * this module, we:
 *
 * 1. Guarantee single React instance across Host and all Plugins
 * 2. Centralize version management (RUNTIME_VERSIONS)
 * 3. Enable consistent MF shared configuration via mf-shared-config.ts
 * 4. Prevent "Invalid Hook Call" errors from multiple React copies
 *
 * @see {@link ../mf-shared-config.ts} for Module Federation configuration
 * @see {@link ../versions.ts} for centralized version constants
 */

// =============================================================================
// Core React Exports
// =============================================================================

/**
 * Import React as default for re-export.
 * React uses CommonJS-style 'export =' pattern, so we import as default
 * and re-export individual APIs explicitly.
 */
import React from 'react';
import ReactDOM from 'react-dom';
import { createRoot, hydrateRoot } from 'react-dom/client';

/**
 * Default export for React.
 * Allows usage like: import React from '@brix/shared-runtime-web/react'
 *
 * This is necessary for:
 * - JSX transform in some configurations
 * - Libraries that expect React as default export
 * - Legacy code patterns
 */
export { React };

/**
 * Default export for ReactDOM.
 * Allows usage like: import { ReactDOM } from '@brix/shared-runtime-web/react'
 */
export { ReactDOM };

// =============================================================================
// React Core Hooks - Explicit Re-exports
// =============================================================================

/**
 * Explicit re-export of React hooks and APIs.
 * This approach avoids TypeScript issues with 'export *' on CommonJS modules.
 */
export const {
  // Core Hooks
  useState,
  useEffect,
  useContext,
  useReducer,
  useCallback,
  useMemo,
  useRef,
  useImperativeHandle,
  useLayoutEffect,
  useDebugValue,
  // React 18+ Hooks
  useTransition,
  useDeferredValue,
  useId,
  useSyncExternalStore,
  useInsertionEffect,
  // Component APIs
  createElement,
  cloneElement,
  isValidElement,
  createContext,
  forwardRef,
  memo,
  lazy,
  createRef,
  // Components
  Fragment,
  StrictMode,
  Suspense,
  Profiler,
  // Children utilities
  Children,
  // Version
  version,
} = React;

// =============================================================================
// React DOM Client Exports (React 18+)
// =============================================================================

/**
 * Re-export React 18+ client APIs.
 *
 * Exports:
 * - createRoot: Create a root for concurrent rendering
 * - hydrateRoot: Hydrate server-rendered content with concurrent features
 *
 * These are the recommended APIs for React 18+ applications.
 *
 * @example
 * ```typescript
 * import { createRoot } from '@brix/shared-runtime-web/react';
 *
 * const root = createRoot(document.getElementById('root')!);
 * root.render(<App />);
 * ```
 */
export { createRoot, hydrateRoot };

/**
 * Re-export ReactDOM utilities.
 */
export const {
  createPortal,
  flushSync,
} = ReactDOM;

// =============================================================================
// JSX Runtime Exports
// =============================================================================

/**
 * Re-export JSX runtime for the new JSX transform.
 *
 * The new JSX transform (React 17+) automatically imports these functions
 * during compilation. By re-exporting them, we ensure the build system
 * can resolve them from our singleton React instance.
 *
 * Exports:
 * - jsx: Used for single-child JSX elements
 * - jsxs: Used for multi-child JSX elements
 * - Fragment: Short syntax for fragments (<>...</>)
 *
 * Note: These are typically not imported directly in application code.
 * The build system (Babel/TypeScript) handles this automatically when
 * configured with the new JSX transform.
 */
export { jsx, jsxs, Fragment as JsxFragment } from 'react/jsx-runtime';

// =============================================================================
// Type Re-exports
// =============================================================================

/**
 * Re-export common React types for TypeScript users.
 * These are the most frequently used types in React applications.
 */
export type {
  FC,
  ReactNode,
  ReactElement,
  ComponentType,
  ComponentProps,
  PropsWithChildren,
  PropsWithRef,
  RefObject,
  MutableRefObject,
  Dispatch,
  SetStateAction,
  ChangeEvent,
  FormEvent,
  MouseEvent,
  KeyboardEvent,
  FocusEvent,
  CSSProperties,
  HTMLAttributes,
  ButtonHTMLAttributes,
  InputHTMLAttributes,
  FormHTMLAttributes,
  Context,
  Provider,
  Consumer,
  Ref,
  ForwardedRef,
  LegacyRef,
  RefCallback,
  DependencyList,
  EffectCallback,
  Reducer,
  ReducerState,
  ReducerAction,
  SyntheticEvent,
} from 'react';

/**
 * Re-export ReactDOM types.
 */
export type { Root } from 'react-dom/client';
