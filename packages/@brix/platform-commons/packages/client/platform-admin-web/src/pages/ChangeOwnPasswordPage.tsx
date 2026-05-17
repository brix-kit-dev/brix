/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file ChangeOwnPasswordPage — self-service password change.
 *
 * This is the ONLY endpoint callable while `forcePasswordChange === true`.
 * After success the host MUST drop the current token (the old hash is no
 * longer valid server-side) — we surface this via {@link onPasswordChanged}.
 */

import { useState, type FormEvent } from 'react';
import { useI18n, useTheme } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import { useUIStrict, AdminPageShell, PageHeader, StatusBadge } from '../internal/ui-kit';
import { useChangeOwnPassword } from '../hooks/useChangeOwnPassword';
import { I18N_KEYS, I18N_NAMESPACE, makeT } from '../i18n';

const STRONG_RE =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{12,}$/;

export interface ChangeOwnPasswordPageProps {
  /** Called after a successful change — host should clear the token & redirect to /login. */
  onPasswordChanged?: () => void;
}

export function ChangeOwnPasswordPage(
  props: ChangeOwnPasswordPageProps,
): JSX.Element {
  const { Card, Input, Button, message } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);
  const { change, loading, error, success } = useChangeOwnPassword();

  const [oldPw, setOldPw] = useState('');
  const [newPw, setNewPw] = useState('');
  const [confirmPw, setConfirmPw] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const newPwInvalid = !STRONG_RE.test(newPw);
  const mismatch = newPw !== confirmPw;
  const oldPwMissing = !oldPw;
  const passwordRequirements = [
    {
      label: tt(I18N_KEYS.changePassword.requirementLength),
      met: newPw.length >= 12,
    },
    {
      label: tt(I18N_KEYS.changePassword.requirementUpper),
      met: /[a-z]/.test(newPw) && /[A-Z]/.test(newPw),
    },
    {
      label: tt(I18N_KEYS.changePassword.requirementDigit),
      met: /\d/.test(newPw),
    },
    {
      label: tt(I18N_KEYS.changePassword.requirementSymbol),
      met: /[^A-Za-z0-9]/.test(newPw),
    },
  ];

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitted(true);
    if (oldPwMissing || newPwInvalid || mismatch) return;
    try {
      await change({ oldPassword: oldPw, newPassword: newPw });
      message.success?.(tt(I18N_KEYS.changePassword.success));
      // Clear locally — the password is no longer needed in memory.
      setOldPw('');
      setNewPw('');
      setConfirmPw('');
      props.onPasswordChanged?.();
    } catch (err) {
      message.error?.(err instanceof Error ? err.message : String(err));
    }
  }

  return (
    <AdminPageShell maxWidth={980}>
      <PageHeader
        title={tt(I18N_KEYS.changePassword.title)}
        subtitle={tt(I18N_KEYS.changePassword.subtitle)}
      />
      <div
        className="platform-admin-password-grid"
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 560px) minmax(260px, 1fr)',
          gap: t.space.md,
          alignItems: 'start',
        }}
      >
        <style>
          {`
            @media (max-width: ${t.breakpoints.md}px) {
              .platform-admin-password-grid { grid-template-columns: 1fr !important; }
            }
          `}
        </style>
        <Card
          style={{
            padding: t.space.lg,
            border: `1px solid color-mix(in srgb, ${t.colors.border.default} 72%, transparent)`,
            boxShadow: 'none',
          }}
        >
          <h2
            style={{
              margin: `0 0 ${t.space.lg}`,
              color: t.colors.text.primary,
              fontSize: t.typography.titleMedium.fontSize,
              lineHeight: t.typography.titleMedium.lineHeight,
            }}
          >
            {tt(I18N_KEYS.changePassword.cardTitle)}
          </h2>
        {success ? (
          <p style={{ color: t.colors.status.success }}>
            {tt(I18N_KEYS.changePassword.success)}
          </p>
        ) : (
          <form
            data-testid="change-password-form"
            onSubmit={handleSubmit}
            noValidate
          >
            <Input
              type="password"
              label={tt(I18N_KEYS.changePassword.oldPassword)}
              value={oldPw}
              onChange={(e) => setOldPw(e.target.value)}
              required
              fullWidth
              autoComplete="current-password"
              error={submitted && oldPwMissing}
              helperText={
                submitted && oldPwMissing
                  ? tt(I18N_KEYS.admin.requiredField)
                  : ''
              }
              disabled={loading}
              data-testid="change-password-old"
            />
            <div style={{ height: t.space.sm }} />
            <Input
              type="password"
              label={tt(I18N_KEYS.changePassword.newPassword)}
              value={newPw}
              onChange={(e) => setNewPw(e.target.value)}
              required
              fullWidth
              autoComplete="new-password"
              error={submitted && newPwInvalid}
              helperText={
                submitted && newPwInvalid
                  ? tt(I18N_KEYS.changePassword.weakPassword)
                  : ''
              }
              disabled={loading}
              data-testid="change-password-new"
            />
            <div style={{ height: t.space.sm }} />
            <Input
              type="password"
              label={tt(I18N_KEYS.changePassword.confirmNew)}
              value={confirmPw}
              onChange={(e) => setConfirmPw(e.target.value)}
              required
              fullWidth
              autoComplete="new-password"
              error={submitted && mismatch}
              helperText={
                submitted && mismatch ? tt(I18N_KEYS.changePassword.mismatch) : ''
              }
              disabled={loading}
              data-testid="change-password-confirm"
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

            <div style={{ marginTop: t.space.lg }}>
              <Button
                type="submit"
                loading={loading}
                disabled={loading}
                data-testid="change-password-submit"
              >
                {tt(I18N_KEYS.changePassword.submit)}
              </Button>
            </div>
          </form>
        )}
        </Card>

        <aside
          style={{
            padding: t.space.lg,
            borderRadius: t.shape.lg,
            border: `1px solid color-mix(in srgb, ${t.colors.brand.primary} 22%, ${t.colors.border.default})`,
            background: t.colors.surface.elevated,
          }}
        >
          <h2
            style={{
              margin: 0,
              color: t.colors.text.primary,
              fontSize: t.typography.titleSmall.fontSize,
              lineHeight: t.typography.titleSmall.lineHeight,
            }}
          >
            {tt(I18N_KEYS.changePassword.requirementTitle)}
          </h2>
          <div style={{ display: 'grid', gap: t.space.sm, marginTop: t.space.md }}>
            {passwordRequirements.map((requirement) => (
              <div
                key={requirement.label}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: t.space.sm,
                  color: t.colors.text.secondary,
                  fontSize: t.typography.bodySmall.fontSize,
                  lineHeight: t.typography.bodySmall.lineHeight,
                }}
              >
                <span>{requirement.label}</span>
                <StatusBadge kind={requirement.met ? 'success' : 'neutral'}>
                  {requirement.met ? 'OK' : '—'}
                </StatusBadge>
              </div>
            ))}
          </div>
        </aside>
      </div>
    </AdminPageShell>
  );
}
