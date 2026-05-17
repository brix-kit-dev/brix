/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file CreateSuperAdminDialog — modal form to provision a new platform admin.
 *
 * UX contract (SSOT §9 + §8.4):
 *  1. Operator fills username/email/role/notes; client-side validation runs.
 *  2. On submit the backend returns a one-shot `tempPassword`.
 *  3. The dialog switches into "reveal" mode showing the password with a
 *     copy button and an explicit acknowledgement step. Closing the dialog
 *     calls `acknowledge()` which clears the password from React state.
 */

import { useState, type FormEvent } from 'react';
import { useI18n, useTheme } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import { useUIStrict } from '../internal/ui-kit';
import { useCreateSuperAdmin } from '../hooks/useCreateSuperAdmin';
import { I18N_KEYS, I18N_NAMESPACE, makeT } from '../i18n';
import { PLATFORM_ROLE_CODE, type PlatformRoleCode } from '../constants';

const USERNAME_RE = /^[A-Za-z0-9._-]{3,64}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const ROLE_OPTIONS: ReadonlyArray<{ value: PlatformRoleCode; label: string }> = [
  { value: PLATFORM_ROLE_CODE.PLATFORM_ADMIN, label: 'Platform Admin' },
  { value: PLATFORM_ROLE_CODE.SUPPORT_ADMIN, label: 'Support Admin' },
  { value: PLATFORM_ROLE_CODE.AUDITOR, label: 'Auditor' },
  // SUPER_ADMIN is intentionally NOT in the options — bootstrap-only role
  // (SSOT §4.1: SUPER_ADMIN may only be created via the YAML bootstrap path).
];

export interface CreateSuperAdminDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated?: () => void;
}

export function CreateSuperAdminDialog(
  props: CreateSuperAdminDialogProps,
): JSX.Element {
  const { Modal, Input, Select, Button, message } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);

  const { create, acknowledge, loading, error, result } = useCreateSuperAdmin();

  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [role, setRole] = useState<PlatformRoleCode>(
    PLATFORM_ROLE_CODE.PLATFORM_ADMIN,
  );
  const [notes, setNotes] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const usernameInvalid = !USERNAME_RE.test(username);
  const emailInvalid = !EMAIL_RE.test(email);

  function handleClose() {
    if (loading) return;
    acknowledge();
    setSubmitted(false);
    setUsername('');
    setEmail('');
    setDisplayName('');
    setRole(PLATFORM_ROLE_CODE.PLATFORM_ADMIN);
    setNotes('');
    props.onClose();
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitted(true);
    if (usernameInvalid || emailInvalid) return;

    try {
      await create({
        username,
        email,
        displayName: displayName || undefined,
        role,
        notes: notes || undefined,
      });
      props.onCreated?.();
    } catch (err) {
      message.error?.(err instanceof Error ? err.message : String(err));
    }
  }

  async function copyPassword() {
    if (!result) return;
    try {
      await navigator.clipboard.writeText(result.tempPassword);
      message.success?.(tt(I18N_KEYS.common.copied));
    } catch {
      message.error?.('Clipboard unavailable');
    }
  }

  // Reveal mode (after success)
  if (result) {
    return (
      <Modal
        open={props.open}
        title={tt(I18N_KEYS.admin.tempPasswordTitle)}
        onClose={handleClose}
        showCloseButton={false}
        closeOnEscape={false}
        closeOnOverlayClick={false}
        data-testid="super-admin-create-password-modal"
        footer={
          <Button
            onClick={handleClose}
            data-testid="super-admin-create-password-close"
          >
            {tt(I18N_KEYS.common.close)}
          </Button>
        }
      >
        <div
          role="alert"
          data-testid="super-admin-create-password-warning"
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
            onClick={copyPassword}
            data-testid="super-admin-create-password-copy"
          >
            {tt(I18N_KEYS.common.copy)}
          </Button>
        </div>
        <p style={{ marginTop: t.space.md, color: t.colors.text.secondary, fontSize: '0.875rem' }}>
          {tt(I18N_KEYS.admin.tempPasswordExpires, {
            when: new Date(result.tempPasswordExpiresAt).toLocaleString(),
          })}
        </p>
      </Modal>
    );
  }

  // Form mode
  return (
    <Modal
      open={props.open}
      title={tt(I18N_KEYS.admin.createDialogTitle)}
      onClose={handleClose}
      onConfirm={() => {
        // Synthesise a submit event so we go through the validation path.
        handleSubmit({ preventDefault() {} } as FormEvent);
      }}
      confirmLoading={loading}
      confirmText={tt(I18N_KEYS.common.confirm)}
      cancelText={tt(I18N_KEYS.common.cancel)}
      data-testid="super-admin-create-dialog"
    >
      <form
        data-testid="super-admin-create-form"
        onSubmit={handleSubmit}
        noValidate
      >
        <Input
          type="text"
          label={tt(I18N_KEYS.admin.fieldUsername)}
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
          fullWidth
          maxLength={64}
          autoComplete="off"
          error={submitted && usernameInvalid}
          helperText={
            submitted && usernameInvalid
              ? tt(I18N_KEYS.admin.invalidUsername)
              : ''
          }
          data-testid="super-admin-create-username"
        />
        <div style={{ height: t.space.sm }} />
        <Input
          type="email"
          label={tt(I18N_KEYS.admin.fieldEmail)}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
          fullWidth
          autoComplete="off"
          error={submitted && emailInvalid}
          helperText={
            submitted && emailInvalid ? tt(I18N_KEYS.admin.invalidEmail) : ''
          }
          data-testid="super-admin-create-email"
        />
        <div style={{ height: t.space.sm }} />
        <Input
          type="text"
          label={tt(I18N_KEYS.admin.fieldDisplayName)}
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          fullWidth
          maxLength={120}
          data-testid="super-admin-create-display-name"
        />
        <div style={{ height: t.space.sm }} />
        <Select
          label={tt(I18N_KEYS.admin.fieldRole)}
          value={role}
          options={ROLE_OPTIONS.slice()}
          fullWidth
          required
          onChange={(v) => setRole(v as PlatformRoleCode)}
          data-testid="super-admin-create-role"
        />
        <div style={{ height: t.space.sm }} />
        <Input
          type="text"
          label={tt(I18N_KEYS.admin.fieldNotes)}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          fullWidth
          multiline
          rows={2}
          maxLength={500}
          data-testid="super-admin-create-notes"
        />

        {error ? (
          <div
            role="alert"
            data-testid="super-admin-create-error"
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
      </form>
    </Modal>
  );
}
