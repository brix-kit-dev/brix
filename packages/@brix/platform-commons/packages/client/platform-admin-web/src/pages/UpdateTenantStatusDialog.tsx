/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file UpdateTenantStatusDialog — change a tenant's lifecycle status.
 *
 * Client only offers the legal forward transitions (ACTIVE ↔ SUSPENDED).
 * Server-side StatusMachine remains the source of truth for what is allowed.
 */

import { useState } from 'react';
import { useI18n, useTheme } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import { useUIStrict } from '../internal/ui-kit';
import { useUpdateTenantStatus } from '../hooks/useUpdateTenantStatus';
import {
  PLATFORM_TENANT_STATUS,
  PLATFORM_TENANT_TRANSITIONABLE_STATUS,
  type PlatformTenantStatus,
} from '../constants';
import { I18N_KEYS, I18N_NAMESPACE, makeT } from '../i18n';
import type { PlatformTenantDto } from '../types';

const STATUS_OPTIONS = PLATFORM_TENANT_TRANSITIONABLE_STATUS.map((v) => ({
  value: v,
  label: v,
}));

export interface UpdateTenantStatusDialogProps {
  open: boolean;
  tenant: PlatformTenantDto;
  onClose: () => void;
  onUpdated?: () => void;
}

export function UpdateTenantStatusDialog(
  props: UpdateTenantStatusDialogProps,
): JSX.Element {
  const { Modal, Select, Input, message } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);
  const { update, loading, error } = useUpdateTenantStatus();

  const [target, setTarget] = useState<PlatformTenantStatus>(
    props.tenant.status === PLATFORM_TENANT_STATUS.ACTIVE
      ? PLATFORM_TENANT_STATUS.SUSPENDED
      : PLATFORM_TENANT_STATUS.ACTIVE,
  );
  const [reason, setReason] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const reasonInvalid = !reason.trim();
  const sameStatus = target === props.tenant.status;

  async function handleConfirm() {
    setSubmitted(true);
    if (reasonInvalid || sameStatus) return;
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
      onConfirm={handleConfirm}
      confirmLoading={loading}
      confirmText={tt(I18N_KEYS.common.confirm)}
      cancelText={tt(I18N_KEYS.common.cancel)}
      data-testid="platform-tenant-status-dialog"
    >
      <p style={{ marginTop: 0, color: t.colors.text.secondary }}>
        {props.tenant.code} — {props.tenant.name}
      </p>
      <Select
        label={tt(I18N_KEYS.tenant.colStatus)}
        options={STATUS_OPTIONS}
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

      {error ? (
        <div
          role="alert"
          style={{
            marginTop: t.space.md,
            padding: t.space.sm,
            borderRadius: t.shape.sm,
            background: t.colors.status.error,
            color: t.colors.brand.primaryContrast,
            fontSize: '0.875rem',
          }}
        >
          {error.message}
        </div>
      ) : null}
    </Modal>
  );
}
