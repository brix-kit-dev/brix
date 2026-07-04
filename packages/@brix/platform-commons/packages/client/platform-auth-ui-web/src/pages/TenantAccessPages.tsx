/**
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file TenantAccessPages
 * @description Phase 3 Actor/Subject login and context selection pages.
 */

import React, { useCallback, useMemo, useState } from 'react';
import { useTheme, useUIOptional } from '@brix-sdk/runtime-sdk-react';
import type {
  DesignTokens,
  LoginResult,
  TenantOption,
} from '@brix-sdk/runtime-sdk-api-web';
import { TenantSelector } from '../components/TenantSelector';
import type { LoginFormData } from '../components/LoginForm';
import { useTenantLoginFlow } from '../hooks/useTenantLoginFlow';

export interface TenantAccessPageConfig {
  readonly apiBaseUrl?: string;
  readonly completePath?: string;
  readonly changePasswordPath?: string;
  readonly onComplete?: (result: LoginResult) => void | Promise<void>;
}

export const TENANT_ACCESS_ROUTES = {
  ACTOR_LOGIN: '/login/actor',
  SUBJECT_LOGIN: '/login/subject',
  ACTOR_CONTEXT: '/login/actor/context',
  INVITATION_ACCEPT: '/invitations/accept',
} as const;

interface CredentialFormProps {
  readonly title: string;
  readonly subtitle: string;
  readonly submitLabel: string;
  readonly loading: boolean;
  readonly error: string | null;
  readonly onSubmit: (data: LoginFormData) => Promise<void>;
}

function useAuthPageStyles() {
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  return useMemo(() => ({
    page: {
      minHeight: '100vh',
      display: 'grid',
      placeItems: 'center',
      padding: t.space.lg,
      background: t.colors.surface.page,
      fontFamily: t.typography.fontFamily,
    } as React.CSSProperties,
    panel: {
      width: '100%',
      maxWidth: 460,
      borderRadius: t.shape.md,
      border: `1px solid ${t.colors.border.default}`,
      background: t.colors.surface.elevated,
      padding: t.space.lg,
      boxShadow: t.shadows.sm,
    } as React.CSSProperties,
    title: {
      margin: 0,
      color: t.colors.text.primary,
      fontSize: t.typography.titleMedium.fontSize,
      lineHeight: t.typography.titleMedium.lineHeight,
      fontWeight: 750,
    } as React.CSSProperties,
    subtitle: {
      margin: `${t.space.xs} 0 ${t.space.lg}`,
      color: t.colors.text.secondary,
      lineHeight: t.typography.bodyMedium.lineHeight,
    } as React.CSSProperties,
    fields: {
      display: 'grid',
      gap: t.space.md,
    } as React.CSSProperties,
    error: {
      padding: `${t.space.xs} ${t.space.sm}`,
      borderRadius: t.shape.sm,
      border: `1px solid color-mix(in srgb, ${t.colors.status.error} 32%, ${t.colors.border.default})`,
      background: `color-mix(in srgb, ${t.colors.status.error} 8%, ${t.colors.surface.elevated})`,
      color: t.colors.status.error,
      fontWeight: 600,
    } as React.CSSProperties,
    input: {
      width: '100%',
      minHeight: 40,
      borderRadius: t.shape.sm,
      border: `1px solid ${t.colors.border.default}`,
      padding: `0 ${t.space.sm}`,
      color: t.colors.text.primary,
      background: t.colors.surface.elevated,
    } as React.CSSProperties,
    label: {
      display: 'grid',
      gap: t.space.xs,
      color: t.colors.text.secondary,
      fontSize: t.typography.label.fontSize,
      fontWeight: 650,
    } as React.CSSProperties,
    button: {
      minHeight: 42,
      borderRadius: t.shape.sm,
      border: 0,
      padding: `0 ${t.space.md}`,
      color: t.colors.brand.primaryContrast,
      background: t.colors.brand.primary,
      fontWeight: 700,
      cursor: 'pointer',
    } as React.CSSProperties,
    secondary: {
      minHeight: 38,
      borderRadius: t.shape.sm,
      border: `1px solid ${t.colors.border.default}`,
      padding: `0 ${t.space.md}`,
      color: t.colors.text.primary,
      background: t.colors.surface.elevated,
      fontWeight: 650,
      cursor: 'pointer',
    } as React.CSSProperties,
  }), [t]);
}

