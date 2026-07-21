/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file PlatformTenantListPage — paginated tenant view + status mutation entry.
 *
 * Status transitions go through {@link UpdateTenantStatusDialog} which
 * enforces (client-side) the same legal targets the backend StatusMachine
 * accepts (`ACTIVE` / `SUSPENDED`).
 */

import { useState } from 'react';
import { useAuth, useI18n, useTheme } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import {
  useUIStrict,
  AdminPageShell,
  PageHeader,
  SummaryGrid,
  ToolbarPanel,
  TriState,
  DataTable,
  StatusBadge,
  IconActionButton,
} from '../internal/ui-kit';
import { useInstallationQuota } from '../hooks/useInstallationQuota';
import { usePlatformTenantList } from '../hooks/usePlatformTenantList';
import { UpdateTenantStatusDialog } from './UpdateTenantStatusDialog';
import { CreateTenantDialog } from './CreateTenantDialog';
import {
  PLATFORM_ADMIN_PERMISSIONS,
  PLATFORM_TENANT_STATUS,
  type PlatformTenantStatus,
} from '../constants';
import { I18N_KEYS, I18N_NAMESPACE, makeT } from '../i18n';
import type { InstallationQuotaDto, PlatformTenantDto } from '../types';

const STATUS_FILTER_OPTIONS = [
  { value: '', label: '— Any —' },
  ...Object.values(PLATFORM_TENANT_STATUS).map((v) => ({
    value: v,
    label: v,
  })),
];

function badgeKind(status: PlatformTenantStatus) {
  switch (status) {
    case PLATFORM_TENANT_STATUS.ACTIVE:
      return 'success' as const;
    case PLATFORM_TENANT_STATUS.SUSPENDED:
      return 'warning' as const;
    case PLATFORM_TENANT_STATUS.TERMINATED:
      return 'error' as const;
    default:
      return 'neutral' as const;
  }
}

function formatNullable(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—';
  return String(value);
}

function formatQuota(tenant: PlatformTenantDto): string {
  if (tenant.quotaUsed === null && tenant.quotaLimit === null) return '—';
  return `${formatNullable(tenant.quotaUsed)} / ${formatNullable(tenant.quotaLimit)}`;
}

export function formatInstallationQuotaUsage(
  quota: InstallationQuotaDto | null | undefined,
): string {
  return quota && quota.quota > 0 ? `${quota.used} / ${quota.quota}` : '—';
}

export function isInstallationQuotaFull(
  quota: InstallationQuotaDto | null | undefined,
): boolean {
  return Boolean(quota && quota.quota > 0 && quota.used >= quota.quota);
}

