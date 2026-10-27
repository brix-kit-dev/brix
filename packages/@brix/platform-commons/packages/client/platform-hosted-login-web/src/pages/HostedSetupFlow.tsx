/**
 * @file Hosted setup pages
 * @description A FIRST_OWNER invitee sets a password and binds an authenticator app
 * (identity-auth BRX-IA-ONBOARD-002, BRX-IA-CRED-001), on a page laid out like the platform
 * administrator setup page (SetupLayout). Credentials are only ever entered on the hosted pages
 * (root BRX-RS-UI-018, identity-auth IA-2).
 */

import { useEffect, useState, type KeyboardEvent } from 'react';
import { useI18n, useUI } from '@brix-sdk/runtime-sdk-react';
import { HOSTED_LOGIN_NAMESPACE } from '../i18n/messages';
import { useHostedSetup, type HostedSetupViewModel } from '../hooks/useHostedSetup';
import type { HostedSetupRepository } from '../repositories/HostedSetupRepository';
import type { QrCodeRenderer } from '../qrCode';
import { hostedLoginTokens as tokens } from '../theme';
import {
  IconBadge,
  RuleBadge,
  SetupPage,
  SetupSteps,
  setupHeadingStyle,
  setupPanelStyle,
  setupSmallTextStyle,
  type SetupStepState,
} from './SetupLayout';

const FIRST_OWNER_SETUP = 'TENANT_FIRST_OWNER_SETUP';

// The live password rules mirror the default security profile (platform-identity PasswordPolicy);
// the server stays the judge and also rejects breached passwords, which the page cannot check.
const MIN_PASSWORD_LENGTH = 8;
const MIN_ACCOUNT_NAME = 4;
const SERVICE_NAME = 'brix';

export function HostedSetupFlow({ repository, token, qrCode }: {
  readonly repository: HostedSetupRepository;
  readonly token: string | null;
  readonly qrCode: QrCodeRenderer;
}) {
  const model = useHostedSetup(repository, token);
  const { Spin } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  // The purpose is known once the link is open; until then the page uses the general title.
  const heading = model.purpose === FIRST_OWNER_SETUP ? 'setup.firstOwner' : 'setup';
  const title = t(`${heading}.title`);
  const subtitle = t(`${heading}.subtitle`);

  if (model.step === 'loading') {
    return (
      <SetupPage title={title} subtitle={subtitle}>
        <div style={{ minHeight: 220, display: 'grid', placeItems: 'center' }}><Spin /></div>
      </SetupPage>
    );
  }
  if (model.step === 'setup-done') {
    return <SetupPage title={title} subtitle={subtitle}><DoneStep model={model} /></SetupPage>;
  }
  if (model.step !== 'setup-password' && model.step !== 'setup-totp') {
    return <SetupPage title={title} subtitle={subtitle}><InvalidStep /></SetupPage>;
  }
  const passwordStep = model.step === 'setup-password';
  const progress: { label: string; state: SetupStepState }[] = [
    { label: t('setup.step.password'), state: passwordStep ? 'current' : 'done' },
    { label: t('setup.step.authenticator'), state: passwordStep ? 'pending' : 'current' },
    { label: t('setup.step.complete'), state: 'pending' },
  ];
  return (
    <SetupPage title={title} subtitle={subtitle}>
      <div style={{ display: 'grid', gap: tokens.space.xl }}>
        <SetupSteps items={progress} label={t('setup.progress')} />
        {model.email ? <AccountRow email={model.email} /> : null}
        {passwordStep ? <PasswordStep model={model} /> : <TotpStep model={model} qrCode={qrCode} />}
      </div>
    </SetupPage>
  );
}

function AccountRow({ email }: { readonly email: string }) {
  const { Icon } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  return (
    <div
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        gap: tokens.space.sm,
        padding: tokens.space.md,
        borderRadius: tokens.shape.md,
        border: `1px solid ${tokens.colors.border.subtle}`,
        background: tokens.colors.surface.elevated,
      }}
    >
      <Icon name="account_circle" size={20} color={tokens.colors.brand.primary} aria-label="" />
      <span style={{ color: tokens.colors.text.secondary }}>{t('setup.account')}</span>
      <strong style={{ minWidth: 0, color: tokens.colors.text.primary, overflowWrap: 'anywhere' }}>{email}</strong>
    </div>
  );
}

