/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useI18n, useTheme } from "@brix-sdk/runtime-sdk-react";
import type { DesignTokens } from "@brix-sdk/runtime-sdk-api-web";
import { useUIStrict, AdminPageShell, PageHeader } from "../internal/ui-kit";
import { usePlatformLoginTotp } from "../hooks/usePlatformLoginTotp";
import { I18N_KEYS, I18N_NAMESPACE, makeT } from "../i18n";
import { PLATFORM_ADMIN_ROUTES } from "../constants";
import type { PlatformLoginTotpResponse } from "../types";

interface LoginTotpRouteState {
  mfaChallengeToken?: string;
  loginId?: string;
}

export interface PlatformLoginTotpPageProps {
  onLoginSuccess?: (res: PlatformLoginTotpResponse, loginId: string) => void;
}

export function PlatformLoginTotpPage(
  props: PlatformLoginTotpPageProps,
): JSX.Element {
  const { Card, Input, Button, message } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);
  const navigate = useNavigate();
  const location = useLocation();
  const routeState = (location.state ?? {}) as LoginTotpRouteState;
  const { loginTotp, loading, error } = usePlatformLoginTotp();
  const [code, setCode] = useState("");
  const [submitted, setSubmitted] = useState(false);

  const challenge = routeState.mfaChallengeToken?.trim();
  const loginId = routeState.loginId?.trim() ?? "";
  const codeInvalid = !/^\d{6}$/.test(code);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitted(true);
    if (!challenge || codeInvalid) return;
    try {
      const res = await loginTotp({
        mfaChallengeToken: challenge,
        totpCode: code,
      });
      props.onLoginSuccess?.(res, loginId);
      setCode("");
      navigate(PLATFORM_ADMIN_ROUTES.DASHBOARD, { replace: true });
    } catch (err) {
      message.error?.(err instanceof Error ? err.message : String(err));
    }
  }

  if (!challenge) {
    return <Navigate to={PLATFORM_ADMIN_ROUTES.LOGIN} replace />;
  }

  return (
    <AdminPageShell maxWidth={900}>
      <PageHeader
        title={tt(I18N_KEYS.loginTotp.title)}
        subtitle={tt(I18N_KEYS.loginTotp.subtitle)}
      />
      <Card
        style={{
          padding: t.space.xl,
          border: `1px solid ${t.colors.border.subtle}`,
          boxShadow: t.shadows.none,
        }}
      >
        <div
          className="platform-login-totp-grid"
          style={{
            display: "grid",
            gridTemplateColumns: "minmax(0, 1fr) 320px",
            gap: t.space.xl,
            alignItems: "stretch",
          }}
        >
          <style>
            {`
              @media (max-width: ${t.breakpoints.md}px) {
                .platform-login-totp-grid { grid-template-columns: 1fr !important; }
              }
            `}
          </style>
          <form
            data-testid="platform-login-totp-form"
            onSubmit={handleSubmit}
            noValidate
            style={{
              minWidth: 0,
              boxSizing: "border-box",
              padding: t.space.lg,
              borderRadius: t.shape.md,
              border: `1px solid ${t.colors.border.subtle}`,
              background: t.colors.surface.elevated,
              display: "grid",
              gap: t.space.lg,
              alignContent: "center",
            }}
          >
            <Input
              type="tel"
              label={tt(I18N_KEYS.loginTotp.code)}
              placeholder={tt(I18N_KEYS.loginTotp.codePlaceholder)}
              value={code}
              onChange={(e) =>
                setCode(e.target.value.replace(/\D/g, "").slice(0, 6))
              }
              required
              autoFocus
              fullWidth
              autoComplete="one-time-code"
              maxLength={6}
              error={submitted && codeInvalid}
              helperText={
                submitted && codeInvalid
                  ? tt(I18N_KEYS.loginTotp.invalidCode)
                  : tt(I18N_KEYS.loginTotp.codeHelp)
              }
              disabled={loading}
              data-testid="platform-login-totp-code"
            />
            {error ? (
              <div
                role="alert"
                style={{
                  marginTop: t.space.md,
                  padding: t.space.sm,
                  borderRadius: t.shape.sm,
                  border: `1px solid ${t.colors.status.error}`,
                  color: t.colors.status.error,
                  background: `color-mix(in srgb, ${t.colors.status.error} 8%, transparent)`,
                }}
              >
                {error.message}
              </div>
            ) : null}
            <div style={{ display: "flex", gap: t.space.sm, flexWrap: "wrap" }}>
              <Button
                type="submit"
                loading={loading}
                disabled={loading}
                data-testid="platform-login-totp-submit"
              >
                {tt(I18N_KEYS.loginTotp.submit)}
              </Button>
              <Button
                type="button"
                variant="secondary"
                disabled={loading}
                onClick={() =>
                  navigate(PLATFORM_ADMIN_ROUTES.LOGIN, { replace: true })
                }
              >
                {tt(I18N_KEYS.loginTotp.backToLogin)}
              </Button>
            </div>
          </form>
          <aside
            style={{
              padding: t.space.lg,
              borderRadius: t.shape.md,
              border: `1px solid color-mix(in srgb, ${t.colors.brand.primary} 20%, ${t.colors.border.default})`,
              background: `color-mix(in srgb, ${t.colors.brand.primary} 4%, ${t.colors.surface.elevated})`,
              display: "grid",
              alignContent: "center",
            }}
          >
            <h2
              style={{
                margin: 0,
                color: t.colors.text.primary,
                fontSize: t.typography.titleSmall.fontSize,
                lineHeight: t.typography.titleSmall.lineHeight,
              }}
            >
              {tt(I18N_KEYS.loginTotp.helpTitle)}
            </h2>
            <div
              style={{
                display: "grid",
                gap: t.space.sm,
                marginTop: t.space.md,
              }}
            >
              {[
                tt(I18N_KEYS.loginTotp.helpBound),
                tt(I18N_KEYS.loginTotp.helpRefresh),
              ].map((text) => (
                <div
                  key={text}
                  style={{
                    display: "flex",
                    gap: t.space.sm,
                    alignItems: "flex-start",
                  }}
                >
                  <span
                    aria-hidden="true"
                    style={{
                      width: 8,
                      height: 8,
                      marginTop: 7,
                      borderRadius: t.shape.full,
                      background: t.colors.brand.primary,
                      flex: "0 0 auto",
                    }}
                  />
                  <span
                    style={{
                      color: t.colors.text.secondary,
                      fontSize: t.typography.bodySmall.fontSize,
                      lineHeight: t.typography.bodySmall.lineHeight,
                    }}
                  >
                    {text}
                  </span>
                </div>
              ))}
            </div>
            <div
              style={{
                marginTop: t.space.md,
                padding: t.space.md,
                borderRadius: t.shape.sm,
                border: `1px solid ${t.colors.border.subtle}`,
                color: t.colors.text.secondary,
                background: t.colors.surface.elevated,
                fontSize: t.typography.bodySmall.fontSize,
                lineHeight: t.typography.bodySmall.lineHeight,
              }}
            >
              {tt(I18N_KEYS.loginTotp.noQrHelp)}
            </div>
          </aside>
        </div>
      </Card>
    </AdminPageShell>
  );
}
