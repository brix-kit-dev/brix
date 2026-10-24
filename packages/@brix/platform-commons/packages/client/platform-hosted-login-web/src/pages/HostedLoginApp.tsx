/**
 * @file Hosted login pages
 * @description Identifier, password and TOTP steps of the staff login, the same steps for a
 * FIRST_OWNER invitation link, the setup of an invitee's account, plus the result and error
 * pages. Credentials are only ever entered here (root BRX-RS-UI-018, identity-auth IA-2).
 */

import { useMemo, useState, type KeyboardEvent } from 'react';
import { useI18n, useUI } from '@brix-sdk/runtime-sdk-react';
import { HOSTED_LOGIN_NAMESPACE } from '../i18n/messages';
import { useHostedLogin, type HostedLoginViewModel, type InvitationMode } from '../hooks/useHostedLogin';
import type { HostedLoginRepository } from '../repositories/HostedLoginRepository';
import type { HostedSetupRepository } from '../repositories/HostedSetupRepository';
import { renderQrCode, type QrCodeRenderer } from '../qrCode';
import { Frame } from './Frame';
import { HostedSetupFlow } from './HostedSetupFlow';

export interface HostedLoginAppProps {
  readonly repository: HostedLoginRepository;
  readonly setupRepository: HostedSetupRepository;
  readonly location: Pick<Location, 'pathname' | 'search' | 'origin'>;
  readonly navigate: (url: string) => void;
  /** One-time token of a setup or invitation link, already removed from the address bar. */
  readonly linkToken?: string | null;
  readonly qrCode?: QrCodeRenderer;
}

const RESULT_STEPS = new Set([
  'no-tenant', 'password-change-required', 'mfa-setup-required', 'invitation-setup-required',
]);
const LOGIN_ERRORS = new Set(['login_expired', 'token_exchange_failed', 'context_unavailable', 'access_denied']);
const INVITATION_ERRORS = new Set([
  'invitation_invalid', 'invitation_taken', 'invitation_quota', 'invitation_mismatch', 'too_many_attempts',
]);

export function HostedLoginApp({
  repository, setupRepository, location, navigate, linkToken = null, qrCode = renderQrCode,
}: HostedLoginAppProps) {
  if (location.pathname.startsWith('/auth/error')) {
    const reason = new URLSearchParams(location.search).get('reason') ?? '';
    return <LoginErrorPage reason={LOGIN_ERRORS.has(reason) ? reason : 'default'} />;
  }
  if (location.pathname.startsWith('/auth/signed-out')) {
    return <SignedOutPage />;
  }
  if (location.pathname.startsWith('/auth/setup')) {
    return <HostedSetupFlow repository={setupRepository} token={linkToken} qrCode={qrCode} />;
  }
  if (location.pathname.startsWith('/auth/invitation')) {
    return <LoginFlow repository={repository} navigate={navigate} origin={location.origin}
      invitation={{ token: linkToken }} />;
  }
  return <LoginFlow repository={repository} navigate={navigate} origin={location.origin} />;
}

function LoginFlow({ repository, navigate, origin, invitation }: {
  readonly repository: HostedLoginRepository;
  readonly navigate: (url: string) => void;
  readonly origin: string;
  readonly invitation?: InvitationMode;
}) {
  const model = useHostedLogin(repository, navigate, origin, invitation);
  const { Spin } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  if (model.step === 'loading' || model.step === 'done') {
    return <Frame title={invitation ? t('invitation.title') : undefined}><Spin /></Frame>;
  }
  if (model.step === 'invitation-invalid') {
    return <InvitationErrorPage reason={INVITATION_ERRORS.has(model.error ?? '') ? model.error ?? '' : 'default'} />;
  }
  if (RESULT_STEPS.has(model.step)) {
    return <ResultStep model={model} />;
  }
  if (model.step === 'select-tenant') {
    return <SelectTenantStep model={model} />;
  }
  return <CredentialStep model={model} />;
}

function SelectTenantStep({ model }: { readonly model: HostedLoginViewModel }) {
  const { Stack, Typography, Button, Alert } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  return (
    <Frame>
      <Stack spacing={2}>
        {model.identifier ? <Typography variant="body2" color="textSecondary">{model.identifier}</Typography> : null}
        <Typography variant="subtitle1">{t('select.title')}</Typography>
        {model.error ? <Alert severity="error">{t(`error.${model.error}`, t('error.unavailable'))}</Alert> : null}
        {model.options.map((option) => (
          <Button
            key={option.ticket}
            variant="secondary"
            fullWidth
            disabled={model.submitting}
            onClick={() => void model.select(option.ticket)}
          >
            {option.tenantName}（{t(`memberType.${option.memberType}`, option.memberType)}）
          </Button>
        ))}
        <Button variant="text" fullWidth disabled={model.submitting} onClick={() => void model.signOut()}>
          {t('account.switch')}
        </Button>
      </Stack>
    </Frame>
  );
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
    <Frame title={model.invitation ? t('invitation.title') : undefined}
      subtitle={model.invitation ? t('invitation.subtitle') : undefined}>
      <Stack spacing={2}>
        {model.invitation ? <Typography variant="subtitle1">{model.invitation.tenantName}</Typography> : null}
        {model.identifier && step !== 'identify' ? (
          <Typography variant="body2" color="textSecondary">{model.identifier}</Typography>
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
        {/* An invitation is for the invited email only, so there is no other account to use. */}
        {step !== 'identify' && !model.invitation ? (
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
    <Frame title={model.invitation ? t('invitation.title') : undefined}>
      <Stack spacing={2}>
        <Typography variant="h6">{t(`result.${model.step}.title`)}</Typography>
        {model.invitation ? <Typography variant="body2" color="textSecondary">{model.invitation.inviteeEmail}</Typography> : null}
        <Typography variant="body2">{t(`result.${model.step}.body`)}</Typography>
        {model.invitation ? null : (
          <Button variant="secondary" fullWidth disabled={model.submitting} onClick={() => void model.signOut()}>
            {t('account.switch')}
          </Button>
        )}
      </Stack>
    </Frame>
  );
}

function InvitationErrorPage({ reason }: { readonly reason: string }) {
  const { Stack, Typography } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  return (
    <Frame title={t('invitationError.title')}>
      <Stack spacing={2}>
        <Typography variant="body2">{t(`invitationError.${reason}`)}</Typography>
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