function PasswordStep({ model }: { readonly model: HostedSetupViewModel }) {
  const { Input, Button, Alert } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const rules = passwordRules(password, confirmation, model.email);

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
    <div className="hosted-setup-password-grid">
      <div
        className="hosted-setup-panel"
        style={{ ...setupPanelStyle, display: 'grid', gap: tokens.space.md, alignContent: 'center' }}
      >
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
        <div>
          <Button startIcon="arrow_forward" loading={model.submitting} disabled={!password || !confirmation}
            onClick={() => void submit()}>
            {t('setup.password.submit')}
          </Button>
        </div>
      </div>
      <aside
        className="hosted-setup-panel"
        style={{
          ...setupPanelStyle,
          border: `1px solid color-mix(in srgb, ${tokens.colors.brand.primary} 22%, ${tokens.colors.border.default})`,
          background: `color-mix(in srgb, ${tokens.colors.brand.primary} 4%, ${tokens.colors.surface.elevated})`,
          display: 'grid',
          alignContent: 'center',
        }}
      >
        <h2 style={setupHeadingStyle}>{t('setup.rules.title')}</h2>
        <ul style={{ display: 'grid', gap: tokens.space.sm, margin: `${tokens.space.md} 0 0`, padding: 0, listStyle: 'none' }}>
          {rules.map((rule) => (
            <li
              key={rule.key}
              style={{
                ...setupSmallTextStyle,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: tokens.space.sm,
                minWidth: 0,
              }}
            >
              <span style={{ minWidth: 0, overflowWrap: 'break-word' }}>{t(`setup.rules.${rule.key}`)}</span>
              <RuleBadge met={rule.met} />
            </li>
          ))}
        </ul>
        <p style={{ ...setupSmallTextStyle, marginTop: tokens.space.md }}>{t('setup.rules.checkedOnContinue')}</p>
      </aside>
    </div>
  );
}

function TotpStep({ model, qrCode }: { readonly model: HostedSetupViewModel; readonly qrCode: QrCodeRenderer }) {
  const { Input, Button, Alert } = useUI();
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
    <div className="hosted-setup-totp-grid">
      <section className="hosted-setup-panel" style={setupPanelStyle}>
        {image ? (
          <img src={image} alt={t('setup.totp.qrAlt')} width={192} height={192}
            style={{ display: 'block', margin: '0 auto', imageRendering: 'pixelated' }} />
        ) : null}
        {secret ? (
          <div
            style={{
              marginTop: image ? tokens.space.md : 0,
              padding: tokens.space.sm,
              borderRadius: tokens.shape.sm,
              border: `1px solid ${tokens.colors.border.subtle}`,
              background: tokens.colors.surface.page,
            }}
          >
            <p style={setupSmallTextStyle}>{t('setup.totp.manual')}</p>
            <code
              style={{
                display: 'block',
                marginTop: tokens.space.xs,
                color: tokens.colors.text.primary,
                fontSize: tokens.typography.bodySmall.fontSize,
                wordBreak: 'break-all',
              }}
            >
              {secret}
            </code>
          </div>
        ) : null}
      </section>
      <section className="hosted-setup-panel" style={{ minWidth: 0 }}>
        <h2 style={setupHeadingStyle}>{t('setup.totp.title')}</h2>
        <p style={{ ...setupSmallTextStyle, margin: `${tokens.space.md} 0 ${tokens.space.lg}` }}>{t('setup.totp.hint')}</p>
        <div style={{ display: 'grid', gap: tokens.space.md }}>
          {model.error ? <Alert severity="error">{t(`error.${model.error}`, t('error.unavailable'))}</Alert> : null}
          <Input
            type="text"
            name="one-time-code"
            autoComplete="one-time-code"
            label={t('setup.totp.label')}
            helperText={t('setup.totp.help')}
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
        </div>
        <div style={{ marginTop: tokens.space.xl }}>
          <Button loading={model.submitting} disabled={!code.trim()} onClick={() => void submit()}>
            {t('setup.totp.submit')}
          </Button>
        </div>
      </section>
    </div>
  );
}

