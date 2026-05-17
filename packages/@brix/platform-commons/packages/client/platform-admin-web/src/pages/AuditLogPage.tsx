/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file AuditLogPage — read-only filterable view of the platform audit log.
 *
 * SSOT §10 requires an `actor / action / target / result / time` skeleton;
 * this page surfaces those columns plus IP and reason for forensic flow.
 */

import { useI18n } from '@brix-sdk/runtime-sdk-react';
import {
  useUIStrict,
  AdminPageShell,
  PageHeader,
  SummaryGrid,
  ToolbarPanel,
  TriState,
  DataTable,
  StatusBadge,
} from '../internal/ui-kit';
import { useAuditLog } from '../hooks/useAuditLog';
import { I18N_KEYS, I18N_NAMESPACE, makeT } from '../i18n';
import { PLATFORM_AUDIT_ACTIONS } from '../constants';

const ACTION_OPTIONS = [
  { value: '', label: '— Any —' },
  ...Object.values(PLATFORM_AUDIT_ACTIONS).map((v) => ({ value: v, label: v })),
];

const RESULT_OPTIONS = [
  { value: '', label: '— Any —' },
  { value: 'SUCCESS', label: 'SUCCESS' },
  { value: 'FAILURE', label: 'FAILURE' },
];

export function AuditLogPage(): JSX.Element {
  const { Button, Select } = useUIStrict();
  const tt = makeT(useI18n(I18N_NAMESPACE).t);

  const audit = useAuditLog();
  const logsOnPage = audit.data?.content ?? [];
  const successCount = logsOnPage.filter((entry) => entry.result === 'SUCCESS').length;
  const failureCount = logsOnPage.filter((entry) => entry.result === 'FAILURE').length;
  const filtered = Boolean(audit.query.action || audit.query.result);

  return (
    <AdminPageShell maxWidth={1240}>
      <PageHeader
        title={tt(I18N_KEYS.audit.title)}
        subtitle={tt(I18N_KEYS.audit.subtitle)}
        actions={
          <Button
            variant="secondary"
            onClick={() => audit.refresh()}
            data-testid="audit-refresh"
          >
            {tt(I18N_KEYS.common.refresh)}
          </Button>
        }
      />

      <SummaryGrid
        items={[
          {
            label: tt(I18N_KEYS.audit.summaryTotal),
            value: audit.data?.totalElements ?? '—',
            tone: 'brand',
          },
          {
            label: tt(I18N_KEYS.audit.summarySuccess),
            value: successCount,
            tone: 'success',
          },
          {
            label: tt(I18N_KEYS.audit.summaryFailure),
            value: failureCount,
            tone: failureCount > 0 ? 'error' : 'neutral',
          },
          {
            label: tt(I18N_KEYS.audit.summaryFiltered),
            value: filtered ? tt(I18N_KEYS.common.apply) : '—',
            tone: filtered ? 'info' : 'neutral',
          },
        ]}
      />

      <ToolbarPanel>
        <div style={{ minWidth: 240 }}>
          <Select
            label={tt(I18N_KEYS.audit.filterAction)}
            options={ACTION_OPTIONS}
            value={audit.query.action ?? ''}
            onChange={(v) =>
              audit.setQuery({
                ...audit.query,
                action: (v as string) || undefined,
                page: 0,
              })
            }
            clearable
            fullWidth
            data-testid="audit-filter-action"
          />
        </div>
        <div style={{ minWidth: 200 }}>
          <Select
            label={tt(I18N_KEYS.audit.filterResult)}
            options={RESULT_OPTIONS}
            value={audit.query.result ?? ''}
            onChange={(v) =>
              audit.setQuery({
                ...audit.query,
                result: ((v as string) || undefined) as
                  | 'SUCCESS'
                  | 'FAILURE'
                  | undefined,
                page: 0,
              })
            }
            clearable
            fullWidth
            data-testid="audit-filter-result"
          />
        </div>
      </ToolbarPanel>

      <TriState
        loading={audit.loading}
        error={audit.error}
        data={audit.data}
        isEmpty={(d) => d.content.length === 0}
        loadingNode={tt(I18N_KEYS.common.loading)}
        emptyNode={tt(I18N_KEYS.common.empty)}
      >
        {(page) => (
          <DataTable
            dense
            rowKey={(r) => r.id}
            rows={page.content}
            columns={[
              {
                key: 'time',
                header: tt(I18N_KEYS.audit.colTime),
                render: (r) => new Date(r.createdAt).toLocaleString(),
              },
              {
                key: 'actor',
                header: tt(I18N_KEYS.audit.colActor),
                render: (r) => r.actorUsername ?? r.actorIdentityId ?? '—',
              },
              {
                key: 'action',
                header: tt(I18N_KEYS.audit.colAction),
                render: (r) => r.action,
              },
              {
                key: 'target',
                header: tt(I18N_KEYS.audit.colTarget),
                render: (r) =>
                  r.targetId ? `${r.targetType}:${r.targetId}` : r.targetType,
              },
              {
                key: 'tenant',
                header: tt(I18N_KEYS.audit.colTenant),
                render: (r) => r.tenantId ?? '—',
              },
              {
                key: 'result',
                header: tt(I18N_KEYS.audit.colResult),
                render: (r) =>
                  r.result === 'SUCCESS' ? (
                    <StatusBadge kind="success">
                      {tt(I18N_KEYS.audit.resultSuccess)}
                    </StatusBadge>
                  ) : (
                    <StatusBadge kind="error">
                      {tt(I18N_KEYS.audit.resultFailure)}
                    </StatusBadge>
                  ),
              },
              {
                key: 'reason',
                header: tt(I18N_KEYS.audit.colReason),
                render: (r) => r.reason ?? '—',
              },
              {
                key: 'ip',
                header: tt(I18N_KEYS.audit.colIp),
                render: (r) => r.ip ?? '—',
              },
            ]}
          />
        )}
      </TriState>
    </AdminPageShell>
  );
}
