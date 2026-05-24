/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file CreateTenantDialog — platform super-admin tenant creation dialog.
 *
 * Creates a new tenant in PENDING_ACTIVATION status via
 * POST /api/platform/tenants. The tenant can be activated afterwards via
 * UpdateTenantStatusDialog.
 *
 * Architecture rules (SSOT §11):
 *  - All UI via useUIStrict()          (R-1, R-2)
 *  - All tokens via useTheme().tokens  (R-6)
 *  - All strings via makeT(useI18n())  (R-5)
 *  - No direct UI library imports      (R-3)
 */

import { useState } from 'react';
import { useI18n, useTheme } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import { useUIStrict } from '../internal/ui-kit';
import { useCreatePlatformTenant } from '../hooks/useCreatePlatformTenant';
import { I18N_KEYS, I18N_NAMESPACE, makeT } from '../i18n';

/** Regex mirror of backend CreatePlatformTenantRequest validation. */
const CODE_PATTERN = /^[a-z][a-z0-9]*(-[a-z0-9]+)*$/;

export interface CreateTenantDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated?: () => void;
}

export function CreateTenantDialog(
  props: CreateTenantDialogProps,
): JSX.Element {
  const { Modal, Box, Stack, Input, Typography, Alert, Icon, message } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);
  const { create, loading, error } = useCreatePlatformTenant();

  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [submitted, setSubmitted] = useState(false);

  // Client-side validation
  const codeBlank = !code.trim();
  const codePatternInvalid =
    !codeBlank && (code.length < 2 || code.length > 64 || !CODE_PATTERN.test(code));
  const codeInvalid = codeBlank || codePatternInvalid;
  const nameInvalid = !name.trim();

  function reset() {
    setCode('');
    setName('');
    setSubmitted(false);
  }

  function handleClose() {
    if (!loading) {
      reset();
      props.onClose();
    }
  }

  async function handleConfirm() {
    if (loading) return;
    setSubmitted(true);
    if (codeInvalid || nameInvalid) return;
    try {
      await create({ code: code.trim(), name: name.trim() });
      message.success?.(tt(I18N_KEYS.tenant.createSuccess));
      reset();
      props.onCreated?.();
    } catch (e) {
      message.error?.(e instanceof Error ? e.message : tt(I18N_KEYS.tenant.createFailed));
    }
  }

  function codeHelperText(): string {
    if (submitted && codeBlank) return tt(I18N_KEYS.tenant.codeRequired);
    if (submitted && codePatternInvalid) return tt(I18N_KEYS.tenant.codeInvalid);
    return tt(I18N_KEYS.tenant.fieldCodeHelper);
  }

  return (
    <Modal
      open={props.open}
      title={tt(I18N_KEYS.tenant.createDialogTitle)}
      onClose={handleClose}
      onConfirm={handleConfirm}
      confirmLoading={loading}
      confirmText={tt(I18N_KEYS.common.confirm)}
      showCancel={false}
      data-testid="platform-tenant-create-dialog"
    >
      <Stack direction="column" style={{ gap: t.space.md }}>
        <Box
          style={{
            display: 'flex',
            gap: t.space.md,
            padding: t.space.md,
            borderRadius: t.shape.md,
            border: `1px solid ${t.colors.border.subtle}`,
            background: t.colors.surface.elevated,
          }}
        >
          <Box
            style={{
              width: t.space.xxl,
              height: t.space.xxl,
              borderRadius: t.shape.md,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
              background: t.colors.brand.primary,
              color: t.colors.brand.primaryContrast,
            }}
          >
            <Icon
              name="domain_add"
              size="large"
              color={t.colors.brand.primaryContrast}
              aria-label={tt(I18N_KEYS.tenant.createIntroIconLabel)}
            />
          </Box>
          <Box style={{ minWidth: 0 }}>
            <Typography variant="subtitle2" style={{ fontWeight: 700 }}>
              {tt(I18N_KEYS.tenant.createIntroTitle)}
            </Typography>
            <Typography
              variant="body2"
              color="textSecondary"
              style={{ marginTop: t.space.xs }}
            >
              {tt(I18N_KEYS.tenant.createIntroBody)}
            </Typography>
          </Box>
        </Box>

        <Alert severity="info">
          {tt(I18N_KEYS.tenant.createPendingActivationNote)}
        </Alert>

        <Stack direction="column" style={{ gap: t.space.sm }}>
          <Input
            type="text"
            label={tt(I18N_KEYS.tenant.fieldCode)}
            value={code}
            onChange={(e) => setCode(e.target.value.toLowerCase())}
            required
            fullWidth
            error={submitted && codeInvalid}
            helperText={codeHelperText()}
            data-testid="platform-tenant-create-code"
          />
          <Input
            type="text"
            label={tt(I18N_KEYS.tenant.fieldName)}
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            fullWidth
            error={submitted && nameInvalid}
            helperText={
              submitted && nameInvalid
                ? tt(I18N_KEYS.tenant.nameRequired)
                : tt(I18N_KEYS.tenant.fieldNameHelper)
            }
            data-testid="platform-tenant-create-name"
          />
        </Stack>

        {error ? (
          <Alert severity="error" data-testid="platform-tenant-create-error">
            {error.message}
          </Alert>
        ) : null}
      </Stack>
    </Modal>
  );
}
