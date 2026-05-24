/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useI18n, useTheme } from "@brix-sdk/runtime-sdk-react";
import type { DesignTokens } from "@brix-sdk/runtime-sdk-api-web";
import QRCode from "qrcode";
import {
  AdminPageShell,
  CircularIconBadge,
  ConnectedSteps,
  PageHeader,
  StatusBadge,
  type ConnectedStepItem,
  useUIStrict,
} from "../internal/ui-kit";
import { usePlatformSetup } from "../hooks/usePlatformSetup";
import { PLATFORM_ADMIN_ROUTES } from "../constants";
import { I18N_KEYS, I18N_NAMESPACE, makeT } from "../i18n";

const STRONG_RE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{12,}$/;

type SetupStep = "password" | "totp" | "done";

export function PlatformSetupPage(): JSX.Element {
  const { Card, Input, Button, Icon, message } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const token = params.get("token")?.trim() ?? "";
  const setup = usePlatformSetup();
  const [step, setStep] = useState<SetupStep>("password");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [totpCode, setTotpCode] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [generatedQrCodeDataUri, setGeneratedQrCodeDataUri] = useState<
    string | null
  >(null);
  const [qrCodeGenerationFailed, setQrCodeGenerationFailed] = useState(false);

  useEffect(() => {
    if (!token) return;
    setup.validate(token).catch(() => undefined);
  }, [token, setup.validate]);

  useEffect(() => {
    const otpauthUri = setup.totpResult?.otpauthUri;
    if (!otpauthUri || setup.totpResult?.qrCodeDataUri) {
      setGeneratedQrCodeDataUri(null);
      setQrCodeGenerationFailed(false);
      return;
    }

    let cancelled = false;
    setGeneratedQrCodeDataUri(null);
    setQrCodeGenerationFailed(false);
    QRCode.toDataURL(otpauthUri, {
      errorCorrectionLevel: "M",
      margin: 1,
      width: 192,
    })
      .then((dataUri) => {
        if (!cancelled) {
          setGeneratedQrCodeDataUri(dataUri);
          setQrCodeGenerationFailed(false);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setGeneratedQrCodeDataUri(null);
          setQrCodeGenerationFailed(true);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [setup.totpResult?.otpauthUri, setup.totpResult?.qrCodeDataUri]);

  const passwordInvalid = !STRONG_RE.test(password);
  const passwordMismatch = password !== confirmPassword;
  const totpInvalid = !/^\d{6}$/.test(totpCode);
  const accountLabel =
    setup.validateResult?.displayName ??
    setup.validateResult?.email ??
    setup.validateResult?.username ??
    setup.validateResult?.loginId ??
    "";
  const passwordRequirements = [
    {
      label: tt(I18N_KEYS.changePassword.requirementLength),
      met: password.length >= 12,
    },
    {
      label: tt(I18N_KEYS.changePassword.requirementUpper),
      met: /[a-z]/.test(password) && /[A-Z]/.test(password),
    },
    {
      label: tt(I18N_KEYS.changePassword.requirementDigit),
      met: /\d/.test(password),
    },
    {
      label: tt(I18N_KEYS.changePassword.requirementSymbol),
      met: /[^A-Za-z0-9]/.test(password),
    },
  ];
  const setupProgress: ConnectedStepItem[] = [
    {
      label: tt(I18N_KEYS.setup.stepPassword),
      state: step === "password" ? "current" : "done",
    },
    {
      label: tt(I18N_KEYS.setup.stepAuthenticator),
      state:
        step === "password" ? "pending" : step === "totp" ? "current" : "done",
    },
    {
      label: tt(I18N_KEYS.setup.stepComplete),
      state: step === "done" ? "done" : "pending",
    },
  ];

  async function handlePasswordSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitted(true);
    if (passwordInvalid || passwordMismatch) return;
    try {
      await setup.initTotp(token);
      setSubmitted(false);
      setStep("totp");
    } catch (err) {
      message.error?.(err instanceof Error ? err.message : String(err));
    }
  }

  async function handleTotpSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitted(true);
    if (!setup.totpResult || totpInvalid) return;
    try {
      const res = await setup.complete({
        token,
        challengeId: setup.totpResult.challengeId,
        password,
        totpCode,
      });
      if (!res.activated) {
        throw new Error(tt(I18N_KEYS.setup.completeFailed));
      }
      setPassword("");
      setConfirmPassword("");
      setTotpCode("");
      setStep("done");
      message.success?.(tt(I18N_KEYS.setup.success));
    } catch (err) {
      message.error?.(err instanceof Error ? err.message : String(err));
    }
  }

  async function handleManualSecretCopy(): Promise<void> {
    if (!manualSecret) return;
    try {
      await navigator.clipboard.writeText(manualSecret);
      message.success?.(tt(I18N_KEYS.common.copied));
    } catch (err) {
      message.error?.(err instanceof Error ? err.message : String(err));
    }
  }

  const invalidLink =
    !token || (setup.validateResult && !setup.validateResult.valid);
  const manualSecret = extractTotpSecret(setup.totpResult?.otpauthUri);
  const qrCodeDataUri =
    setup.totpResult?.qrCodeDataUri ?? generatedQrCodeDataUri;
  const showManualSecretFallback = Boolean(
    qrCodeGenerationFailed && manualSecret,
  );

  return (
    <AdminPageShell maxWidth={1060}>
      <PageHeader
        title={tt(I18N_KEYS.setup.title)}
        subtitle={tt(I18N_KEYS.setup.subtitle)}
      />
      <Card
        style={{
          width: "100%",
          maxWidth: "100%",
          boxSizing: "border-box",
          overflow: "hidden",
          padding: t.space.lg,
          border: `1px solid ${t.colors.border.subtle}`,
          boxShadow: t.shadows.sm,
          background: t.colors.surface.elevated,
        }}
      >
        {setup.loading && !setup.validateResult ? (
          <div
            style={{
              minHeight: 220,
              display: "grid",
              placeItems: "center",
              color: t.colors.text.secondary,
            }}
          >
            {tt(I18N_KEYS.common.loading)}
          </div>
        ) : invalidLink ? (
          <div
            role="alert"
            style={{
              minHeight: 220,
              display: "grid",
              gridTemplateColumns: "56px minmax(0, 1fr)",
              gap: t.space.md,
              alignItems: "start",
              padding: t.space.lg,
              borderRadius: t.shape.md,
              border: `1px solid color-mix(in srgb, ${t.colors.status.error} 30%, ${t.colors.border.default})`,
              background: `color-mix(in srgb, ${t.colors.status.error} 6%, ${t.colors.surface.elevated})`,
            }}
          >
            <CircularIconBadge
              icon="link_off"
              tone="info"
              label={tt(I18N_KEYS.setup.invalidLink)}
            />
            <div>
              <h2
                style={{
                  margin: 0,
                  color: t.colors.text.primary,
                  fontSize: t.typography.titleSmall.fontSize,
                  fontWeight: 750,
                  lineHeight: t.typography.titleSmall.lineHeight,
                }}
              >
                {tt(I18N_KEYS.setup.invalidLink)}
              </h2>
              <p
                style={{
                  margin: `${t.space.sm} 0 0`,
                  color: t.colors.text.secondary,
                  fontSize: t.typography.bodySmall.fontSize,
                  lineHeight: t.typography.bodySmall.lineHeight,
                }}
              >
                {tt(I18N_KEYS.setup.invalidLinkHelp)}
              </p>
            </div>
          </div>
        ) : step === "done" ? (
          <div
            className="platform-setup-done-grid"
            style={{
              display: "grid",
              gridTemplateColumns: "minmax(0, 1fr)",
              gap: t.space.xl,
              alignItems: "stretch",
            }}
          >
            <style>
              {`
                @media (max-width: ${t.breakpoints.md}px) {
                .platform-setup-done-grid { grid-template-columns: 1fr !important; }
                .platform-setup-done-main { padding: ${t.space.md} !important; }
              }
            `}
            </style>
            <section
              className="platform-setup-done-main"
              style={{
                display: "grid",
                gap: t.space.lg,
                alignContent: "start",
                padding: t.space.lg,
                borderRadius: t.shape.md,
                border: `1px solid ${t.colors.border.subtle}`,
                background: t.colors.surface.elevated,
                minWidth: 0,
                boxSizing: "border-box",
              }}
            >
              <div>
                <h2
                  style={{
                    margin: 0,
                    color: t.colors.text.primary,
                    fontSize: t.typography.titleSmall.fontSize,
                    display: "flex",
                    alignItems: "center",
                    gap: t.space.sm,
                    lineHeight: t.typography.titleSmall.lineHeight,
                  }}
                >
                  <Icon
                    name="check_circle"
                    size={24}
                    color={t.colors.brand.primary}
                  />
                  {tt(I18N_KEYS.setup.successTitle)}
                </h2>
                <p
                  style={{
                    margin: `${t.space.sm} 0 0`,
                    color: t.colors.text.secondary,
                    lineHeight: t.typography.bodyMedium.lineHeight,
                  }}
                >
                  {tt(I18N_KEYS.setup.successBody)}
                </p>
              </div>
              <div style={{ justifySelf: "start" }}>
                <Button
                  type="button"
                  size="small"
                  startIcon="login"
                  onClick={() =>
                    navigate(PLATFORM_ADMIN_ROUTES.LOGIN, { replace: true })
                  }
                >
                  {tt(I18N_KEYS.login.submit)}
                </Button>
              </div>
            </section>
          </div>
        ) : (
          <div style={{ display: "grid", gap: t.space.xl }}>
            <ConnectedSteps
              items={setupProgress}
              label={tt(I18N_KEYS.setup.progressLabel)}
            />
            {accountLabel ? (
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: t.space.sm,
                  padding: t.space.md,
                  borderRadius: t.shape.md,
                  border: `1px solid ${t.colors.border.subtle}`,
                  background: t.colors.surface.elevated,
                }}
              >
                <Icon
                  name="account_circle"
                  size={20}
                  color={t.colors.brand.primary}
                />
                <span style={{ color: t.colors.text.secondary }}>
                  {tt(I18N_KEYS.setup.account)}
                </span>
                <strong style={{ color: t.colors.text.primary }}>
                  {accountLabel}
                </strong>
              </div>
            ) : null}

            {step === "password" ? (
              <div
                className="platform-setup-password-grid"
                style={{
                  display: "grid",
                  gridTemplateColumns: "minmax(0, 1fr) 340px",
                  gap: t.space.xl,
                  alignItems: "stretch",
                }}
              >
                <style>
                  {`
                  @media (max-width: ${t.breakpoints.md}px) {
                    .platform-setup-password-grid { grid-template-columns: 1fr !important; }
                    .platform-setup-password-form,
                    .platform-setup-policy-panel { padding: ${t.space.md} !important; }
                  }
                `}
                </style>
                <form
                  className="platform-setup-password-form"
                  data-testid="platform-setup-password-form"
                  onSubmit={handlePasswordSubmit}
                  noValidate
                  style={{
                    minWidth: 0,
                    boxSizing: "border-box",
                    padding: t.space.lg,
                    borderRadius: t.shape.md,
                    border: `1px solid ${t.colors.border.subtle}`,
                    background: t.colors.surface.elevated,
                    alignSelf: "stretch",
                    display: "grid",
                    gap: t.space.md,
                    alignContent: "center",
                  }}
                >
                  <Input
                    type="password"
                    label={tt(I18N_KEYS.setup.password)}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    fullWidth
                    autoComplete="new-password"
                    error={submitted && passwordInvalid}
                    helperText={
                      submitted && passwordInvalid
                        ? tt(I18N_KEYS.changePassword.weakPassword)
                        : ""
                    }
                    disabled={setup.loading}
                    data-testid="platform-setup-password"
                  />
                  <Input
                    type="password"
                    label={tt(I18N_KEYS.setup.confirmPassword)}
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    required
                    fullWidth
                    autoComplete="new-password"
                    error={submitted && passwordMismatch}
                    helperText={
                      submitted && passwordMismatch
                        ? tt(I18N_KEYS.setup.passwordMismatch)
                        : ""
                    }
                    disabled={setup.loading}
                    data-testid="platform-setup-confirm-password"
                  />
                  <div>
                    <Button
                      type="submit"
                      startIcon="arrow_forward"
                      loading={setup.loading}
                      disabled={setup.loading}
                      data-testid="platform-setup-continue"
                    >
                      {tt(I18N_KEYS.setup.continue)}
                    </Button>
                  </div>
                </form>
                <aside
                  className="platform-setup-policy-panel"
                  style={{
                    minWidth: 0,
                    boxSizing: "border-box",
                    padding: t.space.lg,
                    borderRadius: t.shape.md,
                    border: `1px solid color-mix(in srgb, ${t.colors.brand.primary} 22%, ${t.colors.border.default})`,
                    background: `color-mix(in srgb, ${t.colors.brand.primary} 4%, ${t.colors.surface.elevated})`,
                    alignSelf: "stretch",
                    display: "grid",
                    alignContent: "center",
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
                    style={{
                      display: "grid",
                      gap: t.space.sm,
                      marginTop: t.space.md,
                    }}
                  >
                    {passwordRequirements.map((requirement) => (
                      <div
                        key={requirement.label}
                        style={{
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "space-between",
                          gap: t.space.sm,
                          minWidth: 0,
                          color: t.colors.text.secondary,
                          fontSize: t.typography.bodySmall.fontSize,
                          lineHeight: t.typography.bodySmall.lineHeight,
                        }}
                      >
                        <span
                          style={{ minWidth: 0, overflowWrap: "break-word" }}
                        >
                          {requirement.label}
                        </span>
                        <StatusBadge
                          kind={requirement.met ? "success" : "neutral"}
                        >
                          {requirement.met ? "OK" : "-"}
                        </StatusBadge>
                      </div>
                    ))}
                  </div>
                </aside>
              </div>
            ) : (
              <form
                data-testid="platform-setup-totp-form"
                onSubmit={handleTotpSubmit}
                noValidate
              >
                <div
                  className="platform-setup-totp-grid"
                  style={{
                    display: "grid",
                    gridTemplateColumns: "280px minmax(0, 1fr)",
                    gap: t.space.xl,
                    alignItems: "start",
                  }}
                >
                  <style>
                    {`
                      @media (max-width: ${t.breakpoints.md}px) {
                        .platform-setup-totp-grid { grid-template-columns: 1fr !important; }
                        .platform-setup-totp-qr,
                        .platform-setup-totp-code-panel { padding: ${t.space.md} !important; }
                      }
                    `}
                  </style>
                  <section
                    className="platform-setup-totp-qr"
                    style={{
                      minWidth: 0,
                      boxSizing: "border-box",
                      padding: t.space.lg,
                      borderRadius: t.shape.md,
                      border: `1px solid ${t.colors.border.subtle}`,
                      background: t.colors.surface.elevated,
                    }}
                  >
                    {qrCodeDataUri ? (
                      <img
                        src={qrCodeDataUri}
                        alt="TOTP QR"
                        style={{
                          width: 192,
                          height: 192,
                          display: "block",
                          margin: "0 auto",
                        }}
                      />
                    ) : qrCodeGenerationFailed ? (
                      <div
                        role="status"
                        style={{
                          minHeight: 192,
                          display: "grid",
                          placeItems: "center",
                          textAlign: "center",
                          padding: t.space.md,
                          borderRadius: t.shape.sm,
                          border: `1px dashed ${t.colors.border.default}`,
                          color: t.colors.text.secondary,
                          background: t.colors.surface.page,
                        }}
                      >
                        {tt(I18N_KEYS.setup.qrUnavailable)}
                      </div>
                    ) : null}
                    {showManualSecretFallback ? (
                      <div
                        style={{
                          marginTop: t.space.md,
                          padding: t.space.sm,
                          borderRadius: t.shape.sm,
                          border: `1px solid ${t.colors.border.subtle}`,
                          background: t.colors.surface.page,
                          color: t.colors.text.secondary,
                          fontSize: t.typography.bodySmall.fontSize,
                        }}
                      >
                        <div style={{ marginBottom: t.space.xs }}>
                          {tt(I18N_KEYS.setup.manualSecret)}
                        </div>
                        <code
                          style={{
                            color: t.colors.text.primary,
                            wordBreak: "break-all",
                          }}
                        >
                          {manualSecret}
                        </code>
                        <p
                          style={{
                            margin: `${t.space.sm} 0 0`,
                            color: t.colors.text.secondary,
                            fontSize: t.typography.bodySmall.fontSize,
                            lineHeight: t.typography.bodySmall.lineHeight,
                          }}
                        >
                          {tt(I18N_KEYS.setup.manualSecretHelp)}
                        </p>
                        <div style={{ marginTop: t.space.md }}>
                          <Button
                            type="button"
                            variant="secondary"
                            size="small"
                            startIcon="copy"
                            onClick={() => void handleManualSecretCopy()}
                          >
                            {tt(I18N_KEYS.setup.copyManualSecret)}
                          </Button>
                        </div>
                      </div>
                    ) : null}
                  </section>
                  <section
                    className="platform-setup-totp-code-panel"
                    style={{ minWidth: 0 }}
                  >
                    <h2
                      style={{
                        margin: `0 0 ${t.space.md}`,
                        color: t.colors.text.primary,
                        fontSize: t.typography.titleSmall.fontSize,
                      }}
                    >
                      {tt(I18N_KEYS.setup.totpTitle)}
                    </h2>
                    <p
                      style={{
                        margin: `0 0 ${t.space.lg}`,
                        color: t.colors.text.secondary,
                        fontSize: t.typography.bodySmall.fontSize,
                        lineHeight: t.typography.bodySmall.lineHeight,
                      }}
                    >
                      {tt(I18N_KEYS.setup.totpHelp)}
                    </p>
                    <Input
                      type="tel"
                      label={tt(I18N_KEYS.setup.totpCode)}
                      value={totpCode}
                      onChange={(e) =>
                        setTotpCode(
                          e.target.value.replace(/\D/g, "").slice(0, 6),
                        )
                      }
                      required
                      fullWidth
                      autoComplete="one-time-code"
                      maxLength={6}
                      error={submitted && totpInvalid}
                      helperText={
                        submitted && totpInvalid
                          ? tt(I18N_KEYS.loginTotp.invalidCode)
                          : tt(I18N_KEYS.setup.totpCodeHelp)
                      }
                      disabled={setup.loading}
                      data-testid="platform-setup-totp-code"
                    />
                    <div style={{ marginTop: t.space.xl }}>
                      <Button
                        type="submit"
                        loading={setup.loading}
                        disabled={setup.loading}
                        data-testid="platform-setup-complete"
                      >
                        {tt(I18N_KEYS.setup.complete)}
                      </Button>
                    </div>
                  </section>
                </div>
              </form>
            )}

            {setup.error ? (
              <div role="alert" style={{ color: t.colors.status.error }}>
                {setup.error.message}
              </div>
            ) : null}
          </div>
        )}
      </Card>
    </AdminPageShell>
  );
}

function extractTotpSecret(otpauthUri?: string): string | null {
  if (!otpauthUri) return null;
  try {
    return new URL(otpauthUri).searchParams.get("secret");
  } catch {
    return null;
  }
}
