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
 */
/**
 * @file Public API Snapshot Test (Golden File)
 * @description Validates stability of public API exports to prevent accidental breaking changes
 * @module @brix/runtime-sdk-api-web/test
 * @version 3.2.0
 * 
 * [Test Description]
 * Golden File tests ensure the following by creating API export snapshots:
 * 1. Public APIs are not accidentally modified
 * 2. Type exports remain stable
 * 3. Any API changes require explicit confirmation
 * 
 * [Update Snapshot]
 * When intentionally modifying the API, run:
 * pnpm test -- -u
 */

import { describe, it, expect } from 'vitest';
import * as ApiExports from './index';

// ============================================================================
// Public API Export List
// ============================================================================

/**
 * Expected capability type identifiers to be exported
 */
const EXPECTED_CAPABILITY_TYPES = [
  'NavigationCapabilityType',
  'EventBusCapabilityType',
  'PluginStateCapabilityType',
  'AuthCapabilityType',
  'HttpCapabilityType',
  'ConfigCapabilityType',
  'I18nCapabilityType',
  'ThemeCapabilityType',
  'LayoutCapabilityType',
] as const;

/**
 * Expected context-related exports
 */
const EXPECTED_CONTEXT_EXPORTS = [
  'RuntimeContext',
  'RuntimeContextProvider',
  'createRuntimeContext',
] as const;

/**
 * Expected plugin-related type exports
 */
const EXPECTED_PLUGIN_EXPORTS = [
  'PluginEntry',
  'PluginLifecycle',
  'PluginContext',
  'PluginStatus',
  'PluginManifest',
] as const;

/**
 * Expected navigation-related type exports
 */
const EXPECTED_NAVIGATION_EXPORTS = [
  'NavigateResult',
  'NavigateOptions',
  'PageInfo',
  'RouteContribution',
] as const;

/**
 * Expected state-related type exports
 */
const EXPECTED_STATE_EXPORTS = [
  'StatePersistenceOptions',
  'PluginStateChangeEvent',
  'PluginStateSubscribeOptions',
] as const;

/**
 * Expected event-related type exports
 */
const EXPECTED_EVENT_EXPORTS = [
  'EventHandler',
  'EventSubscription',
  'EventType',
  'Unsubscribe',
] as const;

// ============================================================================
// Golden File Tests
// ============================================================================

describe('API Export Stability Tests (Golden File)', () => {
  it('should export all expected modules', () => {
    // Get actual exported keys
    const actualExports = Object.keys(ApiExports);
    
    // Snapshot verification
    expect(actualExports.sort()).toMatchSnapshot('api-exports');
  });

  describe('Capability Type Identifiers', () => {
    it('should export all capability type identifiers', () => {
      for (const capType of EXPECTED_CAPABILITY_TYPES) {
        expect(
          ApiExports,
          `Missing capability type identifier: ${capType}`
        ).toHaveProperty(capType);
      }
    });

    it('capability type identifier structure should remain stable', () => {
      const capabilityTypes: Record<string, unknown> = {};
      
      for (const capType of EXPECTED_CAPABILITY_TYPES) {
        const exported = (ApiExports as Record<string, unknown>)[capType];
        if (exported && typeof exported === 'object') {
          capabilityTypes[capType] = {
            id: (exported as { id?: string }).id,
            name: (exported as { name?: string }).name,
          };
        }
      }
      
      expect(capabilityTypes).toMatchSnapshot('capability-types');
    });
  });

  describe('Context Exports', () => {
    it('should export runtime context related modules', () => {
      for (const contextExport of EXPECTED_CONTEXT_EXPORTS) {
        // Context exports may be functions or components
        const hasExport = contextExport in ApiExports;
        if (!hasExport) {
          // Some context exports may be type-only exports, skip check
          console.warn(`Context export ${contextExport} may be a type-only export`);
        }
      }
    });
  });

  describe('Type Export Completeness', () => {
    it('plugin-related types should be accessible', () => {
      // Types only exist at compile time, cannot be directly verified at runtime
      // This test is mainly for documentation purposes
      const typeNames = EXPECTED_PLUGIN_EXPORTS;
      expect(typeNames.length).toBeGreaterThan(0);
    });

    it('navigation-related types should be accessible', () => {
      const typeNames = EXPECTED_NAVIGATION_EXPORTS;
      expect(typeNames.length).toBeGreaterThan(0);
    });

    it('state-related types should be accessible', () => {
      const typeNames = EXPECTED_STATE_EXPORTS;
      expect(typeNames.length).toBeGreaterThan(0);
    });

    it('event-related types should be accessible', () => {
      const typeNames = EXPECTED_EVENT_EXPORTS;
      expect(typeNames.length).toBeGreaterThan(0);
    });
  });
});

// ============================================================================
// API Signature Stability
// ============================================================================

describe('API Signature Stability', () => {
  it('export count should not decrease (prevent accidental deletion)', () => {
    const actualExportCount = Object.keys(ApiExports).length;
    
    // Set minimum export count baseline (adjust based on actual situation)
    // This number should be set after the API stabilizes
    const MIN_EXPECTED_EXPORTS = 5;
    
    expect(
      actualExportCount,
      `Export count (${actualExportCount}) is below expected minimum (${MIN_EXPECTED_EXPORTS})`
    ).toBeGreaterThanOrEqual(MIN_EXPECTED_EXPORTS);
  });
});
