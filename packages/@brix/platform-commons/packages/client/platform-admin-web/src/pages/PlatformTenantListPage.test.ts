import { describe, expect, it } from 'vitest';
import {
  formatInstallationQuotaUsage,
  isInstallationQuotaFull,
} from './PlatformTenantListPage';
import type { InstallationQuotaDto } from '../types';

describe('PlatformTenantListPage quota summary', () => {
  it('uses the installation quota snapshot as a global total', () => {
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

    expect(formatInstallationQuotaUsage(quota)).toBe('2 / 3');
    expect(isInstallationQuotaFull(quota)).toBe(false);
  });

  it('marks installation quota as full without multiplying by tenant rows', () => {
    const quota: InstallationQuotaDto = {
      installationId: 'default',
      quota: 3,
      used: 3,
      licenseStatus: 'OPEN_CORE_ACTIVE',
      expiresAt: null,
      canCreateTenant: false,
      refusalReason: 'TENANT_QUOTA_EXCEEDED',
      updatedAt: null,
    };

    expect(formatInstallationQuotaUsage(quota)).toBe('3 / 3');
    expect(isInstallationQuotaFull(quota)).toBe(true);
  });
});