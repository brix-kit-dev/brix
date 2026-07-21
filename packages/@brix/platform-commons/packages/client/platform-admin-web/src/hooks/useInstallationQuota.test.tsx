/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/* @vitest-environment jsdom */

import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useInstallationQuota } from './useInstallationQuota';
import type { InstallationQuotaDto } from '../types';

const mocks = vi.hoisted(() => ({
  getInstallationQuota: vi.fn<[], Promise<InstallationQuotaDto>>(),
  repositories: null as null | {
    license: {
      getInstallationQuota: () => Promise<InstallationQuotaDto>;
    };
  },
}));

vi.mock('./useRepositories', () => ({
  useRepositories: () => {
    mocks.repositories ??= {
      license: {
        getInstallationQuota: mocks.getInstallationQuota,
      },
    };
    return mocks.repositories;
  },
}));

const quota: InstallationQuotaDto = {
  installationId: 'default',
  quota: 3,
  used: 2,
  licenseStatus: 'OPEN_CORE_ACTIVE',
  expiresAt: null,
  canCreateTenant: true,
  refusalReason: null,
  updatedAt: null,
};

describe('useInstallationQuota', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('loads the installation quota once on mount', async () => {
    mocks.getInstallationQuota.mockResolvedValue(quota);

    const { result } = renderHook(() => useInstallationQuota());

    await waitFor(() => expect(result.current.data).toEqual(quota));
    await new Promise((resolve) => setTimeout(resolve, 25));

    expect(mocks.getInstallationQuota).toHaveBeenCalledTimes(1);
  });
});