function CredentialForm(props: CredentialFormProps): JSX.Element {
  const ui = useUIOptional();
  const styles = useAuthPageStyles();
  const [form, setForm] = useState<LoginFormData>({
    username: '',
    password: '',
    rememberMe: false,
  });

  const update = useCallback(
    (field: keyof LoginFormData, value: string | boolean) => {
      setForm((current) => ({ ...current, [field]: value }));
    },
    [],
  );

  const submit = useCallback(
    async (event: React.FormEvent) => {
      event.preventDefault();
      if (!form.username.trim() || !form.password) return;
      await props.onSubmit(form);
    },
    [form, props],
  );

  return (
    <form style={styles.fields} onSubmit={submit} aria-busy={props.loading}>
      <div>
        <h1 style={styles.title}>{props.title}</h1>
        <p style={styles.subtitle}>{props.subtitle}</p>
      </div>
      {props.error ? (
        <div role="alert" style={styles.error}>{props.error}</div>
      ) : null}
      {ui ? (
        <>
          <ui.Input
            label="账号"
            name="username"
            value={form.username}
            onChange={(event) => update('username', event.target.value)}
            disabled={props.loading}
            autoFocus
            fullWidth
          />
          <ui.Input
            label="密码"
            name="password"
            type="password"
            value={form.password}
            onChange={(event) => update('password', event.target.value)}
            disabled={props.loading}
            fullWidth
          />
          <ui.Checkbox
            checked={form.rememberMe}
            onChange={(event) => update('rememberMe', event.target.checked)}
            disabled={props.loading}
          >
            保持登录
          </ui.Checkbox>
          <ui.Button
            type="submit"
            variant="primary"
            loading={props.loading}
            disabled={props.loading || !form.username.trim() || !form.password}
            fullWidth
          >
            {props.submitLabel}
          </ui.Button>
        </>
      ) : (
        <>
          <label style={styles.label}>
            账号
            <input
              style={styles.input}
              value={form.username}
              onChange={(event) => update('username', event.target.value)}
              disabled={props.loading}
              autoComplete="username"
            />
          </label>
          <label style={styles.label}>
            密码
            <input
              style={styles.input}
              type="password"
              value={form.password}
              onChange={(event) => update('password', event.target.value)}
              disabled={props.loading}
              autoComplete="current-password"
            />
          </label>
          <label style={{ ...styles.label, display: 'flex', gap: 8 }}>
            <input
              type="checkbox"
              checked={form.rememberMe}
              onChange={(event) => update('rememberMe', event.target.checked)}
              disabled={props.loading}
            />
            保持登录
          </label>
          <button
            type="submit"
            style={styles.button}
            disabled={props.loading || !form.username.trim() || !form.password}
          >
            {props.loading ? '处理中...' : props.submitLabel}
          </button>
        </>
      )}
    </form>
  );
}

function AuthPanel(props: { readonly children: React.ReactNode }): JSX.Element {
  const styles = useAuthPageStyles();
  return (
    <main style={styles.page}>
      <section style={styles.panel}>{props.children}</section>
    </main>
  );
}

export function ActorLoginPage(config: TenantAccessPageConfig = {}): JSX.Element {
  const flow = useTenantLoginFlow({ ...config, track: 'actor' });
  return (
    <AuthPanel>
      {flow.step === 'SELECT_TENANT' ? (
        <ActorContextSelectorPage
          options={flow.tenantOptions}
          loading={flow.loading}
          error={flow.error}
          onSelect={flow.selectOption}
          onBack={flow.reset}
        />
      ) : (
        <CredentialForm
          title="B 端登录"
          subtitle="使用组织成员账号登录，登录后选择本次访问上下文。"
          submitLabel="继续"
          loading={flow.loading}
          error={flow.error}
          onSubmit={flow.submitCredentials}
        />
      )}
    </AuthPanel>
  );
}

