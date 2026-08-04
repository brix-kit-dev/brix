/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { useAuth, useTheme, useUI } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import { useAcceptFirstOwnerInvitation } from '../hooks/useAcceptFirstOwnerInvitation';
import { useNoReferrerPolicy } from '../hooks/useNoReferrerPolicy';

export function FirstOwnerInvitationPage(): JSX.Element {
  useNoReferrerPolicy();
  const initialInvitationToken = useInvitationTokenFromLocation();
  const auth = useAuth();
  const { Button, Card, Alert, Icon, Input } = useUI();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const accept = useAcceptFirstOwnerInvitation();
  const [invitationToken, setInvitationToken] = useState(initialInvitationToken);
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const surfaceCard = t.colors.surface.card ?? '#ffffff';
  const pageFontFamily = [
    '"Noto Sans CJK SC"',
    '"Source Han Sans SC"',
    '"PingFang SC"',
    '"Microsoft YaHei"',
    '"WenQuanYi Micro Hei"',
    t.typography.fontFamily,
    'sans-serif',
  ].join(', ');

  const normalizedInvitationToken = invitationToken.trim();
  const normalizedLoginId = loginId.trim();
  const tokenMissing = submitted && !normalizedInvitationToken;
  const inviteeLoginRequired = !auth.isLoading && !auth.isAuthenticated;
  const loginIdMissing = submitted && inviteeLoginRequired && !normalizedLoginId;
  const passwordMissing = submitted && inviteeLoginRequired && !password;
  const canSubmit = !auth.isLoading && !accept.loading && !accept.result;

  const handleAccept = useCallback(async (event?: FormEvent<HTMLFormElement>) => {
    event?.preventDefault();
    setSubmitted(true);
    if (!normalizedInvitationToken) {
      setLocalError('邀请链接缺少必要凭证，请使用最新邮件中的完整链接。');
      return;
    }
    if (inviteeLoginRequired && (!normalizedLoginId || !password)) {
      setLocalError('请输入受邀邮箱/用户名和密码，然后接受并激活租户。');
      return;
    }
    setLocalError(null);
    try {
      if (inviteeLoginRequired) {
        await accept.acceptWithInviteeLogin(normalizedInvitationToken, {
          loginId: normalizedLoginId,
          password,
        });
        setPassword('');
        return;
      }
      await accept.accept(normalizedInvitationToken);
    } catch (cause) {
      setLocalError(toFirstOwnerErrorMessage(cause));
    }
  }, [
    accept,
    inviteeLoginRequired,
    normalizedInvitationToken,
    normalizedLoginId,
    password,
  ]);

  const statusAlert = useMemo(() => {
    if (accept.result) {
      return (
        <Alert severity="success" data-testid="first-owner-accept-success">
          租户已激活，当前状态：{accept.result.tenantStatus || 'ACTIVE'}。
        </Alert>
      );
    }
    if (localError || accept.error) {
      return (
        <Alert severity="error" data-testid="first-owner-accept-error">
          {localError ?? '接受邀请失败，请确认邀请未过期且当前登录账号与受邀邮箱一致。'}
        </Alert>
      );
    }
    if (inviteeLoginRequired) {
      return (
        <Alert severity="info" data-testid="first-owner-accept-auth-required">
          请使用受邀邮箱/用户名完成身份校验；系统会在本页直接接受邀请并激活租户。
        </Alert>
      );
    }
    if (!normalizedInvitationToken) {
      return (
        <Alert severity="error" data-testid="first-owner-accept-missing-token">
          邀请链接无效，请粘贴最新邮件中的邀请凭证。
        </Alert>
      );
    }
    return (
      <Alert severity="info" data-testid="first-owner-accept-ready">
        此操作会将当前登录身份设为租户首位 OWNER，并激活待接管租户。
      </Alert>
    );
  }, [
    accept.error,
    accept.result,
    auth.isAuthenticated,
    auth.isLoading,
    inviteeLoginRequired,
    localError,
    normalizedInvitationToken,
  ]);

  return (
    <main
      style={{
        minHeight: '100%',
        display: 'grid',
        placeItems: 'center',
        padding: `clamp(20px, 4vw, 48px) ${t.space.lg}`,
        background: `linear-gradient(145deg, ${t.colors.surface.page} 0%, color-mix(in srgb, ${t.colors.brand.primary} 8%, ${t.colors.surface.page}) 48%, ${t.colors.surface.page} 100%)`,
        fontFamily: pageFontFamily,
        boxSizing: 'border-box',
      }}
    >
      <Card
        style={{
          width: 'min(100%, 920px)',
          borderRadius: t.shape.md,
          border: `1px solid ${t.colors.border.subtle ?? t.colors.border.default}`,
          boxShadow: t.shadows.lg ?? t.shadows.md,
          background: surfaceCard,
          overflow: 'hidden',
        }}
      >
        <div
          className="first-owner-accept-grid"
          style={{
            display: 'grid',
            gridTemplateColumns: 'minmax(0, 0.9fr) minmax(320px, 1.1fr)',
            gap: 0,
          }}
        >
          <div
            style={{
              display: 'grid',
              alignContent: 'space-between',
              gap: t.space.xl,
              padding: t.space.xl,
              background: `color-mix(in srgb, ${t.colors.brand.primary} 10%, ${surfaceCard})`,
              borderRight: `1px solid ${t.colors.border.subtle ?? t.colors.border.default}`,
            }}
          >
            <div>
              <span
                aria-hidden="true"
                style={{
                  width: 44,
                  height: 44,
                  display: 'inline-grid',
                  placeItems: 'center',
                  borderRadius: t.shape.md,
                  color: t.colors.brand.primary,
                  background: surfaceCard,
                  boxShadow: t.shadows.sm,
                }}
              >
                <Icon name="user" size={22} />
              </span>
              <h1
                style={{
                  margin: `${t.space.lg} 0 0`,
                  color: t.colors.text.primary,
                  fontSize: t.typography.titleLarge.fontSize,
                  lineHeight: t.typography.titleLarge.lineHeight,
                  fontWeight: 700,
                }}
              >
                接受租户所有者邀请
              </h1>
              <p
                style={{
                  margin: `${t.space.md} 0 0`,
                  color: t.colors.text.secondary,
                  fontSize: t.typography.bodyMedium.fontSize,
                  lineHeight: 1.7,
                }}
              >
                该操作会以当前已验证的 Actor 身份接管待激活租户，并创建首位 OWNER。
              </p>
            </div>
            <div
              style={{
                display: 'grid',
                gap: t.space.sm,
                color: t.colors.text.secondary,
                fontSize: t.typography.bodySmall?.fontSize ?? t.typography.bodyMedium.fontSize,
                lineHeight: 1.6,
              }}
            >
              <StepLine icon="identity" text="邀请凭证只保存在当前页面内存中。" t={t} />
              <StepLine icon="login" text="仅受邀身份校验通过后可以完成接管。" t={t} />
              <StepLine icon="success" text="接受成功后，邀请会进入不可重复使用状态。" t={t} />
            </div>
          </div>

          <form
            onSubmit={(event) => {
              void handleAccept(event);
            }}
            style={{
              display: 'grid',
              gap: t.space.lg,
              padding: t.space.xl,
            }}
          >
            <div>
              <p
                style={{
                  margin: 0,
                  color: t.colors.text.secondary,
                  fontSize: t.typography.bodySmall?.fontSize ?? t.typography.bodyMedium.fontSize,
                  lineHeight: 1.6,
                }}
              >
                登录状态
              </p>
              <p
                style={{
                  margin: `${t.space.xs} 0 0`,
                  color: t.colors.text.primary,
                  fontSize: t.typography.titleMedium?.fontSize ?? t.typography.titleLarge.fontSize,
                  lineHeight: t.typography.titleMedium?.lineHeight ?? t.typography.titleLarge.lineHeight,
                  fontWeight: 650,
                }}
              >
                {auth.isAuthenticated
                  ? auth.user?.email ?? auth.user?.username ?? '已验证身份'
                  : '请输入受邀账号信息'}
              </p>
            </div>

            {inviteeLoginRequired ? (
              <div
                style={{
                  display: 'grid',
                  gap: t.space.md,
                }}
              >
                <Input
                  label="受邀邮箱 / 用户名"
                  type="text"
                  value={loginId}
                  placeholder="输入收到邀请邮件的账号"
                  required
                  fullWidth
                  autoComplete="username"
                  startAdornment="identity"
                  error={loginIdMissing}
                  helperText={
                    loginIdMissing
                      ? '请输入受邀邮箱或用户名。'
                      : '必须与邀请中的受邀身份一致。'
                  }
                  disabled={accept.loading || Boolean(accept.result)}
                  data-testid="first-owner-login-id-input"
                  onChange={(event) => {
                    setLoginId(event.target.value);
                    setLocalError(null);
                    accept.resetError();
                  }}
                />

                <Input
                  label="密码"
                  type="password"
                  value={password}
                  placeholder="输入受邀账号密码"
                  required
                  fullWidth
                  autoComplete="current-password"
                  startAdornment="lock"
                  error={passwordMissing}
                  helperText={
                    passwordMissing
                      ? '请输入密码。'
                      : '密码仅用于本次身份校验，不会保存在页面中。'
                  }
                  disabled={accept.loading || Boolean(accept.result)}
                  data-testid="first-owner-password-input"
                  onChange={(event) => {
                    setPassword(event.target.value);
                    setLocalError(null);
                    accept.resetError();
                  }}
                />
              </div>
            ) : null}

            <Input
              label="邀请凭证"
              type="password"
              value={invitationToken}
              placeholder="粘贴邮件链接中的 token 参数"
              required
              fullWidth
              autoComplete="off"
              startAdornment="identity"
              error={tokenMissing}
              helperText={
                tokenMissing
                  ? '请输入邀请凭证。'
                  : '从邮件链接进入时会自动读取；地址栏中的 token 会立即清除。'
              }
              disabled={accept.loading || Boolean(accept.result)}
              data-testid="first-owner-invitation-token-input"
              onChange={(event) => {
                setInvitationToken(event.target.value);
                setLocalError(null);
                accept.resetError();
              }}
            />

            {statusAlert}

            <div
              style={{
                display: 'flex',
                justifyContent: 'flex-end',
                gap: t.space.sm,
              }}
            >
              <Button
                variant="primary"
                type="submit"
                loading={accept.loading}
                disabled={!canSubmit}
                startIcon={accept.result ? 'check' : 'check'}
              >
                {accept.result ? '已完成' : '接受并激活'}
              </Button>
            </div>
          </form>
        </div>
      </Card>
      <style>
        {`
          @media (max-width: 760px) {
            .first-owner-accept-grid {
              grid-template-columns: 1fr !important;
            }
            .first-owner-accept-grid > div:first-child {
              border-right: 0 !important;
              border-bottom: 1px solid ${t.colors.border.subtle ?? t.colors.border.default} !important;
            }
          }
        `}
      </style>
    </main>
  );
}

