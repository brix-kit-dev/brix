/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useI18n, useTheme } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import {
  AdminPageShell,
  PageHeader,
  SummaryGrid,
  StatusBadge,
  TriState,
  useUIStrict,
} from '../internal/ui-kit';
import { useInstallationQuota } from '../hooks/useInstallationQuota';
import { I18N_KEYS, I18N_NAMESPACE, makeT } from '../i18n';

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleString() : '—';
}

export function LicenseQuotaPage(): JSX.Element {
  const { Button } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);
  const quota = useInstallationQuota();

  return (
    <AdminPageShell maxWidth={1120}>
      <PageHeader
        title={tt(I18N_KEYS.license.title)}
        subtitle={tt(I18N_KEYS.license.subtitle)}
        actions={
          <Button
            variant="secondary"
            onClick={() => quota.refresh()}
            data-testid="license-quota-refresh"
          >
            {tt(I18N_KEYS.common.refresh)}
          </Button>
        }
      />

      <TriState
        loading={quota.loading}
        error={quota.error}
        data={quota.data}
        loadingNode={tt(I18N_KEYS.common.loading)}
        emptyNode={tt(I18N_KEYS.common.empty)}
      >
        {(snapshot) => {
          const ratio = snapshot.quota > 0 ? snapshot.used / snapshot.quota : 0;
          const percent = Math.min(100, Math.round(ratio * 100));
          return (
            <div style={{ display: 'grid', gap: t.space.md }}>
              <SummaryGrid
                items={[
                  {
                    label: tt(I18N_KEYS.license.installationId),
                    value: snapshot.installationId,
                    tone: 'brand',
                  },
                  {
                    label: tt(I18N_KEYS.license.quotaUsage),
                    value: `${snapshot.used} / ${snapshot.quota}`,
                    helper: `${percent}%`,
                    tone: snapshot.canCreateTenant ? 'success' : 'warning',
                  },
                  {
                    label: tt(I18N_KEYS.license.licenseStatus),
                    value: snapshot.licenseStatus,
                    tone: 'info',
                  },
                  {
                    label: tt(I18N_KEYS.license.expiresAt),
                    value: formatTime(snapshot.expiresAt),
                    tone: 'neutral',
                  },
                ]}
              />

              <section
                aria-label={tt(I18N_KEYS.license.admissionState)}
                style={{
                  display: 'grid',
                  gap: t.space.md,
                  padding: t.space.lg,
                  borderRadius: t.shape.lg,
                  border: `1px solid ${t.colors.border.default}`,
                  background: t.colors.surface.elevated,
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    gap: t.space.sm,
                    flexWrap: 'wrap',
                    alignItems: 'center',
                  }}
                >
                  <div style={{ display: 'grid', gap: t.space.xs }}>
                    <h2
                      style={{
                        margin: 0,
                        color: t.colors.text.primary,
                        fontSize: t.typography.titleSmall.fontSize,
                        lineHeight: t.typography.titleSmall.lineHeight,
                      }}
                    >
                      {tt(I18N_KEYS.license.admissionState)}
                    </h2>
                    <span style={{ color: t.colors.text.secondary }}>
                      {tt(I18N_KEYS.license.updatedAt)}: {formatTime(snapshot.updatedAt)}
                    </span>
                  </div>
                  {snapshot.canCreateTenant ? (
                    <StatusBadge kind="success">
                      {tt(I18N_KEYS.license.canCreate)}
                    </StatusBadge>
                  ) : (
                    <StatusBadge kind="warning">
                      {snapshot.refusalReason ?? tt(I18N_KEYS.license.cannotCreate)}
                    </StatusBadge>
                  )}
                </div>

                <div
                  aria-hidden="true"
                  style={{
                    height: 10,
                    borderRadius: t.shape.full,
                    overflow: 'hidden',
                    background: t.colors.surface.page,
                    border: `1px solid ${t.colors.border.default}`,
                  }}
                >
                  <div
                    style={{
                      width: `${percent}%`,
                      height: '100%',
                      background: snapshot.canCreateTenant
                        ? t.colors.status.success
                        : t.colors.status.warning,
                    }}
                  />
                </div>
              </section>
            </div>
          );
        }}
      </TriState>
    </AdminPageShell>
  );
}