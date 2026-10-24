/**
 * @file Hosted setup pages
 * @description A FIRST_OWNER invitee sets a password and binds an authenticator app
 * (identity-auth BRX-IA-ONBOARD-002, BRX-IA-CRED-001). Credentials are only ever entered on the
 * hosted pages (root BRX-RS-UI-018, identity-auth IA-2).
 */

import { useEffect, useState, type KeyboardEvent } from 'react';
import { useI18n, useUI } from '@brix-sdk/runtime-sdk-react';
import { HOSTED_LOGIN_NAMESPACE } from '../i18n/messages';
import { useHostedSetup, type HostedSetupViewModel } from '../hooks/useHostedSetup';
import type { HostedSetupRepository } from '../repositories/HostedSetupRepository';
import type { QrCodeRenderer } from '../qrCode';
import { Frame } from './Frame';

const FIRST_OWNER_SETUP = 'TENANT_FIRST_OWNER_SETUP';

export function HostedSetupFlow({ repository, token, qrCode }: {
  readonly repository: HostedSetupRepository;
  readonly token: string | null;
  readonly qrCode: QrCodeRenderer;
}) {
  const model = useHostedSetup(repository, token);
  const { Spin } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  if (model.step === 'loading') {
    return <Frame title={t('setup.title')}><Spin /></Frame>;
  }
  if (model.step === 'setup-password') {
    return <PasswordStep model={model} />;
  }
  if (model.step === 'setup-totp') {
    return <TotpStep model={model} qrCode={qrCode} />;
  }
  if (model.step === 'setup-done') {
    return <DoneStep model={model} />;
  }
  return <InvalidStep />;
}

function PasswordStep({ model }: { readonly model: HostedSetupViewModel }) {
  const { Stack, Typography, Input, Button, Alert } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');

  const submit = async () => {
    if (!password || !confirmation || model.submitting) {
      return;
    }
    const entered = password;
    const repeated = confirmation;
    // Credentials stay in these fields only until they are sent.
    setPassword('');
    setConfirmation('');
    await model.password(entered, repeated);
  };
  const onKeyDown = (event: KeyboardEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      void submit();
    }
  };

  return (
    <Frame title={t('setup.title')} subtitle={t('setup.subtitle')}>
      <Stack spacing={2}>
        {model.email ? <Typography variant="body2" color="textSecondary">{model.email}</Typography> : null}
        {model.error ? <Alert severity="error">{t(`error.${model.error}`, t('error.unavailable'))}</Alert> : null}
        {/* Lets password managers save the new password for this account. */}
        <input type="email" name="username" autoComplete="username" value={model.email ?? ''} readOnly hidden />
        <Input
          type="password"
          name="new-password"
          autoComplete="new-password"
          label={t('setup.password.label')}
          value={password}
          autoFocus
          fullWidth
          required
          maxLength={128}
          disabled={model.submitting}
          onChange={(event) => setPassword(event.target.value)}
          onKeyDown={onKeyDown}
        />
        <Input
          type="password"
          name="new-password-confirmation"
          autoComplete="new-password"
          label={t('setup.password.confirm')}
          value={confirmation}
          fullWidth
          required
          maxLength={128}
          disabled={model.submitting}
          onChange={(event) => setConfirmation(event.target.value)}
          onKeyDown={onKeyDown}
        />
        <Typography variant="body2" color="textSecondary">{t('setup.password.hint')}</Typography>
        <Button variant="primary" fullWidth loading={model.submitting} disabled={!password || !confirmation}
          onClick={() => void submit()}>
          {t('setup.password.submit')}
        </Button>
      </Stack>
    </Frame>
  );
}

function TotpStep({ model, qrCode }: { readonly model: HostedSetupViewModel; readonly qrCode: QrCodeRenderer }) {
  const { Stack, Typography, Input, Button, Alert } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  const [code, setCode] = useState('');
  const [image, setImage] = useState<string | null>(null);
  const secret = manualKey(model.otpauthUri);

  useEffect(() => {
    let active = true;
    setImage(null);
    if (model.otpauthUri) {
      qrCode(model.otpauthUri)
        .then((dataUri) => {
          if (active) {
            setImage(dataUri);
          }
        })
        .catch(() => {
          // The key below works without the image.
        });
    }
    return () => {
      active = false;
    };
  }, [model.otpauthUri, qrCode]);

  const submit = async () => {
    if (!code.trim() || model.submitting) {
      return;
    }
    const entered = code;
    setCode('');
    await model.complete(entered);
  };

  return (
    <Frame title={t('setup.title')} subtitle={t('setup.totp.subtitle')}>
      <Stack spacing={2}>
        <Typography variant="body2">{t('setup.totp.hint')}</Typography>
        {image ? (
          <img src={image} alt={t('setup.totp.qrAlt')} width={192} height={192}
            style={{ alignSelf: 'center', imageRendering: 'pixelated' }} />
        ) : null}
        {secret ? (
          <Stack spacing={0.5}>
            <Typography variant="body2" color="textSecondary">{t('setup.totp.manual')}</Typography>
            <Typography variant="body2" style={{ fontFamily: 'ui-monospace, monospace', wordBreak: 'break-all' }}>
              {secret}
            </Typography>
          </Stack>
        ) : null}
        {model.error ? <Alert severity="error">{t(`error.${model.error}`, t('error.unavailable'))}</Alert> : null}
        <Input
          type="text"
          name="one-time-code"
          autoComplete="one-time-code"
          label={t('setup.totp.label')}
          value={code}
          fullWidth
          required
          maxLength={8}
          disabled={model.submitting}
          onChange={(event) => setCode(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault();
              void submit();
            }
          }}
        />
        <Button variant="primary" fullWidth loading={model.submitting} disabled={!code.trim()} onClick={() => void submit()}>
          {t('setup.totp.submit')}
        </Button>
      </Stack>
    </Frame>
  );
}

function DoneStep({ model }: { readonly model: HostedSetupViewModel }) {
  const { Stack, Typography } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  return (
    <Frame title={t('setup.done.title')}>
      <Stack spacing={2}>
        {model.purpose === FIRST_OWNER_SETUP ? (
          <>
            <Typography variant="body2">{t('setup.done.firstOwner')}</Typography>
            {model.email ? <Typography variant="body2" color="textSecondary">{model.email}</Typography> : null}
          </>
        ) : (
          <Typography variant="body2">{t('setup.done.body')}</Typography>
        )}
      </Stack>
    </Frame>
  );
}

function InvalidStep() {
  const { Stack, Typography } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  return (
    <Frame title={t('setup.invalid.title')}>
      <Stack spacing={2}>
        <Typography variant="body2">{t('setup.invalid.body')}</Typography>
      </Stack>
    </Frame>
  );
}

/** The shared secret of the enrollment in groups of four, for typing it into the app. */
export function manualKey(otpauthUri: string | null): string | null {
  if (!otpauthUri) {
    return null;
  }
  try {
    const secret = new URL(otpauthUri).searchParams.get('secret');
    return secret ? secret.replace(/(.{4})(?=.)/g, '$1 ') : null;
  } catch {
    return null;
  }
}
