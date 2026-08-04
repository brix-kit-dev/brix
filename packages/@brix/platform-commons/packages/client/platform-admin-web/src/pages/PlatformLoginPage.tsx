/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file PlatformLoginPage — Platform Super-Admin entry point.
 *
 * Routing contract: password verification only. On success the page routes to
 * the TOTP step with the MFA challenge carried in router state.
 */

import { useState, type CSSProperties, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTheme, useI18n } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import { useUIStrict } from '../internal/ui-kit';
import { usePlatformLogin } from '../hooks/usePlatformLogin';
import { I18N_KEYS, I18N_NAMESPACE, makeT } from '../i18n';
import { PLATFORM_ADMIN_ROUTES } from '../constants';
import shinwaLogoUrl from '../assets/shinwa.png';

export interface PlatformLoginPageProps {}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function PlatformLoginPage(_props: PlatformLoginPageProps): JSX.Element {
  const { Button, Input, Card, Icon } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);
  const navigate = useNavigate();
  const { login, loading, error } = usePlatformLogin();

  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const trimmedLoginId = loginId.trim();
  const loginIdMissing = !trimmedLoginId;
  const loginIdInvalid = !!trimmedLoginId && !EMAIL_RE.test(trimmedLoginId);
  const usernameError = submitted && (loginIdMissing || loginIdInvalid);
  const passwordError = submitted && !password;
  const loginTitleId = 'platform-login-title';
  const loginSubtitleId = 'platform-login-subtitle';
  const taglineParts = tt(I18N_KEYS.login.tagline).split('\n');
  const passwordToggleLabel = showPassword
    ? tt(I18N_KEYS.login.hidePassword)
    : tt(I18N_KEYS.login.showPassword);

  const formFieldStyle: CSSProperties = { marginTop: 14 };
  const loginAccent = t.colors.brand.primary;
  const surfacePage = t.colors.surface.page;
  const surfaceCard = t.colors.surface.card;
  const textPrimary = t.colors.text.primary;
  const textSecondary = t.colors.text.secondary;
  const borderSubtle = t.colors.border.subtle;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitted(true);
    if (loginIdMissing || loginIdInvalid || !password) return;

    try {
      setSubmitError(null);
      const res = await login({ loginId: trimmedLoginId, password });
      if (!res.mfaChallengeToken) {
        setSubmitError(tt(I18N_KEYS.login.mfaChallengeMissing));
        return;
      }
      navigate(PLATFORM_ADMIN_ROUTES.LOGIN_TOTP, {
        replace: true,
        state: {
          mfaChallengeToken: res.mfaChallengeToken,
          loginId: trimmedLoginId,
        },
      });
    } catch {
      // error already captured by hook; surface via inline alert below
    }
  }

  return (
    <main
      className="platform-login-shell"
      style={{
        minHeight: '100vh',
        height: '100vh',
        position: 'relative',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'hidden',
        background: `linear-gradient(118deg, ${surfacePage} 0%, color-mix(in srgb, ${loginAccent} 6%, ${surfacePage}) 34%, color-mix(in srgb, ${loginAccent} 14%, ${surfacePage}) 62%, color-mix(in srgb, ${loginAccent} 30%, ${surfacePage}) 100%)`,
        backgroundSize: '120% 120%',
        padding: 'clamp(16px, 4vh, 32px) clamp(16px, 4vw, 32px)',
        boxSizing: 'border-box',
        fontFamily: t.typography.fontFamily,
      }}
    >
      <style>
        {`
          .platform-login-shell * { box-sizing: border-box; }
          .platform-login-stage {
            width: 100%;
            max-width: 1260px;
            display: grid;
            grid-template-columns: minmax(600px, 1fr) minmax(360px, 428px);
            gap: 64px;
            align-items: center;
            position: relative;
            z-index: 1;
          }
          .platform-login-brand-title {
            font-size: 3rem;
            line-height: 1.14;
          }
          .platform-login-card {
            justify-self: end;
          }
          @media (max-width: 1080px) {
            .platform-login-shell {
              padding: 24px;
            }
            .platform-login-stage {
              max-width: 428px;
              grid-template-columns: 1fr;
              gap: 0;
            }
            .platform-login-brand {
              display: none !important;
            }
            .platform-login-card {
              justify-self: center;
            }
          }
          @media (max-width: 640px) {
            .platform-login-shell {
              padding: 20px 16px;
              align-items: center;
            }
            .platform-login-card {
              padding: 34px 22px 26px !important;
            }
            .platform-login-title {
              font-size: 1.85rem !important;
            }
            .platform-login-footer {
              flex-direction: column;
              gap: 10px !important;
            }
          }
        `}
      </style>
      <NetworkBackdrop color={loginAccent} surfaceColor={surfaceCard} />
      <div
        className="platform-login-stage"
        style={{
          color: textPrimary,
        }}
      >
        <section
          className="platform-login-brand"
          aria-label={tt(I18N_KEYS.login.productName)}
          style={{
            minHeight: 400,
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
            gap: t.space.lg,
            paddingLeft: t.space.sm,
            maxWidth: 620,
          }}
        >
          <div>
            <div
              style={{
                color: loginAccent,
                fontSize: t.typography.titleSmall.fontSize,
                fontWeight: 600,
                lineHeight: t.typography.titleSmall.lineHeight,
                textTransform: 'uppercase',
              }}
            >
              {tt(I18N_KEYS.login.brandLabel)}
            </div>
            <div
              aria-hidden="true"
              style={{
                width: 48,
                height: 4,
                borderRadius: t.shape.full,
                background: loginAccent,
                marginTop: t.space.sm,
                marginBottom: t.space.lg,
              }}
            />
            <h1
              className="platform-login-brand-title"
              style={{
                margin: 0,
                color: textPrimary,
                fontWeight: 600,
              }}
            >
              {taglineParts.map((part) => (
                <span key={part} style={{ display: 'block' }}>
                  {part}
                </span>
              ))}
            </h1>
            <p
              style={{
                margin: `${t.space.md} 0 0`,
                color: textSecondary,
                fontSize: '1.125rem',
                fontWeight: 500,
                lineHeight: 1.5,
              }}
            >
              {tt(I18N_KEYS.login.features)}
              <br />
              {tt(I18N_KEYS.login.consoleName)}
            </p>
            <p
              style={{
                margin: `${t.space.md} 0 0`,
                maxWidth: 500,
                color: textSecondary,
                fontSize: t.typography.bodyLarge?.fontSize ?? '1rem',
                fontWeight: t.typography.bodyMedium.fontWeight,
                lineHeight: 1.7,
              }}
            >
              {tt(I18N_KEYS.login.description)}
            </p>
          </div>
        </section>

        <Card
          className="platform-login-card"
          style={{
            width: '100%',
            maxWidth: 428,
            minHeight: 482,
            padding: '38px 28px 28px',
            border: `1px solid ${borderSubtle}`,
            borderRadius: t.shape.md,
            background: surfaceCard,
            boxShadow: t.shadows.xl,
            boxSizing: 'border-box',
          }}
        >
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              textAlign: 'center',
            }}
          >
            <div
              style={{
                width: 52,
                height: 52,
                display: 'grid',
                placeItems: 'center',
                borderRadius: t.shape.full,
                background: `color-mix(in srgb, ${loginAccent} 10%, transparent)`,
                border: `1px solid color-mix(in srgb, ${loginAccent} 16%, transparent)`,
                color: loginAccent,
                marginBottom: 16,
              }}
            >
              <Icon name="security" size={24} color={loginAccent} />
            </div>
            <h1
              id={loginTitleId}
              className="platform-login-title"
              style={{
                margin: 0,
                color: textPrimary,
                fontSize: '1.75rem',
                fontWeight: 700,
                lineHeight: 1.18,
              }}
            >
              {tt(I18N_KEYS.login.title)}
            </h1>
            <p
              id={loginSubtitleId}
              style={{
                color: textSecondary,
                margin: `6px 0 0`,
                fontSize: t.typography.bodyLarge?.fontSize ?? '1rem',
                fontWeight: 500,
                lineHeight: 1.5,
              }}
            >
              {tt(I18N_KEYS.login.subtitle)}
            </p>
          </div>

          <form
            data-testid="login-form"
            aria-labelledby={loginTitleId}
            aria-describedby={loginSubtitleId}
            onSubmit={handleSubmit}
            noValidate
            style={{ marginTop: 24 }}
          >
            <div style={{ marginTop: 0 }}>
              <Input
                type="email"
                name="loginId"
                label={tt(I18N_KEYS.login.username)}
                placeholder={tt(I18N_KEYS.login.usernamePlaceholder)}
                value={loginId}
                onChange={(e) => setLoginId(e.target.value)}
                required
                autoFocus
                fullWidth
                size="large"
                startAdornment="person"
                error={usernameError}
                helperText={
                  usernameError
                    ? tt(loginIdMissing
                        ? I18N_KEYS.login.requiredUsername
                        : I18N_KEYS.login.invalidEmail)
                    : ''
                }
                autoComplete="email"
                maxLength={64}
                disabled={loading}
                data-testid="login-username"
              />
            </div>
            <div style={{ ...formFieldStyle, position: 'relative' }}>
              <Input
                type={showPassword ? 'text' : 'password'}
                name="password"
                label={tt(I18N_KEYS.login.password)}
                placeholder={tt(I18N_KEYS.login.passwordPlaceholder)}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                fullWidth
                size="large"
                startAdornment="lock"
                error={passwordError}
                helperText={
                  passwordError ? tt(I18N_KEYS.login.requiredPassword) : ''
                }
                autoComplete="current-password"
                disabled={loading}
                data-testid="login-password"
              />
              <button
                type="button"
                aria-label={passwordToggleLabel}
                title={passwordToggleLabel}
                onClick={() => setShowPassword((value) => !value)}
                disabled={loading}
                style={{
                  position: 'absolute',
                  right: 10,
                  top: 2,
                  width: 36,
                  height: 36,
                  display: 'grid',
                  placeItems: 'center',
                  border: 0,
                  borderRadius: t.shape.full,
                  background: 'transparent',
                  color: textSecondary,
                  cursor: loading ? 'default' : 'pointer',
                  padding: 0,
                }}
              >
                <Icon
                  name={showPassword ? 'visibility_off' : 'visibility'}
                  size={22}
                  color="currentColor"
                  aria-label={passwordToggleLabel}
                />
              </button>
            </div>

            {error || submitError ? (
              <div
                role="alert"
                style={{
                  marginTop: t.space.md,
                  padding: t.space.sm,
                  borderRadius: t.shape.sm,
                  border: `1px solid ${t.colors.status.error}`,
                  background: `color-mix(in srgb, ${t.colors.status.error} 8%, transparent)`,
                  color: t.colors.status.error,
                  fontSize: t.typography.bodySmall.fontSize,
                  fontWeight: t.typography.bodySmall.fontWeight,
                  lineHeight: t.typography.bodySmall.lineHeight,
                }}
              >
                {submitError ?? error?.message ?? tt(I18N_KEYS.login.invalidCreds)}
              </div>
            ) : null}

            <div style={{ marginTop: 22 }}>
              <Button
                type="submit"
                fullWidth
                size="large"
                loading={loading}
                data-testid="login-submit"
                style={{
                  minHeight: 50,
                  borderRadius: t.shape.md,
                  background: loginAccent,
                  boxShadow: `0 12px 24px color-mix(in srgb, ${loginAccent} 28%, transparent)`,
                  fontSize: t.typography.bodyLarge?.fontSize ?? '1rem',
                  fontWeight: 700,
                  textTransform: 'none',
                }}
              >
                {tt(I18N_KEYS.login.submit)}
              </Button>
            </div>
          </form>

          <div
            className="platform-login-footer"
            style={{
              marginTop: 26,
              paddingTop: 18,
              borderTop: `1px solid ${borderSubtle}`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: t.space.md,
            }}
          >
            <span
              style={{
                color: textSecondary,
                fontSize: t.typography.labelSmall.fontSize,
                fontWeight: 500,
                lineHeight: t.typography.labelSmall.lineHeight,
              }}
            >
              {tt(I18N_KEYS.login.poweredBy)}
            </span>
            <img
              src={shinwaLogoUrl}
              alt="SHINWA"
              style={{
                width: 116,
                height: 32,
                objectFit: 'contain',
                display: 'block',
              }}
            />
          </div>
        </Card>
      </div>
    </main>
  );
}

