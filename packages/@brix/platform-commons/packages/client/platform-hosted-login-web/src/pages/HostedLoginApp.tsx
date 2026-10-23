/**
 * @file Hosted login pages
 * @description Identifier, password and TOTP steps of the staff login, plus the result and
 * error pages. Credentials are only ever entered here (root BRX-RS-UI-018, identity-auth IA-2).
 */

import { useMemo, useState, type KeyboardEvent, type ReactNode } from 'react';
import { useI18n, useUI } from '@brix-sdk/runtime-sdk-react';
import { HOSTED_LOGIN_NAMESPACE } from '../i18n/messages';
import { useHostedLogin, type HostedLoginViewModel } from '../hooks/useHostedLogin';
import type { HostedLoginRepository } from '../repositories/HostedLoginRepository';

export interface HostedLoginAppProps {
  readonly repository: HostedLoginRepository;
  readonly location: Pick<Location, 'pathname' | 'search' | 'origin'>;
  readonly navigate: (url: string) => void;
}

const RESULT_STEPS = new Set(['no-tenant', 'multiple-tenants', 'password-change-required', 'mfa-setup-required']);
const LOGIN_ERRORS = new Set(['login_expired', 'token_exchange_failed', 'context_unavailable', 'access_denied']);

export function HostedLoginApp({ repository, location, navigate }: HostedLoginAppProps) {
  if (location.pathname.startsWith('/auth/error')) {
    const reason = new URLSearchParams(location.search).get('reason') ?? '';
    return <LoginErrorPage reason={LOGIN_ERRORS.has(reason) ? reason : 'default'} />;
  }
  if (location.pathname.startsWith('/auth/signed-out')) {
    return <SignedOutPage />;
  }
  return <LoginFlow repository={repository} navigate={navigate} origin={location.origin} />;
}

function LoginFlow({ repository, navigate, origin }: {
  readonly repository: HostedLoginRepository;
  readonly navigate: (url: string) => void;
  readonly origin: string;
}) {
  const model = useHostedLogin(repository, navigate, origin);
  const { Spin } = useUI();
  if (model.step === 'loading' || model.step === 'done') {
    return <Frame><Spin /></Frame>;
  }
  if (RESULT_STEPS.has(model.step)) {
    return <ResultStep model={model} />;
  }
  return <CredentialStep model={model} />;
}

function CredentialStep({ model }: { readonly model: HostedLoginViewModel }) {
  const { Stack, Typography, Input, Button, Alert } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  const [value, setValue] = useState('');
  const step = model.step as 'identify' | 'password' | 'totp';
  const field = useMemo(() => ({
    identify: { type: 'email' as const, autoComplete: 'username', name: 'username' },
    password: { type: 'password' as const, autoComplete: 'current-password', name: 'password' },
    totp: { type: 'text' as const, autoComplete: 'one-time-code', name: 'one-time-code' },
  })[step], [step]);

  const submit = async () => {
    if (!value.trim() || model.submitting) {
      return;
    }
    const entered = value;
    if (step !== 'identify') {
      // Credentials stay in this field only until they are sent.
      setValue('');
    }
    if (step === 'identify') {
      await model.identify(entered);
      setValue('');
    } else if (step === 'password') {
      await model.password(entered);
    } else {
      await model.totp(entered);
    }
  };
  const onKeyDown = (event: KeyboardEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      void submit();
    }
  };

  return (
    <Frame>
      <Stack spacing={2}>
        {model.identifier && step !== 'identify' ? (
          <Typography variant="body2" color="secondary">{model.identifier}</Typography>
        ) : null}
        {step === 'totp' ? <Typography variant="body2">{t('totp.hint')}</Typography> : null}
        {model.error ? <Alert severity="error">{t(`error.${model.error}`, t('error.unavailable'))}</Alert> : null}
        <Input
          key={step}
          type={field.type}
          name={field.name}
          autoComplete={field.autoComplete}
          label={t(`${step}.label`)}
          value={value}
          autoFocus
          fullWidth
          required
          maxLength={step === 'totp' ? 8 : 254}
          disabled={model.submitting}
          onChange={(event) => setValue(event.target.value)}
          onKeyDown={onKeyDown}
        />
        <Button variant="primary" fullWidth loading={model.submitting} disabled={!value.trim()} onClick={() => void submit()}>
          {t(`${step}.submit`)}
        </Button>
        {step !== 'identify' ? (
          <Button variant="text" fullWidth disabled={model.submitting} onClick={() => void model.restart()}>
            {t('account.switch')}
          </Button>
        ) : null}
      </Stack>
    </Frame>
  );
}

function ResultStep({ model }: { readonly model: HostedLoginViewModel }) {
  const { Stack, Typography, Button } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  return (
    <Frame>
      <Stack spacing={2}>
        <Typography variant="h6">{t(`result.${model.step}.title`)}</Typography>
        <Typography variant="body2">{t(`result.${model.step}.body`)}</Typography>
        <Button variant="secondary" fullWidth disabled={model.submitting} onClick={() => void model.signOut()}>
          {t('account.switch')}
        </Button>
      </Stack>
    </Frame>
  );
}

function LoginErrorPage({ reason }: { readonly reason: string }) {
  const { Stack, Typography, Button } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  return (
    <Frame title={t('loginError.title')}>
      <Stack spacing={2}>
        <Typography variant="body2">{t(`loginError.${reason}`)}</Typography>
        <Button variant="primary" fullWidth onClick={() => window.location.assign('/bff/login')}>
          {t('loginError.retry')}
        </Button>
      </Stack>
    </Frame>
  );
}

function SignedOutPage() {
  const { Stack, Typography } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  return (
    <Frame title={t('signedOut.title')}>
      <Stack spacing={2}>
        <Typography variant="body2">{t('signedOut.body')}</Typography>
      </Stack>
    </Frame>
  );
}

function Frame({ title, children }: { readonly title?: string; readonly children: ReactNode }) {
  const { Paper, Stack, Typography } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  return (
    <Paper elevation={1} style={{ width: '100%', maxWidth: 400, padding: 32 }}>
      <Stack spacing={3}>
        <Stack spacing={0.5}>
          <Typography variant="h5" component="h1">{title ?? t('title')}</Typography>
          {title ? null : <Typography variant="body2" color="secondary">{t('subtitle')}</Typography>}
        </Stack>
        {children}
      </Stack>
    </Paper>
  );
}
