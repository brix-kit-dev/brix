/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file ChangeOwnPasswordPage — self-service password change.
 *
 * After success the host MUST drop the current token (the old hash is no
 * longer valid server-side) — we surface this via {@link onPasswordChanged}.
 */

import { useState, type FormEvent } from "react";
import { useI18n, useTheme } from "@brix-sdk/runtime-sdk-react";
import type { DesignTokens } from "@brix-sdk/runtime-sdk-api-web";
import {
  useUIStrict,
  AdminPageShell,
  PageHeader,
  StatusBadge,
} from "../internal/ui-kit";
import { useChangeOwnPassword } from "../hooks/useChangeOwnPassword";
import { I18N_KEYS, I18N_NAMESPACE, makeT } from "../i18n";

const STRONG_RE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{12,}$/;

export interface ChangeOwnPasswordPageProps {
  /** Called after a successful change — host should clear the token & redirect to /login. */
  onPasswordChanged?: () => void;
}

export function ChangeOwnPasswordPage(
  props: ChangeOwnPasswordPageProps,
): JSX.Element {
  const { Card, Input, Button, message } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);
  const { change, loading, error, success } = useChangeOwnPassword();

  const [oldPw, setOldPw] = useState("");
  const [newPw, setNewPw] = useState("");
  const [confirmPw, setConfirmPw] = useState("");
  const [totpCode, setTotpCode] = useState("");
  const [submitted, setSubmitted] = useState(false);

  const newPwInvalid = !STRONG_RE.test(newPw);
  const mismatch = newPw !== confirmPw;
  const oldPwMissing = !oldPw;
  const totpInvalid = !/^\d{6}$/.test(totpCode);
  const passwordRequirements = [
    {
      label: tt(I18N_KEYS.changePassword.requirementLength),
      met: newPw.length >= 12,
    },
    {
      label: tt(I18N_KEYS.changePassword.requirementUpper),
      met: /[a-z]/.test(newPw) && /[A-Z]/.test(newPw),
    },
    {
      label: tt(I18N_KEYS.changePassword.requirementDigit),
      met: /\d/.test(newPw),
    },
    {
      label: tt(I18N_KEYS.changePassword.requirementSymbol),
      met: /[^A-Za-z0-9]/.test(newPw),
    },
  ];

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitted(true);
    if (oldPwMissing || newPwInvalid || mismatch || totpInvalid) return;
    try {
      await change({ oldPassword: oldPw, newPassword: newPw, totpCode });
      message.success?.(tt(I18N_KEYS.changePassword.success));
      // Clear locally — the password is no longer needed in memory.
      setOldPw("");
      setNewPw("");
      setConfirmPw("");
      setTotpCode("");
      props.onPasswordChanged?.();
    } catch (err) {
      message.error?.(err instanceof Error ? err.message : String(err));
    }
  }

  return (
    <AdminPageShell maxWidth={1200}>
      <PageHeader
        title={tt(I18N_KEYS.changePassword.title)}
        subtitle={tt(I18N_KEYS.changePassword.subtitle)}
      />
      <div
        className="platform-admin-password-grid"
        style={{
          display: "grid",
          gridTemplateColumns: "minmax(0, 1fr) minmax(340px, 440px)",
          gap: t.space.xl,
          alignItems: "start",
        }}
      >
        <style>
          {`
            @media (max-width: ${t.breakpoints.md}px) {
              .platform-admin-password-grid { grid-template-columns: 1fr !important; }
            }
          `}
        </style>
        <Card
          style={{
            padding: t.space.xl,
            border: `1px solid color-mix(in srgb, ${t.colors.border.default} 72%, transparent)`,
            boxShadow: t.shadows.none,
          }}
        >
          <h2
            style={{
              margin: `0 0 ${t.space.xl}`,
              color: t.colors.text.primary,
              fontSize: t.typography.titleMedium.fontSize,
              fontWeight: 700,
              lineHeight: t.typography.titleMedium.lineHeight,
            }}
          >
            {tt(I18N_KEYS.changePassword.cardTitle)}
          </h2>
          {success ? (
            <div
              role="status"
              style={{
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                padding: `${t.space.xxl} ${t.space.xl}`,
                gap: t.space.md,
                textAlign: "center",
              }}
            >
              <span
                aria-hidden="true"
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  width: 56,
                  height: 56,
                  borderRadius: t.shape.full,
                  background: `color-mix(in srgb, ${t.colors.status.success} 15%, ${t.colors.surface.elevated})`,
                  border: `1px solid color-mix(in srgb, ${t.colors.status.success} 40%, ${t.colors.border.default})`,
                  fontSize: "1.75rem",
                }}
              >
                ✓
              </span>
              <p
                style={{
                  margin: 0,
                  color: t.colors.status.success,
                  fontSize: t.typography.titleSmall.fontSize,
                  fontWeight: 600,
                  lineHeight: t.typography.titleSmall.lineHeight,
                }}
              >
                {tt(I18N_KEYS.changePassword.success)}
              </p>
            </div>
          ) : (
            <form
              data-testid="change-password-form"
              onSubmit={handleSubmit}
              noValidate
            >
              <Input
                type="password"
                label={tt(I18N_KEYS.changePassword.oldPassword)}
                value={oldPw}
                onChange={(e) => setOldPw(e.target.value)}
                required
                fullWidth
                autoComplete="current-password"
                error={submitted && oldPwMissing}
                helperText={
                  submitted && oldPwMissing
                    ? tt(I18N_KEYS.admin.requiredField)
                    : ""
                }
                disabled={loading}
                data-testid="change-password-old"
              />
              <div style={{ height: t.space.md }} />
              <Input
                type="password"
                label={tt(I18N_KEYS.changePassword.newPassword)}
                value={newPw}
                onChange={(e) => setNewPw(e.target.value)}
                required
                fullWidth
                autoComplete="new-password"
                error={submitted && newPwInvalid}
                helperText={
                  submitted && newPwInvalid
                    ? tt(I18N_KEYS.changePassword.weakPassword)
                    : ""
                }
                disabled={loading}
                data-testid="change-password-new"
              />
              <div style={{ height: t.space.md }} />
              <Input
                type="password"
                label={tt(I18N_KEYS.changePassword.confirmNew)}
                value={confirmPw}
                onChange={(e) => setConfirmPw(e.target.value)}
                required
                fullWidth
                autoComplete="new-password"
                error={submitted && mismatch}
                helperText={
                  submitted && mismatch
                    ? tt(I18N_KEYS.changePassword.mismatch)
                    : ""
                }
                disabled={loading}
                data-testid="change-password-confirm"
              />
              <div style={{ height: t.space.md }} />
              <Input
                type="tel"
                label={tt(I18N_KEYS.changePassword.totpCode)}
                value={totpCode}
                onChange={(e) =>
                  setTotpCode(e.target.value.replace(/\D/g, "").slice(0, 6))
                }
                required
                fullWidth
                autoComplete="one-time-code"
                maxLength={6}
                error={submitted && totpInvalid}
                helperText={
                  submitted && totpInvalid
                    ? tt(I18N_KEYS.loginTotp.invalidCode)
                    : ""
                }
                disabled={loading}
                data-testid="change-password-totp-code"
              />

              {error ? (
                <div
                  role="alert"
                  style={{
                    marginTop: t.space.md,
                    padding: t.space.sm,
                    borderRadius: t.shape.sm,
                    background: `color-mix(in srgb, ${t.colors.status.error} 12%, ${t.colors.surface.elevated})`,
                    color: t.colors.status.error,
                    border: `1px solid color-mix(in srgb, ${t.colors.status.error} 36%, ${t.colors.border.default})`,
                    fontSize: t.typography.bodySmall.fontSize,
                  }}
                >
                  {error.message}
                </div>
              ) : null}

              <div style={{ marginTop: t.space.xl }}>
                <Button
                  type="submit"
                  loading={loading}
                  disabled={loading}
                  data-testid="change-password-submit"
                >
                  {tt(I18N_KEYS.changePassword.submit)}
                </Button>
              </div>
            </form>
          )}
        </Card>

        <aside
          style={{
            padding: t.space.xl,
            borderRadius: t.shape.lg,
            border: `1px solid color-mix(in srgb, ${t.colors.brand.primary} 22%, ${t.colors.border.default})`,
            background: `color-mix(in srgb, ${t.colors.brand.primary} 4%, ${t.colors.surface.elevated})`,
          }}
        >
          <h2
            style={{
              margin: 0,
              color: t.colors.text.primary,
              fontSize: t.typography.titleSmall.fontSize,
              fontWeight: 700,
              lineHeight: t.typography.titleSmall.lineHeight,
            }}
          >
            {tt(I18N_KEYS.changePassword.requirementTitle)}
          </h2>
          <div
            style={{ display: "grid", gap: t.space.md, marginTop: t.space.lg }}
          >
            {passwordRequirements.map((requirement) => (
              <div
                key={requirement.label}
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  gap: t.space.sm,
                  color: t.colors.text.secondary,
                  fontSize: t.typography.bodySmall.fontSize,
                  lineHeight: t.typography.bodySmall.lineHeight,
                }}
              >
                <span>{requirement.label}</span>
                <StatusBadge kind={requirement.met ? "success" : "neutral"}>
                  {requirement.met ? "OK" : "—"}
                </StatusBadge>
              </div>
            ))}
          </div>
        </aside>
      </div>
    </AdminPageShell>
  );
}