export function PlatformTenantListPage(): JSX.Element {
  const { Button, Input, Select } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);
  const { hasPermission } = useAuth();

  const list = usePlatformTenantList();
  const installationQuota = useInstallationQuota();
  const [editing, setEditing] = useState<PlatformTenantDto | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [searchText, setSearchText] = useState('');

  const canUpdate = hasPermission(
    PLATFORM_ADMIN_PERMISSIONS.TENANT_UPDATE_STATUS,
  );
  const canCreate = hasPermission(PLATFORM_ADMIN_PERMISSIONS.TENANT_CREATE);
  const tenantsOnPage = list.data?.content ?? [];
  const activeCount = tenantsOnPage.filter(
    (tenant) => tenant.status === PLATFORM_TENANT_STATUS.ACTIVE,
  ).length;
  const suspendedCount = tenantsOnPage.filter(
    (tenant) => tenant.status === PLATFORM_TENANT_STATUS.SUSPENDED,
  ).length;
  const memberCount = tenantsOnPage.reduce(
    (total, tenant) => total + tenant.memberCount,
    0,
  );
  const quotaUsage = formatInstallationQuotaUsage(installationQuota.data);
  const quotaFull = isInstallationQuotaFull(installationQuota.data);
  const licenseCount = tenantsOnPage.filter(
    (tenant) => tenant.licenseStatus,
  ).length;

  function applySearch() {
    list.setQuery({ ...list.query, q: searchText.trim() || undefined, page: 0 });
  }

  return (
    <AdminPageShell>
      <PageHeader
        title={tt(I18N_KEYS.tenant.listTitle)}
        subtitle={tt(I18N_KEYS.tenant.listSubtitle)}
        actions={
          <div style={{ display: 'flex', gap: t.space.xs }}>
            {canCreate && (
              <Button
                variant="primary"
                onClick={() => setCreateOpen(true)}
                data-testid="platform-tenant-create"
              >
                {tt(I18N_KEYS.tenant.createBtn)}
              </Button>
            )}
            <Button
              variant="secondary"
              onClick={() => list.refresh()}
              data-testid="platform-tenant-refresh"
            >
              {tt(I18N_KEYS.common.refresh)}
            </Button>
          </div>
        }
      />

      <SummaryGrid
        items={[
          {
            label: tt(I18N_KEYS.tenant.summaryTotal),
            value: list.data?.totalElements ?? '—',
            tone: 'brand',
          },
          {
            label: tt(I18N_KEYS.tenant.summaryActive),
            value: activeCount,
            tone: 'success',
          },
          {
            label: tt(I18N_KEYS.tenant.summarySuspended),
            value: suspendedCount,
            tone: suspendedCount > 0 ? 'warning' : 'neutral',
          },
          {
            label: tt(I18N_KEYS.tenant.summaryMembers),
            value: memberCount,
            tone: 'info',
          },
          {
            label: '配额使用',
            value: quotaUsage,
            tone: quotaFull ? 'warning' : 'neutral',
          },
          {
            label: 'License 状态',
            value: licenseCount > 0 ? licenseCount : '—',
            tone: licenseCount > 0 ? 'info' : 'neutral',
          },
        ]}
      />

      <ToolbarPanel>
        <div style={{ flex: '1 1 280px' }}>
          <Input
            type="search"
            label={tt(I18N_KEYS.tenant.searchLabel)}
            placeholder={tt(I18N_KEYS.tenant.searchPlaceholder)}
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            fullWidth
            data-testid="platform-tenant-search"
          />
        </div>
        <div style={{ minWidth: 220 }}>
          <Select
            label={tt(I18N_KEYS.tenant.filterStatus)}
            options={STATUS_FILTER_OPTIONS}
            value={list.query.status ?? ''}
            onChange={(v) =>
              list.setQuery({
                ...list.query,
                status: ((v as string) || undefined) as
                  | PlatformTenantStatus
                  | undefined,
                page: 0,
              })
            }
            clearable
            fullWidth
            data-testid="platform-tenant-filter-status"
          />
        </div>
        <Button
          variant="secondary"
          onClick={applySearch}
          data-testid="platform-tenant-search-apply"
        >
          {tt(I18N_KEYS.tenant.applyFilters)}
        </Button>
      </ToolbarPanel>

      <TriState
        loading={list.loading}
        error={list.error}
        data={list.data}
        isEmpty={(d) => d.content.length === 0}
        loadingNode={tt(I18N_KEYS.common.loading)}
        emptyNode={tt(I18N_KEYS.common.empty)}
      >
        {(page) => (
          <DataTable
            rowKey={(r) => r.id}
            rows={page.content}
            columns={[
              {
                key: 'code',
                header: tt(I18N_KEYS.tenant.colCode),
                render: (r) => r.code,
              },
              {
                key: 'name',
                header: tt(I18N_KEYS.tenant.colName),
                render: (r) => r.name,
              },
              {
                key: 'status',
                header: tt(I18N_KEYS.tenant.colStatus),
                render: (r) => (
                  <StatusBadge kind={badgeKind(r.status)}>{r.status}</StatusBadge>
                ),
              },
              {
                key: 'members',
                header: tt(I18N_KEYS.tenant.colMembers),
                render: (r) => r.memberCount,
              },
              {
                key: 'quota',
                header: '配额',
                render: (r) => formatQuota(r),
              },
              {
                key: 'license',
                header: 'License',
                render: (r) => formatNullable(r.licenseStatus),
              },
              {
                key: 'defaults',
                header: '默认配置',
                render: (r) => (
                  <div style={{ display: 'grid', gap: 2, minWidth: 160 }}>
                    <span>Locale: {formatNullable(r.defaultLocale)}</span>
                    <span>Timezone: {formatNullable(r.defaultTimezone)}</span>
                    <span>Theme: {formatNullable(r.defaultTheme)}</span>
                  </div>
                ),
              },
              {
                key: 'createdAt',
                header: tt(I18N_KEYS.tenant.colCreatedAt),
                render: (r) => new Date(r.createdAt).toLocaleString(),
              },
              {
                key: 'actions',
                header: tt(I18N_KEYS.common.actions),
                render: (r) =>
                  canUpdate ? (
                    <IconActionButton
                      icon="edit"
                      label={tt(I18N_KEYS.tenant.actionUpdateStatus)}
                      onClick={() => setEditing(r)}
                      data-testid="platform-tenant-row-update-status"
                    />
                  ) : (
                    '—'
                  ),
                align: 'center',
                width: '72px',
              },
            ]}
          />
        )}
      </TriState>

      {editing ? (
        <UpdateTenantStatusDialog
          open
          tenant={editing}
          onClose={() => setEditing(null)}
          onUpdated={() => {
            setEditing(null);
            void list.refresh();
          }}
        />
      ) : null}

      <CreateTenantDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={() => {
          setCreateOpen(false);
          void list.refresh();
        }}
      />
    </AdminPageShell>
  );
}
