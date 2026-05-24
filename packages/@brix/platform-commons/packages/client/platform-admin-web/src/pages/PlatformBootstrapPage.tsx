/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useI18n, useTheme } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import { AdminPageShell, PageHeader, useUIStrict } from '../internal/ui-kit';
import { usePlatformBootstrap } from '../hooks/usePlatformBootstrap';
import { PLATFORM_ADMIN_ROUTES } from '../constants';
import { I18N_KEYS, I18N_NAMESPACE, makeT } from '../i18n';

const USERNAME_RE = /^[A-Za-z0-9._-]{3,64}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function PlatformBootstrapPage(): JSX.Element {
  const { Card, Input, Button, message } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);
  const navigate = useNavigate();
  const bootstrap = usePlatformBootstrap(false);
  const [setupCode, setSetupCode] = useState('');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const setupCodeMissing = !setupCode.trim();
  const usernameInvalid = !USERNAME_RE.test(username);
  const emailInvalid = !EMAIL_RE.test(email);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitted(true);
    if (setupCodeMissing || usernameInvalid || emailInvalid) return;
    try {
      const res = await bootstrap.createFirstAdmin({
        setupCode: setupCode.trim(),
        username,
        email,
        displayName: displayName.trim() || undefined,
      });
      if (!res.setupLinkSent) {
        throw new Error(tt(I18N_KEYS.admin.setupLinkPending));
      }
      setSetupCode('');
      setUsername('');
      setEmail('');
      setDisplayName('');
      navigate(PLATFORM_ADMIN_ROUTES.BOOTSTRAP_SENT, { replace: true });
    } catch (err) {
      message.error?.(err instanceof Error ? err.message : String(err));
    }
  }

  return (
    <AdminPageShell maxWidth={900}>
      <PageHeader title={tt(I18N_KEYS.bootstrap.title)} subtitle={tt(I18N_KEYS.bootstrap.subtitle)} />
      <Card
        style={{
          padding: t.space.xl,
          border: `1px solid ${t.colors.border.subtle}`,
          boxShadow: t.shadows.none,
        }}
      >
        <form data-testid="platform-bootstrap-form" onSubmit={handleSubmit} noValidate>
          <div style={{ display: 'grid', gap: t.space.md }}>
            <Input
              type="password"
              label={tt(I18N_KEYS.bootstrap.setupCode)}
              value={setupCode}
              onChange={(e) => setSetupCode(e.target.value)}
              required
              fullWidth
              autoComplete="one-time-code"
              error={submitted && setupCodeMissing}
              helperText={submitted && setupCodeMissing ? tt(I18N_KEYS.admin.requiredField) : ''}
              disabled={bootstrap.loading}
              data-testid="platform-bootstrap-setup-code"
            />
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 260px), 1fr))', gap: t.space.md }}>
              <Input
                type="text"
                label={tt(I18N_KEYS.bootstrap.username)}
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                fullWidth
                maxLength={64}
                autoComplete="off"
                error={submitted && usernameInvalid}
                helperText={submitted && usernameInvalid ? tt(I18N_KEYS.admin.invalidUsername) : ''}
                disabled={bootstrap.loading}
                data-testid="platform-bootstrap-username"
              />
              <Input
                type="email"
                label={tt(I18N_KEYS.bootstrap.email)}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                fullWidth
                autoComplete="off"
                error={submitted && emailInvalid}
                helperText={submitted && emailInvalid ? tt(I18N_KEYS.admin.invalidEmail) : ''}
                disabled={bootstrap.loading}
                data-testid="platform-bootstrap-email"
              />
            </div>
            <Input
              type="text"
              label={tt(I18N_KEYS.bootstrap.displayName)}
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              fullWidth
              maxLength={120}
              autoComplete="off"
              disabled={bootstrap.loading}
              data-testid="platform-bootstrap-display-name"
            />
          </div>
          {bootstrap.error ? (
            <div role="alert" style={{ marginTop: t.space.md, color: t.colors.status.error }}>
              {bootstrap.error.message}
            </div>
          ) : null}
          <div style={{ marginTop: t.space.xl }}>
            <Button type="submit" loading={bootstrap.loading} disabled={bootstrap.loading} data-testid="platform-bootstrap-submit">
              {tt(I18N_KEYS.bootstrap.submit)}
            </Button>
          </div>
        </form>
      </Card>
    </AdminPageShell>
  );
}