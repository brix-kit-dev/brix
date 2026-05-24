/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useNavigate } from "react-router-dom";
import { useI18n, useTheme } from "@brix-sdk/runtime-sdk-react";
import type { DesignTokens } from "@brix-sdk/runtime-sdk-api-web";
import {
  AdminPageShell,
  MailDeliveryVisual,
  PageHeader,
  useUIStrict,
} from "../internal/ui-kit";
import { PLATFORM_ADMIN_ROUTES } from "../constants";
import { I18N_KEYS, I18N_NAMESPACE, makeT } from "../i18n";

export function PlatformBootstrapSentPage(): JSX.Element {
  const { Card, Button } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);
  const navigate = useNavigate();

  return (
    <AdminPageShell maxWidth={920}>
      <PageHeader
        title={tt(I18N_KEYS.bootstrap.sentTitle)}
        subtitle={tt(I18N_KEYS.bootstrap.sentSubtitle)}
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
        <div
          className="platform-bootstrap-sent-grid"
          style={{
            display: "grid",
            gridTemplateColumns: "minmax(0, 1fr)",
            minWidth: 0,
          }}
        >
          <style>
            {`
              @media (max-width: ${t.breakpoints.md}px) {
                .platform-bootstrap-sent-grid { grid-template-columns: 1fr !important; }
                .platform-bootstrap-sent-mini-grid { grid-template-columns: 1fr !important; }
                .platform-bootstrap-sent-main { padding: ${t.space.md} !important; }
              }
            `}
          </style>
          <section
            className="platform-bootstrap-sent-main"
            style={{
              display: "grid",
              gridTemplateRows: "auto 1fr auto",
              gap: t.space.lg,
              padding: t.space.xl,
              borderRadius: t.shape.md,
              border: `1px solid ${t.colors.border.subtle}`,
              background: t.colors.surface.elevated,
              minWidth: 0,
              boxSizing: "border-box",
            }}
          >
            <div
              style={{
                display: "flex",
                gap: t.space.lg,
                alignItems: "center",
                flexWrap: "wrap",
              }}
            >
              <MailDeliveryVisual label={tt(I18N_KEYS.bootstrap.sentBadge)} />
              <div style={{ minWidth: 240, flex: "1 1 260px" }}>
                <h2
                  style={{
                    margin: 0,
                    color: t.colors.text.primary,
                    fontSize: t.typography.titleSmall.fontSize,
                    fontWeight: 750,
                    lineHeight: t.typography.titleSmall.lineHeight,
                    overflowWrap: "break-word",
                  }}
                >
                  {tt(I18N_KEYS.bootstrap.sentBody)}
                </h2>
              </div>
            </div>
            <div
              className="platform-bootstrap-sent-mini-grid"
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
                gap: t.space.sm,
              }}
              aria-label={tt(I18N_KEYS.bootstrap.sentNextTitle)}
            >
              <div
                style={{
                  gridColumn: "1 / -1",
                  display: "flex",
                  alignItems: "center",
                  gap: t.space.sm,
                  color: t.colors.text.secondary,
                  fontSize: t.typography.label.fontSize,
                  fontWeight: 700,
                  lineHeight: t.typography.label.lineHeight,
                }}
              >
                <span
                  aria-hidden="true"
                  style={{
                    width: 24,
                    height: 24,
                    borderRadius: t.shape.full,
                    display: "grid",
                    placeItems: "center",
                    border: `1px solid ${t.colors.border.subtle}`,
                    background: t.colors.surface.elevated,
                    boxShadow: t.shadows.sm,
                    color: t.colors.text.primary,
                    fontSize: t.typography.labelSmall.fontSize,
                    fontWeight: 800,
                    lineHeight: 1,
                  }}
                >
                  3
                </span>
                {tt(I18N_KEYS.bootstrap.sentNextTitle)}
              </div>
              <MiniStatusCard
                step="01"
                label={tt(I18N_KEYS.bootstrap.sentNextMail)}
                icon="mail"
              />
              <MiniStatusCard
                step="02"
                label={tt(I18N_KEYS.bootstrap.sentNextSetup)}
                icon="vpn_key"
              />
              <MiniStatusCard
                step="03"
                label={tt(I18N_KEYS.bootstrap.sentNextMfa)}
                icon="security"
              />
            </div>
            <div>
              <Button
                type="button"
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
      </Card>
    </AdminPageShell>
  );
}

function MiniStatusCard(props: {
  step: string;
  label: string;
  icon: string;
}): JSX.Element {
  const { Icon } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  return (
    <div
      style={{
        minWidth: 0,
        minHeight: 84,
        boxSizing: "border-box",
        padding: t.space.md,
        borderRadius: t.shape.md,
        border: `1px solid ${t.colors.border.subtle}`,
        background: t.colors.surface.elevated,
        boxShadow: t.shadows.sm,
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: t.space.sm,
        }}
      >
        <Icon name={props.icon} size={20} color={t.colors.brand.primary} />
        <span
          style={{
            color: t.colors.text.disabled,
            fontSize: t.typography.labelSmall.fontSize,
            fontWeight: 800,
            lineHeight: 1,
          }}
        >
          {props.step}
        </span>
      </div>
      <div
        style={{
          marginTop: t.space.sm,
          color: t.colors.text.secondary,
          fontSize: t.typography.bodySmall.fontSize,
          lineHeight: t.typography.bodySmall.lineHeight,
          overflowWrap: "break-word",
        }}
      >
        {props.label}
      </div>
    </div>
  );
}
