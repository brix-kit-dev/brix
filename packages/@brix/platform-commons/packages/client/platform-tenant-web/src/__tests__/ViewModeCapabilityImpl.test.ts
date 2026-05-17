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
 * @file ViewModeCapabilityImpl — Unit Tests (Phase 2 / C-4)
 * @description Validates the config-delegation, input-validation and
 * listener-fan-out behaviour of {@link ViewModeCapabilityImpl}. No DOM is
 * required — these are pure-logic tests.
 *
 * @module @brix-sdk/platform-tenant-web/test/ViewModeCapabilityImpl
 * @version 3.3.0
 */

import { describe, it, expect, vi } from 'vitest';

import {
  type ViewModeCapabilityConfig,
  type ViewModeSwitchResult,
  VIEW_MODE_PLATFORM_ADMIN,
  VIEW_MODE_TENANT_ACTOR,
} from '@brix-sdk/runtime-sdk-api-web';

import { ViewModeCapabilityImpl } from '../ViewModeCapabilityImpl';

// ============================================================================
// Fixtures
// ============================================================================

function createConfig(overrides: Partial<ViewModeCapabilityConfig> = {}): {
  config: ViewModeCapabilityConfig;
  switchTo: ReturnType<typeof vi.fn>;
} {
  const switchTo = vi.fn(
    async (): Promise<ViewModeSwitchResult> => ({
      accessToken: 'tok',
      expiresInSeconds: 3600,
      mode: VIEW_MODE_TENANT_ACTOR,
      tenantId: '100',
      originalSub: '42',
    }),
  );
  const config: ViewModeCapabilityConfig = {
    getCurrent: () => VIEW_MODE_PLATFORM_ADMIN,
    getOriginalSub: () => null,
    getViewingTenantId: () => null,
    switchTo,
    ...overrides,
  };
  return { config, switchTo };
}

// ============================================================================
// Tests
// ============================================================================

describe('ViewModeCapabilityImpl', () => {
  it('throws when constructed without a config', () => {
    // @ts-expect-error — exercising the runtime guard.
    expect(() => new ViewModeCapabilityImpl(undefined)).toThrow(
      /Configuration is required/,
    );
  });

  it('delegates getter methods to the supplied config', () => {
    const { config } = createConfig({
      getCurrent: () => VIEW_MODE_TENANT_ACTOR,
      getOriginalSub: () => '42',
      getViewingTenantId: () => '100',
    });
    const impl = new ViewModeCapabilityImpl(config);

    expect(impl.getCurrent()).toBe(VIEW_MODE_TENANT_ACTOR);
    expect(impl.getOriginalSub()).toBe('42');
    expect(impl.getViewingTenantId()).toBe('100');
  });

  it('switchTo: rejects empty request', async () => {
    const { config } = createConfig();
    const impl = new ViewModeCapabilityImpl(config);

    // @ts-expect-error — exercising the runtime guard.
    await expect(impl.switchTo(undefined)).rejects.toThrow(
      /SwitchRequest is required/,
    );
  });

  it('switchTo: rejects request without mode', async () => {
    const { config } = createConfig();
    const impl = new ViewModeCapabilityImpl(config);

    await expect(
      // @ts-expect-error — exercising the runtime guard.
      impl.switchTo({ tenantId: '100' }),
    ).rejects.toThrow(/mode is required/);
  });

  it('switchTo: rejects non-PLATFORM_ADMIN mode without tenantId', async () => {
    const { config } = createConfig();
    const impl = new ViewModeCapabilityImpl(config);

    await expect(
      impl.switchTo({ mode: VIEW_MODE_TENANT_ACTOR }),
    ).rejects.toThrow(/tenantId is required/);
  });

  it('switchTo: forwards the request to the config and returns its result', async () => {
    const { config, switchTo } = createConfig();
    const impl = new ViewModeCapabilityImpl(config);

    const result = await impl.switchTo({
      mode: VIEW_MODE_TENANT_ACTOR,
      tenantId: '100',
    });

    expect(switchTo).toHaveBeenCalledWith({
      mode: VIEW_MODE_TENANT_ACTOR,
      tenantId: '100',
    });
    expect(result.accessToken).toBe('tok');
    expect(result.tenantId).toBe('100');
  });

  it('switchTo: notifies subscribed listeners with a ViewModeChangeEvent', async () => {
    const { config } = createConfig();
    const impl = new ViewModeCapabilityImpl(config);
    const listener = vi.fn();
    impl.onViewModeChange(listener);

    await impl.switchTo({
      mode: VIEW_MODE_TENANT_ACTOR,
      tenantId: '100',
    });

    expect(listener).toHaveBeenCalledTimes(1);
    expect(listener).toHaveBeenCalledWith({
      mode: VIEW_MODE_TENANT_ACTOR,
      tenantId: '100',
      originalSub: '42',
    });
  });

  it('onViewModeChange: returned unsubscribe stops further notifications', async () => {
    const { config } = createConfig();
    const impl = new ViewModeCapabilityImpl(config);
    const listener = vi.fn();
    const unsubscribe = impl.onViewModeChange(listener);
    unsubscribe();

    await impl.switchTo({
      mode: VIEW_MODE_TENANT_ACTOR,
      tenantId: '100',
    });

    expect(listener).not.toHaveBeenCalled();
  });

  it('listener exceptions do not break the switch flow', async () => {
    const { config } = createConfig();
    const impl = new ViewModeCapabilityImpl(config);
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    impl.onViewModeChange(() => {
      throw new Error('boom');
    });

    await expect(
      impl.switchTo({ mode: VIEW_MODE_TENANT_ACTOR, tenantId: '100' }),
    ).resolves.toBeDefined();

    expect(consoleSpy).toHaveBeenCalled();
    consoleSpy.mockRestore();
  });

  it('destroy: clears listeners and subsequent subscriptions become no-ops', async () => {
    const { config } = createConfig();
    const impl = new ViewModeCapabilityImpl(config);
    const listener = vi.fn();
    impl.onViewModeChange(listener);

    impl.destroy();

    // Subscribing after destroy returns a no-op unsubscribe.
    const noop = impl.onViewModeChange(listener);
    expect(typeof noop).toBe('function');

    await impl.switchTo({
      mode: VIEW_MODE_TENANT_ACTOR,
      tenantId: '100',
    });

    expect(listener).not.toHaveBeenCalled();
  });
});
