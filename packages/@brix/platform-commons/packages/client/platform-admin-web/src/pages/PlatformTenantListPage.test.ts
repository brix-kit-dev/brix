import { describe, expect, it } from 'vitest';
import {
  canOpenFirstOwnerInvitation,
  canOpenTenantStatusUpdate,
  formatInstallationQuotaUsage,
  isInstallationQuotaFull,
} from './PlatformTenantListPage';
import { getTenantStatusTransitionTargets } from './UpdateTenantStatusDialog';
import {
  PLATFORM_TENANT_STATUS,
  type PlatformTenantStatus,
} from '../constants';
import type { InstallationQuotaDto, PlatformTenantDto } from '../types';

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

describe('PlatformTenantListPage tenant lifecycle actions', () => {
  it('exposes FIRST_OWNER invitation only for pending activation tenants', () => {
    expect(
      canOpenFirstOwnerInvitation(
        tenant(PLATFORM_TENANT_STATUS.PENDING_ACTIVATION),
        true,
      ),
    ).toBe(true);
    expect(
      canOpenFirstOwnerInvitation(tenant(PLATFORM_TENANT_STATUS.ACTIVE), true),
    ).toBe(false);
    expect(
      canOpenFirstOwnerInvitation(
        tenant(PLATFORM_TENANT_STATUS.PENDING_ACTIVATION),
        false,
      ),
    ).toBe(false);
  });

  it('does not expose status updates for pending activation tenants', () => {
    expect(
      canOpenTenantStatusUpdate(
        tenant(PLATFORM_TENANT_STATUS.PENDING_ACTIVATION),
        true,
      ),
    ).toBe(false);
    expect(
      canOpenTenantStatusUpdate(tenant(PLATFORM_TENANT_STATUS.ACTIVE), true),
    ).toBe(true);
    expect(
      canOpenTenantStatusUpdate(tenant(PLATFORM_TENANT_STATUS.SUSPENDED), true),
    ).toBe(true);
    expect(
      canOpenTenantStatusUpdate(tenant(PLATFORM_TENANT_STATUS.TERMINATED), true),
    ).toBe(false);
  });

  it('keeps operator status changes to the active suspended cycle', () => {
    expect(
      getTenantStatusTransitionTargets(PLATFORM_TENANT_STATUS.PENDING_ACTIVATION),
    ).toEqual([]);
    expect(getTenantStatusTransitionTargets(PLATFORM_TENANT_STATUS.ACTIVE)).toEqual([
      PLATFORM_TENANT_STATUS.SUSPENDED,
    ]);
    expect(
      getTenantStatusTransitionTargets(PLATFORM_TENANT_STATUS.SUSPENDED),
    ).toEqual([PLATFORM_TENANT_STATUS.ACTIVE]);
  });
});

function tenant(status: PlatformTenantStatus): PlatformTenantDto {
  return {
    id: 'tenant-42',
    code: 'shinwa-medical',
    name: '信和医疗中心',
    status,
    createdAt: '2026-08-03T00:00:00Z',
    updatedAt: '2026-08-03T00:00:00Z',
    ownerIdentityId: null,
    memberCount: 0,
    quotaUsed: null,
    quotaLimit: null,
    licenseStatus: null,
    defaultLocale: 'zh-CN',
    defaultTimezone: 'Asia/Tokyo',
    defaultTheme: null,
  };
}
