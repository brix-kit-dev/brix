/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file ResetPasswordDialog — requests setup-link based credential reset.
 */

import { useI18n, useTheme } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import { useUIStrict } from '../internal/ui-kit';
import { useResetPassword } from '../hooks/useResetPassword';
import { I18N_KEYS, I18N_NAMESPACE, makeT } from '../i18n';
import type { PlatformAdminDto } from '../types';

export interface ResetPasswordDialogProps {
  open: boolean;
  admin: PlatformAdminDto;
  onClose: () => void;
}

export function ResetPasswordDialog(
  props: ResetPasswordDialogProps,
): JSX.Element {
  const { Modal, Button, message } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);

  const { reset, acknowledge, loading, error, result } = useResetPassword();

  function handleClose() {
    if (loading) return;
    acknowledge();
    props.onClose();
  }

  async function handleConfirm() {
    if (loading || result) return;
    try {
      await reset(props.admin.id);
    } catch (err) {
      message.error?.(err instanceof Error ? err.message : String(err));
    }
  }

  return (
    <Modal
      open={props.open}
      title={
        result
          ? tt(I18N_KEYS.admin.setupLinkTitle)
          : tt(I18N_KEYS.admin.actionResetPassword)
      }
      onClose={handleClose}
      onConfirm={result ? undefined : handleConfirm}
      confirmLoading={loading}
      confirmText={tt(I18N_KEYS.admin.actionResetPassword)}
      cancelText={tt(I18N_KEYS.common.cancel)}
      showCloseButton={!loading}
      closeOnEscape={!loading}
      closeOnOverlayClick={false}
      data-testid="super-admin-reset-password-dialog"
      footer={result ? (
        <Button
          onClick={handleClose}
          disabled={loading}
          data-testid="super-admin-reset-password-close"
        >
          {tt(I18N_KEYS.common.close)}
        </Button>
      ) : undefined}
    >
      {!result ? (
        <div style={{ display: 'grid', gap: t.space.md }}>
          <div
            role="alert"
            style={{
              padding: t.space.md,
              borderRadius: t.shape.md,
              border: `1px solid color-mix(in srgb, ${t.colors.status.warning} 42%, ${t.colors.border.default})`,
              background: `color-mix(in srgb, ${t.colors.status.warning} 10%, ${t.colors.surface.elevated})`,
              color: t.colors.text.primary,
            }}
          >
            <strong style={{ display: 'block', marginBottom: t.space.xs }}>
              {tt(I18N_KEYS.admin.resetConfirm, {
                name: props.admin.username,
              })}
            </strong>
            <span style={{ color: t.colors.text.secondary }}>
              {tt(I18N_KEYS.admin.setupLinkInfo)}
            </span>
          </div>
          <dl
            style={{
              display: 'grid',
              gridTemplateColumns: 'max-content minmax(0, 1fr)',
              gap: `${t.space.xs} ${t.space.md}`,
              margin: 0,
              color: t.colors.text.secondary,
              fontSize: t.typography.bodySmall.fontSize,
            }}
          >
            <dt>{tt(I18N_KEYS.admin.colUsername)}</dt>
            <dd style={{ margin: 0, color: t.colors.text.primary }}>
              {props.admin.username}
            </dd>
            <dt>{tt(I18N_KEYS.admin.colEmail)}</dt>
            <dd style={{ margin: 0, color: t.colors.text.primary }}>
              {props.admin.email ?? '—'}
            </dd>
            <dt>{tt(I18N_KEYS.admin.colRole)}</dt>
            <dd style={{ margin: 0, color: t.colors.text.primary }}>
              {props.admin.role}
            </dd>
          </dl>
          {loading ? (
            <p style={{ margin: 0, color: t.colors.text.secondary }}>
              {tt(I18N_KEYS.common.loading)}
            </p>
          ) : null}
          {error ? (
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
              {error.message}
            </div>
          ) : null}
        </div>
      ) : (
        <>
          <div
            role="alert"
            style={{
              padding: t.space.sm,
              borderRadius: t.shape.sm,
              background: `color-mix(in srgb, ${t.colors.status.warning} 12%, ${t.colors.surface.elevated})`,
              color: t.colors.status.warning,
              border: `1px solid color-mix(in srgb, ${t.colors.status.warning} 36%, ${t.colors.border.default})`,
              marginBottom: t.space.md,
              fontSize: t.typography.bodySmall.fontSize,
            }}
          >
            {result.setupLinkSent
              ? tt(I18N_KEYS.admin.setupLinkSent)
              : tt(I18N_KEYS.admin.setupLinkPending)}
          </div>
          <p
            style={{
              marginTop: t.space.md,
              color: t.colors.text.secondary,
              fontSize: t.typography.bodySmall.fontSize,
            }}
          >
            {tt(I18N_KEYS.admin.setupLinkInfo)}
          </p>
        </>
      )}
    </Modal>
  );
}
