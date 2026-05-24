/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file SuperAdminListPage — paginated list of platform super-admins.
 *
 * Composes {@link useSuperAdminList} (read), {@link useRevokeSuperAdmin}
 * (mutation), and the two dialogs `CreateSuperAdminDialog` /
 * `ResetPasswordDialog` (setup-link flows).
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
  IconActionButton,
} from '../internal/ui-kit';
import { useSuperAdminList } from '../hooks/useSuperAdminList';
import { useRevokeSuperAdmin } from '../hooks/useRevokeSuperAdmin';
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
  | { kind: 'revoke'; admin: PlatformAdminDto }
  | { kind: 'reset'; admin: PlatformAdminDto };

export function SuperAdminListPage(): JSX.Element {
  const { Button, Input, Modal, message } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);
  const { hasPermission } = useAuth();

  const list = useSuperAdminList();
  const revoke = useRevokeSuperAdmin();
  const [dialog, setDialog] = useState<DialogState>({ kind: 'none' });
  const [revokeReason, setRevokeReason] = useState('');
  const [reasonTouched, setReasonTouched] = useState(false);

  const canCreate = hasPermission(PLATFORM_ADMIN_PERMISSIONS.ADMIN_CREATE);
  const canRevoke = hasPermission(PLATFORM_ADMIN_PERMISSIONS.ADMIN_REVOKE);
  const canReset = hasPermission(
    PLATFORM_ADMIN_PERMISSIONS.ADMIN_RESET_PASSWORD,
  );
  const adminsOnPage = list.data?.content ?? [];
  const activeCount = adminsOnPage.filter(
    (admin) => admin.status === PLATFORM_ADMIN_STATUS.ACTIVE,
  ).length;
  const revokedCount = adminsOnPage.filter(
    (admin) => admin.status === PLATFORM_ADMIN_STATUS.REVOKED,
  ).length;

  function openRevoke(admin: PlatformAdminDto) {
    setRevokeReason('');
    setReasonTouched(false);
    setDialog({ kind: 'revoke', admin });
  }

  async function confirmRevoke() {
    if (dialog.kind !== 'revoke') return;
    setReasonTouched(true);
    if (!revokeReason.trim()) return;
    try {
      await revoke.revoke(dialog.admin.id, { reason: revokeReason.trim() });
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

            {canCreate ? (
              <Button
                onClick={() => setDialog({ kind: 'create' })}
                data-testid="super-admin-create-open"
              >
                {tt(I18N_KEYS.admin.create)}
              </Button>
            ) : null}
            <Button
              variant="secondary"
              onClick={() => list.refresh()}
              data-testid="super-admin-refresh"
            >
              {tt(I18N_KEYS.common.refresh)}
            </Button>
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
            label: tt(I18N_KEYS.admin.summaryRevoked),
            value: revokedCount,
            tone: revokedCount > 0 ? 'warning' : 'neutral',
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
                  <div style={{ display: 'grid', gap: t.space.xs }}>
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
                      {tt(I18N_KEYS.admin.statusRevoked)}
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
                  <div style={{ display: 'flex', gap: t.space.xs, flexWrap: 'nowrap', justifyContent: 'center' }}>
                    {canReset && r.status === PLATFORM_ADMIN_STATUS.ACTIVE ? (
                      <IconActionButton
                        icon="vpn_key"
                        label={tt(I18N_KEYS.admin.actionResetPassword)}
                        onClick={() => setDialog({ kind: 'reset', admin: r })}
                        data-testid="super-admin-row-reset-password"
                      />
                    ) : null}
                    {canRevoke && r.status === PLATFORM_ADMIN_STATUS.ACTIVE ? (
                      <IconActionButton
                        icon="block"
                        label={tt(I18N_KEYS.admin.actionRevoke)}
                        tone="danger"
                        onClick={() => openRevoke(r)}
                        data-testid="super-admin-row-revoke"
                      />
                    ) : null}
                  </div>
                ),
                align: 'center',
                width: '92px',
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

      {dialog.kind === 'revoke' ? (
        <Modal
          open
          title={tt(I18N_KEYS.admin.revokeDialogTitle)}
          onClose={() => {
            if (!revoke.loading) setDialog({ kind: 'none' });
          }}
          onConfirm={confirmRevoke}
          confirmLoading={revoke.loading}
          confirmText={tt(I18N_KEYS.admin.actionRevoke)}
          cancelText={tt(I18N_KEYS.common.cancel)}
          closeOnEscape={!revoke.loading}
          closeOnOverlayClick={false}
          data-testid="super-admin-revoke-dialog"
        >
          <div style={{ display: 'grid', gap: t.space.md }}>
            <div
              role="alert"
              style={{
                padding: t.space.md,
                borderRadius: t.shape.md,
                border: `1px solid color-mix(in srgb, ${t.colors.status.error} 36%, ${t.colors.border.default})`,
                background: `color-mix(in srgb, ${t.colors.status.error} 8%, ${t.colors.surface.elevated})`,
                color: t.colors.text.primary,
              }}
            >
              <strong style={{ display: 'block', marginBottom: t.space.xs }}>
                {tt(I18N_KEYS.admin.revokeConfirm, {
                  name: dialog.admin.username,
                })}
              </strong>
              <span style={{ color: t.colors.text.secondary }}>
                {dialog.admin.email ?? dialog.admin.role}
              </span>
            </div>
            <Input
              type="text"
              multiline
              rows={3}
              label={tt(I18N_KEYS.admin.revokeReason)}
              value={revokeReason}
              required
              fullWidth
              onChange={(e) => setRevokeReason(e.target.value)}
              error={reasonTouched && !revokeReason.trim()}
              helperText={
                reasonTouched && !revokeReason.trim()
                  ? tt(I18N_KEYS.admin.requiredField)
                  : ''
              }
              data-testid="super-admin-revoke-reason"
            />
            {revoke.error ? (
              <div
                role="alert"
                style={{
                  padding: t.space.sm,
                  borderRadius: t.shape.sm,
                  background: `color-mix(in srgb, ${t.colors.status.error} 12%, ${t.colors.surface.elevated})`,
                  color: t.colors.status.error,
                  border: `1px solid color-mix(in srgb, ${t.colors.status.error} 36%, ${t.colors.border.default})`,
                  fontSize: t.typography.bodySmall.fontSize,
                }}
              >
                {revoke.error.message}
              </div>
            ) : null}
          </div>
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