function NetworkBackdrop(props: { color: string; surfaceColor: string }): JSX.Element {
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      width="100%"
      height="100%"
      viewBox="0 0 1600 900"
      preserveAspectRatio="xMidYMid slice"
      style={{
        position: 'absolute',
        inset: 0,
        width: '100%',
        height: '100%',
        pointerEvents: 'none',
      }}
    >
      <defs>
        <symbol id="platform-login-cube" viewBox="0 0 220 260">
          <path
            d="M20 74 110 22l90 52v104l-90 52-90-52V74Zm90-52v104M20 74l90 52 90-52M110 126v104"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.4"
            strokeLinejoin="round"
          />
          <circle cx="20" cy="74" r="7" fill="currentColor" />
          <circle cx="110" cy="22" r="7" fill="currentColor" />
          <circle cx="200" cy="74" r="7" fill="currentColor" />
          <circle cx="110" cy="126" r="7" fill="currentColor" />
          <circle cx="20" cy="178" r="6" fill="currentColor" />
          <circle cx="110" cy="230" r="6" fill="currentColor" />
        </symbol>
      </defs>
      <g color={props.color} opacity="0.14">
        <use href="#platform-login-cube" x="-60" y="44" width="310" height="366" />
      </g>
      <g color={props.color} opacity="0.16">
        <use href="#platform-login-cube" x="298" y="630" width="270" height="319" />
      </g>
      <g color={props.surfaceColor} opacity="0.62">
        <use href="#platform-login-cube" x="806" y="520" width="330" height="390" />
      </g>
      <g color={props.color} opacity="0.12">
        <path
          d="M120 260 260 178M122 458 266 392M510 756 698 648"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.2"
          strokeLinecap="round"
        />
        <circle cx="260" cy="178" r="6" fill="currentColor" />
        <circle cx="266" cy="392" r="6" fill="currentColor" />
        <circle cx="510" cy="756" r="6" fill="currentColor" />
      </g>
    </svg>
  );
}
