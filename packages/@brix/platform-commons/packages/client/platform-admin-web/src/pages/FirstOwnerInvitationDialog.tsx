/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useEffect, useState } from 'react';
import { useTheme } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import { useUIStrict } from '../internal/ui-kit';
import { useFirstOwnerInvitation } from '../hooks/useFirstOwnerInvitation';
import type { FirstOwnerInvitationDto, PlatformTenantDto } from '../types';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export interface FirstOwnerInvitationDialogProps {
  open: boolean;
  tenant: PlatformTenantDto;
  currentInvitation?: FirstOwnerInvitationDto | null;
  onClose: () => void;
  onChanged?: (invitation: FirstOwnerInvitationDto | null) => void;
}

export function FirstOwnerInvitationDialog(
  props: FirstOwnerInvitationDialogProps,
): JSX.Element {
  const { Modal, Stack, Input, Alert, Button, message } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const {
    create,
    current,
    loadCurrent,
    resend,
    revoke,
    loading,
    error,
  } = useFirstOwnerInvitation();

  const [inviteeEmail, setInviteeEmail] = useState('');
  const [locale, setLocale] = useState('en-US');
  const [submitted, setSubmitted] = useState(false);
  const currentInvitation = current ?? props.currentInvitation ?? null;

  useEffect(() => {
    if (props.open) {
      setInviteeEmail(currentInvitation?.inviteeEmail ?? '');
      setLocale('en-US');
      setSubmitted(false);
    }
  }, [currentInvitation?.inviteeEmail, props.open]);

  useEffect(() => {
    if (!props.open) return;
    let cancelled = false;
    loadCurrent(props.tenant.id)
      .then((invitation) => {
        if (!cancelled) {
          props.onChanged?.(invitation);
        }
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [loadCurrent, props.open, props.tenant.id]);

  const hasInvitation = Boolean(currentInvitation?.invitationId);
  const emailInvalid =
    !inviteeEmail.trim() || !EMAIL_PATTERN.test(inviteeEmail.trim());

  function handleClose() {
    if (!loading) {
      props.onClose();
    }
  }

  async function handleCreateOrResend() {
    if (loading) return;
    setSubmitted(true);
    if (emailInvalid) return;
    try {
      const payload = {
        locale: locale.trim() || undefined,
      };
      const invitation = hasInvitation
        ? await resend(props.tenant.id, payload)
        : await create(props.tenant.id, {
            inviteeEmail: inviteeEmail.trim(),
            ...payload,
          });
      message.success?.('FIRST_OWNER invitation sent');
      props.onChanged?.(invitation);
    } catch (e) {
      message.error?.(e instanceof Error ? e.message : String(e));
    }
  }

  async function handleRevoke() {
    if (loading || !currentInvitation?.invitationId) return;
    try {
      await revoke(props.tenant.id, currentInvitation.invitationId);
      message.success?.('FIRST_OWNER invitation revoked');
      props.onChanged?.(null);
    } catch (e) {
      message.error?.(e instanceof Error ? e.message : String(e));
    }
  }

  return (
    <Modal
      open={props.open}
      title="FIRST_OWNER invitation"
      onClose={handleClose}
      onConfirm={handleCreateOrResend}
      confirmLoading={loading}
      confirmText={hasInvitation ? 'Resend invitation' : 'Send invitation'}
      cancelText="Close"
      data-testid="platform-tenant-first-owner-dialog"
    >
      <Stack direction="column" style={{ gap: t.space.md }}>
        <Alert severity="info">
          Invitation links are delivered by managed email only.
        </Alert>

        <div style={{ color: t.colors.text.secondary }}>
          {props.tenant.code} — {props.tenant.name}
        </div>

        <Input
          type="email"
          label="Invitee email"
          value={inviteeEmail}
          onChange={(e) => setInviteeEmail(e.target.value)}
          required
          fullWidth
          disabled={hasInvitation || loading}
          error={submitted && emailInvalid}
          helperText={
            submitted && emailInvalid ? 'A valid invitee email is required' : ''
          }
          data-testid="platform-tenant-first-owner-email"
        />

        <Input
          type="text"
          label="Locale"
          value={locale}
          onChange={(e) => setLocale(e.target.value)}
          fullWidth
          data-testid="platform-tenant-first-owner-locale"
        />

        {currentInvitation ? (
          <Alert severity="success" data-testid="platform-tenant-first-owner-state">
            Invitation {currentInvitation.status}; expires at{' '}
            {formatDate(currentInvitation.expiresAt)}
          </Alert>
        ) : null}

        {error ? (
          <Alert severity="error" data-testid="platform-tenant-first-owner-error">
            {error.message}
          </Alert>
        ) : null}

        {hasInvitation ? (
          <Button
            variant="danger"
            onClick={handleRevoke}
            disabled={loading}
            data-testid="platform-tenant-first-owner-revoke"
          >
            Revoke current invitation
          </Button>
        ) : null}
      </Stack>
    </Modal>
  );
}

function formatDate(value: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}
