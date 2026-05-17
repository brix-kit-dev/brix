/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file ResetPasswordDialog — issues a one-shot replacement temp password.
 *
 * Mirrors the same one-shot reveal flow as `CreateSuperAdminDialog` —
 * keeps the security model uniform (SSOT §8.4).
 */

import { useEffect } from 'react';
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

  // Auto-trigger on open: SSOT §6 endpoint #5 has no payload — clicking
  // "Reset" in the parent table is itself the user intent.
  useEffect(() => {
    if (props.open && !result && !loading) {
      void reset(props.admin.id).catch((e) => {
        message.error?.(e instanceof Error ? e.message : String(e));
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [props.open, props.admin.id]);

  function handleClose() {
    if (loading) return;
    acknowledge();
    props.onClose();
  }

  async function copy() {
    if (!result) return;
    try {
      await navigator.clipboard.writeText(result.tempPassword);
      message.success?.(tt(I18N_KEYS.common.copied));
    } catch {
      message.error?.('Clipboard unavailable');
    }
  }

  return (
    <Modal
      open={props.open}
      title={tt(I18N_KEYS.admin.tempPasswordTitle)}
      onClose={handleClose}
      showCloseButton={false}
      closeOnEscape={!loading}
      closeOnOverlayClick={false}
      data-testid="super-admin-reset-password-dialog"
      footer={
        <Button
          onClick={handleClose}
          disabled={loading}
          data-testid="super-admin-reset-password-close"
        >
          {tt(I18N_KEYS.common.close)}
        </Button>
      }
    >
      {loading ? (
        <p style={{ color: t.colors.text.secondary }}>
          {tt(I18N_KEYS.common.loading)}
        </p>
      ) : error ? (
        <div
          role="alert"
          style={{
            padding: t.space.sm,
            borderRadius: t.shape.sm,
            background: t.colors.status.error,
            color: t.colors.brand.primaryContrast,
          }}
        >
          {error.message}
        </div>
      ) : result ? (
        <>
          <p style={{ color: t.colors.text.primary, marginTop: 0 }}>
            {tt(I18N_KEYS.admin.resetConfirm, { name: props.admin.username })}
          </p>
          <div
            role="alert"
            style={{
              padding: t.space.sm,
              borderRadius: t.shape.sm,
              background: t.colors.status.warning,
              color: t.colors.brand.primaryContrast,
              marginBottom: t.space.md,
              fontSize: '0.875rem',
            }}
          >
            {tt(I18N_KEYS.admin.tempPasswordWarning)}
          </div>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: t.space.sm,
              padding: t.space.md,
              background: t.colors.surface.elevated,
              borderRadius: t.shape.md,
              border: `1px solid ${t.colors.border.default}`,
            }}
          >
            <code
              style={{
                flex: 1,
                fontFamily: 'monospace',
                fontSize: '1rem',
                wordBreak: 'break-all',
                color: t.colors.text.primary,
              }}
            >
              {result.tempPassword}
            </code>
            <Button
              size="small"
              variant="secondary"
              onClick={copy}
              data-testid="super-admin-reset-password-copy"
            >
              {tt(I18N_KEYS.common.copy)}
            </Button>
          </div>
          <p
            style={{
              marginTop: t.space.md,
              color: t.colors.text.secondary,
              fontSize: '0.875rem',
            }}
          >
            {tt(I18N_KEYS.admin.tempPasswordExpires, {
              when: new Date(result.tempPasswordExpiresAt).toLocaleString(),
            })}
          </p>
        </>
      ) : null}
    </Modal>
  );
}
