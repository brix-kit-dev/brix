/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * @file useViewMode Hook — Unit Tests (Phase 2 / C-4)
 * @description Validates that {@link useViewMode} correctly resolves
 * {@link ViewModeCapability} from {@link RuntimeContext}, exposes initial
 * state, re-renders on `onViewModeChange` events, and forwards `switchTo`
 * calls to the underlying capability.
 *
 * @module @brix-sdk/runtime-sdk-react/test/useViewMode
 * @version 3.3.0
 *
 * [Architectural Constraint]
 * - This test file MUST NOT import from any UI library.
 * - The mock {@link ViewModeCapability} is constructed from raw vi.fn() stubs
 *   and conforms to the Layer 2A contract.
 */

import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';

import {
  type ViewModeCapability,
  type ViewModeChangeListener,
  type ViewModeSwitchResult,
  ViewModeCapabilityType,
  VIEW_MODE_PLATFORM_ADMIN,
  VIEW_MODE_TENANT_ACTOR,
} from '@brix-sdk/runtime-sdk-api-web';

import { RuntimeContextProvider } from '../context';
import { useViewMode } from '../hooks/useViewMode';

// ============================================================================
// Test Fixtures
// ============================================================================

interface MockState {
  capability: ViewModeCapability;
  emit: (event: Parameters<ViewModeChangeListener>[0]) => void;
  switchTo: ReturnType<typeof vi.fn>;
}

function createMockCapability(initial: {
  mode?: 'PLATFORM_ADMIN' | 'TENANT_ACTOR' | 'TENANT_SUBJECT';
  originalSub?: string | null;
  viewingTenantId?: string | null;
}): MockState {
  let listener: ViewModeChangeListener | null = null;
  const switchTo = vi.fn(
    async (): Promise<ViewModeSwitchResult> => ({
      accessToken: 'new-token',
      expiresInSeconds: 3600,
      mode: VIEW_MODE_PLATFORM_ADMIN,
    }),
  );

  const capability: ViewModeCapability = {
    getCurrent: () => initial.mode ?? VIEW_MODE_PLATFORM_ADMIN,
    getOriginalSub: () => initial.originalSub ?? null,
    getViewingTenantId: () => initial.viewingTenantId ?? null,
    switchTo,
    onViewModeChange: (l: ViewModeChangeListener) => {
      listener = l;
      return () => {
        listener = null;
      };
    },
  };

  return {
    capability,
    switchTo,
    emit: (event) => {
      if (listener) listener(event);
    },
  };
}

function createWrapper(capability: ViewModeCapability | undefined) {
  const context = {
    moduleId: 'test-plugin',
    tenantId: 'test-tenant',
    getCapability: <T,>(type: symbol): T | undefined => {
      if (type === ViewModeCapabilityType) {
        return capability as T | undefined;
      }
      return undefined;
    },
  };
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return React.createElement(
      RuntimeContextProvider,
      { value: context as never },
      children,
    );
  };
}

// ============================================================================
// Tests
// ============================================================================

describe('useViewMode', () => {
  it('exposes the initial state from the capability', () => {
    const mock = createMockCapability({
      mode: VIEW_MODE_PLATFORM_ADMIN,
      originalSub: null,
      viewingTenantId: null,
    });

    const { result } = renderHook(() => useViewMode(), {
      wrapper: createWrapper(mock.capability),
    });

    expect(result.current.mode).toBe(VIEW_MODE_PLATFORM_ADMIN);
    expect(result.current.originalSub).toBeNull();
    expect(result.current.viewingTenantId).toBeNull();
    expect(result.current.isViewingAsTenant).toBe(false);
    expect(result.current.capability).toBe(mock.capability);
  });

  it('reflects an in-progress impersonation session', () => {
    const mock = createMockCapability({
      mode: VIEW_MODE_TENANT_ACTOR,
      originalSub: '42',
      viewingTenantId: '100',
    });

    const { result } = renderHook(() => useViewMode(), {
      wrapper: createWrapper(mock.capability),
    });

    expect(result.current.mode).toBe(VIEW_MODE_TENANT_ACTOR);
    expect(result.current.originalSub).toBe('42');
    expect(result.current.viewingTenantId).toBe('100');
    expect(result.current.isViewingAsTenant).toBe(true);
  });

  it('re-renders when the capability emits a view-mode change event', () => {
    const mock = createMockCapability({
      mode: VIEW_MODE_PLATFORM_ADMIN,
      originalSub: null,
      viewingTenantId: null,
    });

    const { result } = renderHook(() => useViewMode(), {
      wrapper: createWrapper(mock.capability),
    });

    expect(result.current.isViewingAsTenant).toBe(false);

    act(() => {
      mock.emit({
        mode: VIEW_MODE_TENANT_ACTOR,
        tenantId: '200',
        originalSub: '7',
      });
    });

    expect(result.current.mode).toBe(VIEW_MODE_TENANT_ACTOR);
    expect(result.current.viewingTenantId).toBe('200');
    expect(result.current.originalSub).toBe('7');
    expect(result.current.isViewingAsTenant).toBe(true);
  });

  it('forwards switchTo() invocations to the capability', async () => {
    const mock = createMockCapability({ mode: VIEW_MODE_PLATFORM_ADMIN });

    const { result } = renderHook(() => useViewMode(), {
      wrapper: createWrapper(mock.capability),
    });

    await act(async () => {
      await result.current.switchTo({
        mode: VIEW_MODE_TENANT_ACTOR,
        tenantId: '100',
      });
    });

    expect(mock.switchTo).toHaveBeenCalledTimes(1);
    expect(mock.switchTo).toHaveBeenCalledWith({
      mode: VIEW_MODE_TENANT_ACTOR,
      tenantId: '100',
    });
  });

  it('throws a descriptive error when ViewModeCapability is not registered', () => {
    // Suppress React error-boundary noise for this expected throw.
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    try {
      expect(() =>
        renderHook(() => useViewMode(), {
          wrapper: createWrapper(undefined),
        }),
      ).toThrow(/ViewModeCapability is not registered/);
    } finally {
      consoleSpy.mockRestore();
    }
  });
});
