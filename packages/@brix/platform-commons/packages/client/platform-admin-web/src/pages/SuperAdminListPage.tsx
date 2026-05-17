/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file SuperAdminListPage — paginated list of platform super-admins.
 *
 * Composes {@link useSuperAdminList} (read), {@link useDisableSuperAdmin}
 * (mutation), and the two dialogs `CreateSuperAdminDialog` /
 * `ResetPasswordDialog` (one-shot temp-password flows).
 *
 * Permission gating uses bare permission codes from
 * {@link PLATFORM_ADMIN_PERMISSIONS}; never string literals.
 */

import { useState } from 'react';
import { useAuth, useI18n, useTheme } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import {
  useUIStrict,
  AdminPageShell,
  PageHeader,
  SummaryGrid,
  TriState,
  DataTable,
  StatusBadge,
} from '../internal/ui-kit';
import { useSuperAdminList } from '../hooks/useSuperAdminList';
import { useDisableSuperAdmin } from '../hooks/useDisableSuperAdmin';
import { CreateSuperAdminDialog } from './CreateSuperAdminDialog';
import { ResetPasswordDialog } from './ResetPasswordDialog';
import {
  PLATFORM_ADMIN_PERMISSIONS,
  PLATFORM_ADMIN_STATUS,
} from '../constants';
import { I18N_KEYS, I18N_NAMESPACE, makeT } from '../i18n';
import type { PlatformAdminDto } from '../types';

type DialogState =
  | { kind: 'none' }
  | { kind: 'create' }
  | { kind: 'disable'; admin: PlatformAdminDto }
  | { kind: 'reset'; admin: PlatformAdminDto };

