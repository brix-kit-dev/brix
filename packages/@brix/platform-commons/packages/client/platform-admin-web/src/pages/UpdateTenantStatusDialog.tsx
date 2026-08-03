/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file UpdateTenantStatusDialog — change a tenant's lifecycle status.
 *
 * Client only offers operator-controlled transitions (ACTIVE ↔ SUSPENDED).
 * PENDING_ACTIVATION is activated only by the FIRST_OWNER acceptance flow.
 */

import { useState } from 'react';
import { useI18n, useTheme } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import { useUIStrict } from '../internal/ui-kit';
import { useUpdateTenantStatus } from '../hooks/useUpdateTenantStatus';
import {
  PLATFORM_TENANT_STATUS,
  type PlatformTenantStatus,
} from '../constants';
import { I18N_KEYS, I18N_NAMESPACE, makeT } from '../i18n';
import type { PlatformTenantDto } from '../types';

export interface UpdateTenantStatusDialogProps {
  open: boolean;
  tenant: PlatformTenantDto;
  onClose: () => void;
  onUpdated?: () => void;
}

export function getTenantStatusTransitionTargets(
  status: PlatformTenantStatus,
): readonly PlatformTenantStatus[] {
  switch (status) {
    case PLATFORM_TENANT_STATUS.ACTIVE:
      return [PLATFORM_TENANT_STATUS.SUSPENDED];
    case PLATFORM_TENANT_STATUS.SUSPENDED:
      return [PLATFORM_TENANT_STATUS.ACTIVE];
    default:
      return [];
  }
}

export function UpdateTenantStatusDialog(
  props: UpdateTenantStatusDialogProps,
): JSX.Element {
  const { Modal, Select, Input, Alert, message } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);
  const { update, loading, error } = useUpdateTenantStatus();
  const statusTargets = getTenantStatusTransitionTargets(props.tenant.status);
  const hasLegalTransition = statusTargets.length > 0;
  const statusOptions = statusTargets.map((v) => ({ value: v, label: v }));

  const [target, setTarget] = useState<PlatformTenantStatus>(
    statusTargets[0] ?? props.tenant.status,
  );
  const [reason, setReason] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const reasonInvalid = !reason.trim();
  const sameStatus = target === props.tenant.status;

  async function handleConfirm() {
    setSubmitted(true);
    if (!hasLegalTransition || reasonInvalid || sameStatus) return;
    try {
      await update(props.tenant.id, { status: target, reason: reason.trim() });
      message.success?.('OK');
      props.onUpdated?.();
    } catch (e) {
      message.error?.(e instanceof Error ? e.message : String(e));
    }
  }

  return (
    <Modal
      open={props.open}
      title={tt(I18N_KEYS.tenant.statusDialogTitle)}
      onClose={() => !loading && props.onClose()}
      onConfirm={hasLegalTransition ? handleConfirm : undefined}
      confirmLoading={loading}
      confirmText={tt(I18N_KEYS.common.confirm)}
      cancelText={tt(I18N_KEYS.common.cancel)}
      data-testid="platform-tenant-status-dialog"
    >
      <p style={{ marginTop: 0, color: t.colors.text.secondary }}>
        {props.tenant.code} — {props.tenant.name}
      </p>
      {hasLegalTransition ? (
        <>
          <Select
            label={tt(I18N_KEYS.tenant.colStatus)}
            options={statusOptions}
            value={target}
            onChange={(v) => setTarget(v as PlatformTenantStatus)}
            fullWidth
            required
            data-testid="platform-tenant-status-target"
          />
          <div style={{ height: t.space.sm }} />
          <Input
            type="text"
            label={tt(I18N_KEYS.tenant.statusReason)}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            required
            fullWidth
            multiline
            rows={3}
            error={submitted && reasonInvalid}
            helperText={
              submitted && reasonInvalid ? tt(I18N_KEYS.admin.requiredField) : ''
            }
            data-testid="platform-tenant-status-reason"
          />
        </>
      ) : (
        <Alert severity="warning" data-testid="platform-tenant-status-blocked">
          PENDING_ACTIVATION tenants are activated only after the invited
          FIRST_OWNER accepts the invitation.
        </Alert>
      )}

      {error ? (
        <div
          role="alert"
          style={{
            marginTop: t.space.md,
            padding: t.space.sm,
            borderRadius: t.shape.sm,
            background: `color-mix(in srgb, ${t.colors.status.error} 12%, ${t.colors.surface.elevated})`,
            color: t.colors.status.error,
            border: `1px solid color-mix(in srgb, ${t.colors.status.error} 36%, ${t.colors.border.default})`,
            fontSize: t.typography.bodySmall.fontSize,
          }}
        >
          {error.message}
        </div>
      ) : null}
    </Modal>
  );
}