function StepLine({
  icon,
  text,
  t,
}: {
  readonly icon: string;
  readonly text: string;
  readonly t: DesignTokens;
}): JSX.Element {
  const { Icon } = useUI();
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '20px 1fr',
        gap: t.space.sm,
        alignItems: 'start',
      }}
    >
      <Icon name={icon} size={16} color={t.colors.brand.primary} />
      <span>{text}</span>
    </div>
  );
}

function useInvitationTokenFromLocation(): string {
  const [token] = useState(() => readInvitationToken());

  useEffect(() => {
    if (!token || typeof window === 'undefined') return;
    const url = new URL(window.location.href);
    if (!url.searchParams.has('token')) return;
    url.searchParams.delete('token');
    window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`);
  }, [token]);

  return token;
}

function readInvitationToken(): string {
  if (typeof window === 'undefined') {
    return '';
  }
  return new URL(window.location.href).searchParams.get('token')?.trim() ?? '';
}

function toFirstOwnerErrorMessage(cause: unknown): string {
  const code = cause instanceof Error ? cause.message : String(cause);
  if (code === 'FIRST_OWNER_INVITATION_TOKEN_REQUIRED') {
    return '邀请链接缺少必要凭证，请使用最新邮件中的完整链接。';
  }
  if (code === 'FIRST_OWNER_INVITEE_CREDENTIALS_REQUIRED') {
    return '请输入受邀邮箱/用户名和密码，然后接受并激活租户。';
  }
  if (code === 'FIRST_OWNER_INVITEE_LOGIN_INVALID') {
    return '受邀账号或密码不正确。请使用收到邀请邮件的邮箱/用户名，并输入该账号当前密码。';
  }
  if (code === 'FIRST_OWNER_INVITEE_PENDING_SETUP') {
    return '受邀账号尚未完成初始设置。请先打开 setup 邮件完成密码和 MFA 设置，再返回邀请链接。';
  }
  if (code === 'FIRST_OWNER_INVITEE_ACCOUNT_LOCKED') {
    return '受邀账号已被锁定，暂时不能接受 FIRST_OWNER 邀请。请先联系平台管理员处理账号状态。';
  }
  if (code === 'FIRST_OWNER_INVITEE_ACCOUNT_DISABLED') {
    return '受邀账号已停用，不能接受 FIRST_OWNER 邀请。请确认受邀身份状态后重试。';
  }
  if (code === 'FIRST_OWNER_IDENTITY_TOKEN_REQUIRED') {
    return '当前账号已进入租户上下文，无法用于 FIRST_OWNER 接受。请使用受邀邮箱的激活/登录链路重新进入。';
  }
  return '接受邀请失败，请确认邀请未过期，且账号与受邀邮箱一致。';
}
