// @vitest-environment jsdom
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
 * @file RequireViewMode — Unit Tests (Phase 2 / C-4)
 * @description Validates that {@link RequireViewMode} renders {@code children}
 * when the active view mode matches one of the {@code allowed} values, and
 * the supplied {@code fallback} otherwise. The {@link useViewMode} hook is
 * mocked at the module boundary so this test exercises only the guard.
 *
 * @module @brix-sdk/platform-tenant-web/test/RequireViewMode
 * @version 3.3.0
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';

import {
  VIEW_MODE_PLATFORM_ADMIN,
  VIEW_MODE_TENANT_ACTOR,
  type ViewMode,
} from '@brix-sdk/runtime-sdk-api-web';

// Mock the hook from runtime-sdk-react so we can drive `mode` per test.
const useViewModeMock = vi.fn();
vi.mock('@brix-sdk/runtime-sdk-react', () => ({
  useViewMode: () => useViewModeMock(),
}));

import { RequireViewMode } from '../components/RequireViewMode';

function setMode(mode: ViewMode) {
  useViewModeMock.mockReturnValue({
    mode,
    originalSub: null,
    viewingTenantId: null,
    isViewingAsTenant: false,
    switchTo: vi.fn(),
    capability: {} as never,
  });
}

beforeEach(() => {
  useViewModeMock.mockReset();
});

afterEach(() => {
  cleanup();
});

describe('RequireViewMode', () => {
  it('renders children when current mode matches a scalar allowed value', () => {
    setMode(VIEW_MODE_PLATFORM_ADMIN);

    render(
      <RequireViewMode allowed={VIEW_MODE_PLATFORM_ADMIN}>
        <span>protected</span>
      </RequireViewMode>,
    );

    expect(screen.getByText('protected')).toBeDefined();
  });

  it('renders children when current mode is in the allowed array', () => {
    setMode(VIEW_MODE_TENANT_ACTOR);

    render(
      <RequireViewMode
        allowed={[VIEW_MODE_PLATFORM_ADMIN, VIEW_MODE_TENANT_ACTOR]}
      >
        <span>protected</span>
      </RequireViewMode>,
    );

    expect(screen.getByText('protected')).toBeDefined();
  });

  it('renders the supplied fallback when current mode is not allowed', () => {
    setMode(VIEW_MODE_TENANT_ACTOR);

    render(
      <RequireViewMode
        allowed={VIEW_MODE_PLATFORM_ADMIN}
        fallback={<span>denied</span>}
      >
        <span>protected</span>
      </RequireViewMode>,
    );

    expect(screen.queryByText('protected')).toBeNull();
    expect(screen.getByText('denied')).toBeDefined();
  });

  it('renders nothing (null fallback) by default when not allowed', () => {
    setMode(VIEW_MODE_TENANT_ACTOR);

    const { container } = render(
      <RequireViewMode allowed={VIEW_MODE_PLATFORM_ADMIN}>
        <span>protected</span>
      </RequireViewMode>,
    );

    expect(container.textContent).toBe('');
  });
});
