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
  const tokenMissing = submitted && !normalizedInvitationToken;
  const canSubmit = !auth.isLoading && !accept.loading && !accept.result;

  const handleAccept = useCallback(async (event?: FormEvent<HTMLFormElement>) => {
    event?.preventDefault();
    setSubmitted(true);
    if (!normalizedInvitationToken) {
      setLocalError('邀请链接缺少必要凭证，请使用最新邮件中的完整链接。');
      return;
    }
    if (!auth.isAuthenticated) {
      setLocalError('请先使用受邀邮箱完成登录，再返回此页面接受邀请。');
      return;
    }
    setLocalError(null);
    await accept.accept(normalizedInvitationToken);
  }, [accept, auth.isAuthenticated, normalizedInvitationToken]);

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
    if (!auth.isLoading && !auth.isAuthenticated) {
      return (
        <Alert severity="warning" data-testid="first-owner-accept-auth-required">
          请先使用受邀账号完成登录，登录后再点击接受邀请。
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
              <StepLine icon="login" text="仅受邀邮箱登录后可以完成接管。" t={t} />
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
                  : '尚未登录受邀账号'}
              </p>
            </div>

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