export function SubjectLoginPage(config: TenantAccessPageConfig = {}): JSX.Element {
  const flow = useTenantLoginFlow({ ...config, track: 'subject' });
  return (
    <AuthPanel>
      {flow.step === 'SELECT_TENANT' ? (
        <SubjectNoTenantState
          title="当前租户未开放此账号"
          message="C 端登录不会展示跨租户上下文选择。请确认入口租户或联系租户管理员完成准入。"
          onRetry={flow.reset}
        />
      ) : (
        <CredentialForm
          title="C 端登录"
          subtitle="使用当前租户的客户或访客账号登录。"
          submitLabel="登录"
          loading={flow.loading}
          error={flow.error}
          onSubmit={flow.submitCredentials}
        />
      )}
    </AuthPanel>
  );
}

export interface ActorContextSelectorPageProps {
  readonly options: readonly TenantOption[];
  readonly loading?: boolean;
  readonly error?: string | null;
  readonly onSelect: (option: TenantOption) => Promise<void> | void;
  readonly onBack?: () => void;
}

export function ActorContextSelectorPage(
  props: ActorContextSelectorPageProps,
): JSX.Element {
  const styles = useAuthPageStyles();
  const actorOptions = props.options.filter((option) => option.roleType === 'actor');
  return (
    <div>
      <TenantSelector
        options={actorOptions}
        loading={props.loading}
        error={props.error}
        onSelect={(_, option) => void props.onSelect(option)}
        onSelectContext={(_, option) => void props.onSelect(option)}
        labels={{
          title: '选择访问上下文',
          subtitle: '请选择本次要进入的组织成员上下文。',
          emptyHint: '当前账号没有可用的 B 端上下文。',
        }}
      />
      {props.onBack ? (
        <button type="button" style={{ ...styles.secondary, marginTop: 16 }} onClick={props.onBack}>
          返回重新登录
        </button>
      ) : null}
    </div>
  );
}

export interface SubjectNoTenantStateProps {
  readonly title?: string;
  readonly message?: string;
  readonly onRetry?: () => void;
}

export function SubjectNoTenantState(props: SubjectNoTenantStateProps): JSX.Element {
  const styles = useAuthPageStyles();
  return (
    <div role="status">
      <h1 style={styles.title}>{props.title ?? '无可用租户'}</h1>
      <p style={styles.subtitle}>
        {props.message ?? '当前账号没有可访问的 Subject 上下文。'}
      </p>
      {props.onRetry ? (
        <button type="button" style={styles.secondary} onClick={props.onRetry}>
          重新登录
        </button>
      ) : null}
    </div>
  );
}

export interface InvitationAcceptPageProps {
  readonly token: string;
  readonly loading?: boolean;
  readonly error?: string | null;
  readonly onAccept: (token: string) => Promise<void> | void;
}

export function InvitationAcceptPage(props: InvitationAcceptPageProps): JSX.Element {
  const styles = useAuthPageStyles();
  const [submitting, setSubmitting] = useState(false);
  const accept = useCallback(async () => {
    setSubmitting(true);
    try {
      await props.onAccept(props.token);
    } finally {
      setSubmitting(false);
    }
  }, [props]);

  return (
    <AuthPanel>
      <h1 style={styles.title}>接受租户邀请</h1>
      <p style={styles.subtitle}>确认后将按邀请范围加入对应租户。</p>
      {props.error ? <div role="alert" style={styles.error}>{props.error}</div> : null}
      <button
        type="button"
        style={styles.button}
        disabled={props.loading || submitting || !props.token}
        onClick={accept}
      >
        {props.loading || submitting ? '处理中...' : '接受邀请'}
      </button>
    </AuthPanel>
  );
}
