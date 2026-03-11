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
 * @fileoverview Unit tests for global injection utilities.
 *
 * Tests verify:
 * 1. injectGlobals correctly sets window.React and window.ReactDOM
 * 2. checkGlobalsInjected returns correct state
 * 3. clearGlobals properly cleans up
 * 4. Functions are idempotent and safe
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import {
  injectGlobals,
  checkGlobalsInjected,
  getGlobalReact,
  getGlobalReactDOM,
  clearGlobals,
} from './globals';

describe('globals', () => {
  // Clean up after each test to ensure isolation
  afterEach(() => {
    clearGlobals();
  });

  describe('injectGlobals', () => {
    it('should inject React into window', () => {
      expect(window.React).toBeUndefined();

      injectGlobals();

      expect(window.React).toBeDefined();
      expect(typeof window.React.createElement).toBe('function');
      expect(typeof window.React.useState).toBe('function');
    });

    it('should inject ReactDOM into window', () => {
      expect(window.ReactDOM).toBeUndefined();

      injectGlobals();

      expect(window.ReactDOM).toBeDefined();
      expect(typeof window.ReactDOM.createPortal).toBe('function');
    });

    it('should set injection flag', () => {
      expect(window.__BRIX_RUNTIME_INJECTED__).toBeUndefined();

      injectGlobals();

      expect(window.__BRIX_RUNTIME_INJECTED__).toBe(true);
    });

    it('should be idempotent (safe to call multiple times)', () => {
      injectGlobals();
      const firstReact = window.React;

      injectGlobals();
      const secondReact = window.React;

      // Should be the same reference
      expect(firstReact).toBe(secondReact);
    });
  });

  describe('checkGlobalsInjected', () => {
    it('should return false before injection', () => {
      expect(checkGlobalsInjected()).toBe(false);
    });

    it('should return true after injection', () => {
      injectGlobals();

      expect(checkGlobalsInjected()).toBe(true);
    });

    it('should return false after clear', () => {
      injectGlobals();
      expect(checkGlobalsInjected()).toBe(true);

      clearGlobals();
      expect(checkGlobalsInjected()).toBe(false);
    });
  });

  describe('getGlobalReact', () => {
    it('should return undefined before injection', () => {
      expect(getGlobalReact()).toBeUndefined();
    });

    it('should return React after injection', () => {
      injectGlobals();

      const react = getGlobalReact();
      expect(react).toBeDefined();
      expect(typeof react?.createElement).toBe('function');
    });
  });

  describe('getGlobalReactDOM', () => {
    it('should return undefined before injection', () => {
      expect(getGlobalReactDOM()).toBeUndefined();
    });

    it('should return ReactDOM after injection', () => {
      injectGlobals();

      const reactDOM = getGlobalReactDOM();
      expect(reactDOM).toBeDefined();
      expect(typeof reactDOM?.createPortal).toBe('function');
    });
  });

  describe('clearGlobals', () => {
    it('should remove React from window', () => {
      injectGlobals();
      expect(window.React).toBeDefined();

      clearGlobals();
      expect(window.React).toBeUndefined();
    });

    it('should remove ReactDOM from window', () => {
      injectGlobals();
      expect(window.ReactDOM).toBeDefined();

      clearGlobals();
      expect(window.ReactDOM).toBeUndefined();
    });

    it('should remove injection flag', () => {
      injectGlobals();
      expect(window.__BRIX_RUNTIME_INJECTED__).toBe(true);

      clearGlobals();
      expect(window.__BRIX_RUNTIME_INJECTED__).toBeUndefined();
    });

    it('should be safe to call when nothing is injected', () => {
      // Should not throw
      expect(() => clearGlobals()).not.toThrow();
    });
  });
});
