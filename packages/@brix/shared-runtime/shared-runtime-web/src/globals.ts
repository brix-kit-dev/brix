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
 * @fileoverview Global Injection for Legacy Code Compatibility
 *
 * This module provides utilities for injecting runtime dependencies into
 * the global window object. This is primarily for backward compatibility
 * with legacy code that expects globals like `window.React`.
 *
 * @module @brix/shared-runtime-web/globals
 *
 * ## Architecture Context (Layer 2B - Shared Runtime)
 *
 * While modern code should import React via ES modules, some scenarios
 * require global availability:
 *
 * 1. Legacy third-party libraries that expect window.React
 * 2. Browser DevTools (React DevTools) integration
 * 3. Runtime debugging and inspection
 * 4. UMD bundles loaded via script tags
 *
 * ## Usage
 *
 * The Host application should call `injectGlobals()` once during startup,
 * before loading any plugins:
 *
 * ```typescript
 * // Host bootstrap.ts
 * import { injectGlobals } from '@brix/shared-runtime-web/globals';
 *
 * // Inject before React render
 * injectGlobals();
 *
 * // Then proceed with normal initialization
 * import { createRoot } from '@brix/shared-runtime-web/react';
 * const root = createRoot(document.getElementById('root')!);
 * root.render(<App />);
 * ```
 *
 * ## Security Considerations
 *
 * Global injection exposes React to the global scope, which:
 * - Is necessary for certain DevTools and debugging scenarios
 * - May be exploited by malicious scripts on the same origin
 * - Should only be done in controlled environments
 *
 * @remarks
 * Plugins should NOT call `injectGlobals()`. Only the Host is responsible
 * for setting up the global environment.
 */

import * as React from 'react';
import * as ReactDOM from 'react-dom';

// =============================================================================
// Type Definitions for Global Window Extension
// =============================================================================

/**
 * Extends the Window interface to include React globals.
 *
 * This declaration merges with the global Window interface,
 * allowing TypeScript to recognize window.React and window.ReactDOM.
 */
declare global {
  interface Window {
    /**
     * React library exposed globally.
     * Set by `injectGlobals()`.
     */
    React: typeof React;

    /**
     * ReactDOM library exposed globally.
     * Set by `injectGlobals()`.
     */
    ReactDOM: typeof ReactDOM;

    /**
     * Debug flag indicating globals have been injected.
     * Used by `checkGlobalsInjected()`.
     */
    __BRIX_RUNTIME_INJECTED__?: boolean;
  }
}

// =============================================================================
// Core Functions
// =============================================================================

/**
 * Inject React and ReactDOM into the global window object.
 *
 * This function should be called ONCE by the Host application during
 * bootstrap, before any plugins are loaded. It ensures:
 *
 * 1. Legacy code expecting window.React works correctly
 * 2. React DevTools can detect and connect to the React instance
 * 3. All code shares the same React instance (no duplicates)
 *
 * @returns void
 *
 * @example
 * ```typescript
 * // In Host's bootstrap.ts or index.ts
 * import { injectGlobals } from '@brix/shared-runtime-web/globals';
 *
 * // Call once at application startup
 * injectGlobals();
 * ```
 *
 * @remarks
 * - Safe to call multiple times (idempotent)
 * - Only effective in browser environments (no-op in SSR)
 * - Logs injection status in development mode
 */
export function injectGlobals(): void {
  // Guard: Only run in browser environment
  if (typeof window === 'undefined') {
    return;
  }

  // Guard: Prevent duplicate injection
  if (window.__BRIX_RUNTIME_INJECTED__) {
    if (process.env.NODE_ENV === 'development') {
      console.debug('[shared-runtime] Globals already injected, skipping.');
    }
    return;
  }

  // Inject React
  window.React = React;

  // Inject ReactDOM
  window.ReactDOM = ReactDOM;

  // Set injection flag
  window.__BRIX_RUNTIME_INJECTED__ = true;

  // Development logging
  if (process.env.NODE_ENV === 'development') {
  }
}

/**
 * Check if global runtime injection has been performed.
 *
 * This function verifies that:
 * 1. We're in a browser environment
 * 2. window.React is defined
 * 3. window.ReactDOM is defined
 * 4. The injection flag is set
 *
 * @returns True if globals have been properly injected
 *
 * @example
 * ```typescript
 * import { checkGlobalsInjected, injectGlobals } from '@brix/shared-runtime-web/globals';
 *
 * if (!checkGlobalsInjected()) {
 *   console.warn('Globals not injected, injecting now...');
 *   injectGlobals();
 * }
 * ```
 *
 * @remarks
 * This is primarily useful for:
 * - Defensive programming in library code
 * - Architecture guard validation
 * - Debugging global state issues
 */
export function checkGlobalsInjected(): boolean {
  return (
    typeof window !== 'undefined' &&
    window.React !== undefined &&
    window.ReactDOM !== undefined &&
    window.__BRIX_RUNTIME_INJECTED__ === true
  );
}

/**
 * Get the injected React instance from window.
 *
 * This provides a safe way to access the global React, with proper
 * type checking and error handling.
 *
 * @returns The React instance if injected, undefined otherwise
 *
 * @example
 * ```typescript
 * const react = getGlobalReact();
 * if (react) {
 *   console.log('React version:', react.version);
 * }
 * ```
 */
export function getGlobalReact(): typeof React | undefined {
  if (typeof window !== 'undefined' && window.React) {
    return window.React;
  }
  return undefined;
}

/**
 * Get the injected ReactDOM instance from window.
 *
 * This provides a safe way to access the global ReactDOM, with proper
 * type checking and error handling.
 *
 * @returns The ReactDOM instance if injected, undefined otherwise
 *
 * @example
 * ```typescript
 * const reactDOM = getGlobalReactDOM();
 * if (reactDOM) {
 *   console.log('ReactDOM available');
 * }
 * ```
 */
export function getGlobalReactDOM(): typeof ReactDOM | undefined {
  if (typeof window !== 'undefined' && window.ReactDOM) {
    return window.ReactDOM;
  }
  return undefined;
}

/**
 * Remove injected globals from window.
 *
 * This is primarily intended for:
 * - Testing scenarios that need clean global state
 * - Cleanup during application unmount (rare)
 *
 * @returns void
 *
 * @example
 * ```typescript
 * // In test teardown
 * import { clearGlobals } from '@brix/shared-runtime-web/globals';
 *
 * afterEach(() => {
 *   clearGlobals();
 * });
 * ```
 *
 * @remarks
 * In production, globals should typically NOT be cleared as it may break
 * dependent code. This is mainly for testing purposes.
 */
export function clearGlobals(): void {
  if (typeof window === 'undefined') {
    return;
  }

  // TypeScript requires explicit any cast for deletion of required properties
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  delete (window as any).React;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  delete (window as any).ReactDOM;
  delete window.__BRIX_RUNTIME_INJECTED__;

  if (process.env.NODE_ENV === 'development') {
    console.debug('[shared-runtime] Globals cleared.');
  }
}