export function SuperAdminListPage(): JSX.Element {
  const { Button, Input, Modal, message } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);
  const { hasPermission } = useAuth();

  const list = useSuperAdminList();
  const disable = useDisableSuperAdmin();
  const [dialog, setDialog] = useState<DialogState>({ kind: 'none' });
  const [disableReason, setDisableReason] = useState('');
  const [reasonTouched, setReasonTouched] = useState(false);

  const canCreate = hasPermission(PLATFORM_ADMIN_PERMISSIONS.ADMIN_CREATE);
  const canDisable = hasPermission(PLATFORM_ADMIN_PERMISSIONS.ADMIN_DISABLE);
  const canReset = hasPermission(
    PLATFORM_ADMIN_PERMISSIONS.ADMIN_RESET_PASSWORD,
  );
  const adminsOnPage = list.data?.content ?? [];
  const activeCount = adminsOnPage.filter(
    (admin) => admin.status === PLATFORM_ADMIN_STATUS.ACTIVE,
  ).length;
  const disabledCount = adminsOnPage.filter(
    (admin) => admin.status === PLATFORM_ADMIN_STATUS.DISABLED,
  ).length;

  function openDisable(admin: PlatformAdminDto) {
    setDisableReason('');
    setReasonTouched(false);
    setDialog({ kind: 'disable', admin });
  }

  async function confirmDisable() {
    if (dialog.kind !== 'disable') return;
    setReasonTouched(true);
    if (!disableReason.trim()) return;
    try {
      await disable.disable(dialog.admin.id, { reason: disableReason.trim() });
      message.success?.('OK');
      setDialog({ kind: 'none' });
      await list.refresh();
    } catch (e) {
      message.error?.(e instanceof Error ? e.message : String(e));
    }
  }

  return (
    <AdminPageShell>
      <PageHeader
        title={tt(I18N_KEYS.admin.listTitle)}
        subtitle={tt(I18N_KEYS.admin.listSubtitle)}
        actions={
          <>
            <Button
              variant="secondary"
              onClick={() => list.refresh()}
              data-testid="super-admin-refresh"
            >
              {tt(I18N_KEYS.common.refresh)}
            </Button>
            {canCreate ? (
              <Button
                onClick={() => setDialog({ kind: 'create' })}
                data-testid="super-admin-create-open"
              >
                {tt(I18N_KEYS.admin.create)}
              </Button>
            ) : null}
          </>
        }
      />

      <SummaryGrid
        items={[
          {
            label: tt(I18N_KEYS.admin.summaryTotal),
            value: list.data?.totalElements ?? '—',
            helper: tt(I18N_KEYS.admin.summaryPageSize, {
              count: list.query.size ?? adminsOnPage.length,
            }),
            tone: 'brand',
          },
          {
            label: tt(I18N_KEYS.admin.summaryActive),
            value: activeCount,
            tone: 'success',
          },
          {
            label: tt(I18N_KEYS.admin.summaryDisabled),
            value: disabledCount,
            tone: disabledCount > 0 ? 'warning' : 'neutral',
          },
        ]}
      />

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
                key: 'username',
                header: tt(I18N_KEYS.admin.colUsername),
                render: (r) => (
                  <div style={{ display: 'grid', gap: 2 }}>
                    <strong style={{ fontWeight: 700 }}>
                      {r.displayName ?? r.username}
                    </strong>
                    {r.displayName ? (
                      <span style={{ color: t.colors.text.secondary }}>
                        {r.username}
                      </span>
                    ) : null}
                  </div>
                ),
              },
              {
                key: 'email',
                header: tt(I18N_KEYS.admin.colEmail),
                render: (r) => r.email ?? '—',
              },
              {
                key: 'role',
                header: tt(I18N_KEYS.admin.colRole),
                render: (r) => r.role,
              },
              {
                key: 'status',
                header: tt(I18N_KEYS.admin.colStatus),
                render: (r) =>
                  r.status === PLATFORM_ADMIN_STATUS.ACTIVE ? (
                    <StatusBadge kind="success">
                      {tt(I18N_KEYS.admin.statusActive)}
                    </StatusBadge>
                  ) : (
                    <StatusBadge kind="neutral">
                      {tt(I18N_KEYS.admin.statusDisabled)}
                    </StatusBadge>
                  ),
              },
              {
                key: 'createdAt',
                header: tt(I18N_KEYS.admin.colCreatedAt),
                render: (r) => formatTs(r.createdAt),
              },
              {
                key: 'lastLogin',
                header: tt(I18N_KEYS.admin.colLastLogin),
                render: (r) => formatTs(r.lastLoginAt),
              },
              {
                key: 'actions',
                header: tt(I18N_KEYS.common.actions),
                render: (r) => (
                  <div style={{ display: 'flex', gap: t.space.xs, flexWrap: 'wrap' }}>
                    {canReset && r.status === PLATFORM_ADMIN_STATUS.ACTIVE ? (
                      <Button
                        size="small"
                        variant="secondary"
                        onClick={() => setDialog({ kind: 'reset', admin: r })}
                        data-testid="super-admin-row-reset-password"
                      >
                        {tt(I18N_KEYS.admin.actionResetPassword)}
                      </Button>
                    ) : null}
                    {canDisable && r.status === PLATFORM_ADMIN_STATUS.ACTIVE ? (
                      <Button
                        size="small"
                        variant="danger"
                        onClick={() => openDisable(r)}
                        data-testid="super-admin-row-disable"
                      >
                        {tt(I18N_KEYS.admin.actionDisable)}
                      </Button>
                    ) : null}
                  </div>
                ),
              },
            ]}
          />
        )}
      </TriState>

      {dialog.kind === 'create' ? (
        <CreateSuperAdminDialog
          open
          onClose={() => setDialog({ kind: 'none' })}
          onCreated={() => list.refresh()}
        />
      ) : null}

      {dialog.kind === 'reset' ? (
        <ResetPasswordDialog
          open
          admin={dialog.admin}
          onClose={() => setDialog({ kind: 'none' })}
        />
      ) : null}

      {dialog.kind === 'disable' ? (
        <Modal
          open
          title={tt(I18N_KEYS.admin.disableDialogTitle)}
          onClose={() => setDialog({ kind: 'none' })}
          onConfirm={confirmDisable}
          confirmLoading={disable.loading}
          confirmText={tt(I18N_KEYS.common.confirm)}
          cancelText={tt(I18N_KEYS.common.cancel)}
          data-testid="super-admin-disable-dialog"
        >
          <p>
            {tt(I18N_KEYS.admin.disableConfirm, {
              name: dialog.admin.username,
            })}
          </p>
          <Input
            type="text"
            multiline
            rows={3}
            label={tt(I18N_KEYS.admin.disableReason)}
            value={disableReason}
            required
            fullWidth
            onChange={(e) => setDisableReason(e.target.value)}
            error={reasonTouched && !disableReason.trim()}
            helperText={
              reasonTouched && !disableReason.trim()
                ? tt(I18N_KEYS.admin.requiredField)
                : ''
            }
            data-testid="super-admin-disable-reason"
          />
        </Modal>
      ) : null}
    </AdminPageShell>
  );
}

function formatTs(iso: string | null): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}