function DoneStep({ model }: { readonly model: HostedSetupViewModel }) {
  const { Icon } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  const firstOwner = model.purpose === FIRST_OWNER_SETUP;
  const bodyStyle = {
    margin: `${tokens.space.sm} 0 0`,
    color: tokens.colors.text.secondary,
    lineHeight: tokens.typography.bodyMedium.lineHeight,
  };
  return (
    <section className="hosted-setup-panel" style={{ ...setupPanelStyle, display: 'grid', gap: tokens.space.lg }}>
      <div>
        <h2 style={{ ...setupHeadingStyle, display: 'flex', alignItems: 'center', gap: tokens.space.sm }}>
          <Icon name="check_circle" size={24} color={tokens.colors.brand.primary} aria-label="" />
          {t('setup.done.title')}
        </h2>
        <p style={bodyStyle}>{t('setup.done.body')}</p>
        {firstOwner ? null : <p style={bodyStyle}>{t('setup.done.signIn')}</p>}
      </div>
      {firstOwner ? (
        // Nothing to click here: the invitation link exists only in the email (BRX-SA-NOTIFY-001).
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '24px minmax(0, 1fr)',
            gap: tokens.space.md,
            alignItems: 'start',
            padding: tokens.space.md,
            borderRadius: tokens.shape.md,
            border: `1px solid color-mix(in srgb, ${tokens.colors.brand.primary} 22%, ${tokens.colors.border.default})`,
            background: `color-mix(in srgb, ${tokens.colors.brand.primary} 4%, ${tokens.colors.surface.elevated})`,
          }}
        >
          <Icon name="mail" size={24} color={tokens.colors.brand.primary} aria-label="" />
          <div style={{ minWidth: 0 }}>
            <h3 style={{ ...setupHeadingStyle, fontSize: tokens.typography.bodySmall.fontSize }}>{t('setup.done.nextTitle')}</h3>
            {model.email ? (
              <p style={{ ...setupSmallTextStyle, marginTop: tokens.space.xs }}>
                <strong style={{ color: tokens.colors.text.primary, overflowWrap: 'anywhere' }}>{model.email}</strong>
              </p>
            ) : null}
            <p style={{ ...setupSmallTextStyle, marginTop: tokens.space.xs }}>{t('setup.done.nextBody')}</p>
          </div>
        </div>
      ) : null}
    </section>
  );
}

function InvalidStep() {
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  const error = tokens.colors.status.error;
  return (
    <div
      role="alert"
      style={{
        minHeight: 220,
        display: 'grid',
        gridTemplateColumns: '56px minmax(0, 1fr)',
        gap: tokens.space.md,
        alignItems: 'start',
        padding: tokens.space.lg,
        borderRadius: tokens.shape.md,
        border: `1px solid color-mix(in srgb, ${error} 30%, ${tokens.colors.border.default})`,
        background: `color-mix(in srgb, ${error} 6%, ${tokens.colors.surface.elevated})`,
      }}
    >
      <IconBadge icon="link_off" tone={tokens.colors.status.info} />
      <div style={{ minWidth: 0 }}>
        <h2 style={{ ...setupHeadingStyle, fontWeight: 750 }}>{t('setup.invalid.title')}</h2>
        <p style={{ ...setupSmallTextStyle, marginTop: tokens.space.sm }}>{t('setup.invalid.body')}</p>
      </div>
    </div>
  );
}

/** The password rules the page can check while typing; `met` turns their badges to "OK". */
export function passwordRules(password: string, confirmation: string, email: string | null) {
  const lower = password.toLowerCase();
  const name = email ? localPart(email).trim().toLowerCase() : '';
  const containsName = (name.length >= MIN_ACCOUNT_NAME && lower.includes(name)) || lower.includes(SERVICE_NAME);
  return [
    // Counted in code points, as the server counts them.
    { key: 'length', met: Array.from(password).length >= MIN_PASSWORD_LENGTH },
    { key: 'match', met: password.length > 0 && password === confirmation },
    { key: 'name', met: password.length > 0 && !containsName },
  ] as const;
}

function localPart(email: string): string {
  const at = email.indexOf('@');
  return at > 0 ? email.slice(0, at) : email;
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
