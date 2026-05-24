/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file CreateSuperAdminDialog — modal form to provision a new platform admin.
 *
 * UX contract:
 *  1. Operator fills username/email/notes; client-side validation runs.
 *  2. On submit the backend starts setup-link delivery.
 *  3. The dialog reports delivery status without exposing credentials.
 */

import { useState, type FormEvent, type ReactNode } from 'react';
import { useI18n, useTheme } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import { useUIStrict } from '../internal/ui-kit';
import { useCreateSuperAdmin } from '../hooks/useCreateSuperAdmin';
import { I18N_KEYS, I18N_NAMESPACE, makeT } from '../i18n';
import { PLATFORM_ROLE_CODE, type PlatformRoleCode } from '../constants';

const USERNAME_RE = /^[A-Za-z0-9._-]{3,64}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const ROLE_OPTIONS: ReadonlyArray<{ value: PlatformRoleCode; label: string }> = [
  { value: PLATFORM_ROLE_CODE.PLATFORM_SUPER_ADMIN, label: 'Platform Super Admin' },
];

export interface CreateSuperAdminDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated?: () => void;
}

export function CreateSuperAdminDialog(
  props: CreateSuperAdminDialogProps,
): JSX.Element {
  const { Modal, Input, Select, Icon, message } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);

  const { create, acknowledge, loading, error, result } = useCreateSuperAdmin();

  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [role, setRole] = useState<PlatformRoleCode>(
    PLATFORM_ROLE_CODE.PLATFORM_SUPER_ADMIN,
  );
  const [notes, setNotes] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const usernameInvalid = !USERNAME_RE.test(username);
  const emailInvalid = !EMAIL_RE.test(email);
  const selectedRole = roleMeta(role, t);

  function handleClose() {
    if (loading) return;
    acknowledge();
    setSubmitted(false);
    setUsername('');
    setEmail('');
    setDisplayName('');
    setRole(PLATFORM_ROLE_CODE.PLATFORM_SUPER_ADMIN);
    setNotes('');
    props.onClose();
  }

  async function handleConfirm() {
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

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    await handleConfirm();
  }

  if (result) {
    return (
      <Modal
        open={props.open}
        title={tt(I18N_KEYS.admin.setupLinkTitle)}
        onClose={handleClose}
        size="medium"
        width={620}
        showCloseButton={false}
        closeOnEscape={false}
        closeOnOverlayClick={false}
        onConfirm={handleClose}
        confirmText={tt(I18N_KEYS.common.close)}
        showCancel={false}
        data-testid="super-admin-create-setup-modal"
      >
        <div style={{ display: 'grid', gap: t.space.md }}>
          <div
            role="alert"
            data-testid="super-admin-create-setup-status"
            style={{
              display: 'grid',
              gridTemplateColumns: 'auto 1fr',
              gap: t.space.sm,
              alignItems: 'start',
              padding: t.space.md,
              borderRadius: t.shape.md,
              background: `linear-gradient(135deg, color-mix(in srgb, ${t.colors.status.warning} 18%, ${t.colors.surface.elevated}) 0%, ${t.colors.surface.elevated} 100%)`,
              border: `1px solid color-mix(in srgb, ${t.colors.status.warning} 42%, ${t.colors.border.default})`,
              color: t.colors.text.primary,
              fontSize: t.typography.bodySmall.fontSize,
              lineHeight: t.typography.bodySmall.lineHeight,
            }}
          >
            <Icon name="security" size={18} color={t.colors.status.warning} />
            <span>
              {result.setupLinkSent
                ? tt(I18N_KEYS.admin.setupLinkSent)
                : tt(I18N_KEYS.admin.setupLinkPending)}
            </span>
          </div>

          <p
            style={{
              margin: 0,
              color: t.colors.text.secondary,
              fontSize: t.typography.bodySmall.fontSize,
              lineHeight: t.typography.bodySmall.lineHeight,
            }}
          >
            {tt(I18N_KEYS.admin.setupLinkInfo)}
          </p>
        </div>
      </Modal>
    );
  }

  return (
    <Modal
      open={props.open}
      title={tt(I18N_KEYS.admin.createDialogTitle)}
      onClose={handleClose}
      size="large"
      width={820}
      style={{
        backgroundColor: t.colors.surface.elevated,
        border: `1px solid ${t.colors.border.subtle}`,
        boxShadow: t.shadows.xl,
      }}
      onConfirm={handleConfirm}
      confirmText={tt(I18N_KEYS.admin.create)}
      confirmLoading={loading}
      showCancel={false}
      data-testid="super-admin-create-dialog"
    >
      <form
        data-testid="super-admin-create-form"
        onSubmit={handleSubmit}
        noValidate
      >
        <div style={{ display: 'grid', gap: t.space.lg }}>
          <div
            role="note"
            style={{
              display: 'flex',
              gap: t.space.sm,
              alignItems: 'center',
              padding: `${t.space.sm} ${t.space.md}`,
              borderRadius: t.shape.md,
              border: `1px solid color-mix(in srgb, ${t.colors.brand.primary} 28%, ${t.colors.border.subtle})`,
              background: `color-mix(in srgb, ${t.colors.brand.primary} 6%, ${t.colors.surface.card})`,
              color: t.colors.text.secondary,
              fontSize: t.typography.bodySmall.fontSize,
              lineHeight: t.typography.bodySmall.lineHeight,
            }}
          >
            <Icon name="security" size={18} color={t.colors.brand.primary} />
            <span>{tt(I18N_KEYS.admin.setupLinkInfo)}</span>
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns:
                'repeat(auto-fit, minmax(min(100%, 280px), 1fr))',
              gap: t.space.lg,
              alignItems: 'start',
            }}
          >
            <div
              style={{
                display: 'grid',
                gap: t.space.lg,
              }}
            >
              <DialogSection title="Identity" tokens={t}>
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns:
                      'repeat(auto-fit, minmax(min(100%, 220px), 1fr))',
                    gap: t.space.md,
                  }}
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
                      submitted && emailInvalid
                        ? tt(I18N_KEYS.admin.invalidEmail)
                        : ''
                    }
                    data-testid="super-admin-create-email"
                  />
                </div>
                <Input
                  type="text"
                  label={tt(I18N_KEYS.admin.fieldDisplayName)}
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  fullWidth
                  maxLength={120}
                  data-testid="super-admin-create-display-name"
                />
              </DialogSection>

              <DialogSection title="Audit note" tokens={t}>
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
              </DialogSection>
            </div>

            <div
              style={{
                display: 'grid',
                gap: t.space.md,
                padding: t.space.md,
                borderRadius: t.shape.md,
                border: `1px solid color-mix(in srgb, ${selectedRole.accent} 30%, ${t.colors.border.subtle})`,
                background: `linear-gradient(180deg, color-mix(in srgb, ${selectedRole.accent} 7%, ${t.colors.surface.card}) 0%, ${t.colors.surface.card} 100%)`,
              }}
            >
              <div
                style={{
                  color: t.colors.text.primary,
                  fontSize: t.typography.label.fontSize,
                  fontWeight: 750,
                  lineHeight: t.typography.label.lineHeight,
                }}
              >
                Access
              </div>
              <Select
                label={tt(I18N_KEYS.admin.fieldRole)}
                value={role}
                options={ROLE_OPTIONS.slice()}
                fullWidth
                required
                onChange={(v) => setRole(v as PlatformRoleCode)}
                data-testid="super-admin-create-role"
              />
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'auto 1fr',
                  gap: t.space.sm,
                  alignItems: 'start',
                  paddingTop: t.space.sm,
                  borderTop: `1px solid color-mix(in srgb, ${selectedRole.accent} 18%, ${t.colors.border.subtle})`,
                }}
              >
                <div
                  style={{
                    width: 34,
                    height: 34,
                    borderRadius: t.shape.sm,
                    display: 'grid',
                    placeItems: 'center',
                    background: `color-mix(in srgb, ${selectedRole.accent} 12%, ${t.colors.surface.elevated})`,
                  }}
                >
                  <Icon
                    name={selectedRole.icon}
                    size={18}
                    color={selectedRole.accent}
                  />
                </div>
                <div style={{ minWidth: 0 }}>
                  <div
                    style={{
                      color: t.colors.text.primary,
                      fontWeight: 700,
                      fontSize: t.typography.bodyMedium.fontSize,
                      lineHeight: t.typography.bodyMedium.lineHeight,
                    }}
                  >
                    {selectedRole.label}
                  </div>
                  <div
                    style={{
                      color: t.colors.text.secondary,
                      fontSize: t.typography.bodySmall.fontSize,
                      lineHeight: t.typography.bodySmall.lineHeight,
                    }}
                  >
                    {selectedRole.description}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {error ? (
            <div
              role="alert"
              data-testid="super-admin-create-error"
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
      </form>
    </Modal>
  );
}

interface DialogSectionProps {
  title: string;
  tokens: DesignTokens;
  children: ReactNode;
}

function DialogSection(props: DialogSectionProps): JSX.Element {
  const t = props.tokens;
  return (
    <section
      style={{
        display: 'grid',
        gap: t.space.sm,
        paddingBottom: t.space.sm,
        borderBottom: `1px solid ${t.colors.border.subtle}`,
      }}
    >
      <div
        style={{
          color: t.colors.text.primary,
          fontSize: t.typography.label.fontSize,
          fontWeight: 750,
          lineHeight: t.typography.label.lineHeight,
        }}
      >
        {props.title}
      </div>
      {props.children}
    </section>
  );
}

function roleMeta(role: PlatformRoleCode, t: DesignTokens) {
  switch (role) {
    case PLATFORM_ROLE_CODE.BOOTSTRAP:
      return {
        label: 'Bootstrap',
        description: 'Short-lived authority for first administrator initialization.',
        icon: 'lock_reset',
        accent: t.colors.status.warning,
      };
    default:
      return {
        label: 'Platform Super Admin',
        description: 'Full platform administration with audited lifecycle controls.',
        icon: 'admin_panel_settings',
        accent: t.colors.brand.primary,
      };
  }
}
