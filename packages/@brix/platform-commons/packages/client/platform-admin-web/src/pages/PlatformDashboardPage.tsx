/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file PlatformDashboardPage — landing page after a successful platform login.
 *
 * Renders four navigation cards corresponding to the four operational areas
 * defined in SSOT §9. Each card is gated on the relevant permission via
 * {@link AuthCapability.hasPermission} so the UI matches what the backend
 * will actually authorise.
 */

import { useNavigate } from 'react-router-dom';
import { useAuth, useI18n, useTheme } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import { useUIStrict } from '../internal/ui-kit';
import {
  PLATFORM_ADMIN_PERMISSIONS,
  PLATFORM_ADMIN_ROUTES,
} from '../constants';
import { I18N_KEYS, I18N_NAMESPACE, makeT } from '../i18n';

interface DashboardCardSpec {
  key: string;
  label: string;
  area: string;
  description: string;
  route: string;
  icon: string;
  tone: 'brand' | 'info' | 'warning' | 'success';
  permission?: string;
}

export function PlatformDashboardPage(): JSX.Element {
  const { Button, Card, Icon } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tt = makeT(useI18n(I18N_NAMESPACE).t);
  const navigate = useNavigate();
  const { user, hasPermission } = useAuth();

  const cards: DashboardCardSpec[] = [
    {
      key: 'admins',
      label: tt(I18N_KEYS.dashboard.cardAdmins),
      area: tt(I18N_KEYS.dashboard.cardAdminsArea),
      description: tt(I18N_KEYS.dashboard.cardAdminsDescription),
      route: PLATFORM_ADMIN_ROUTES.ADMINS,
      icon: 'admin_panel_settings',
      tone: 'brand',
      permission: PLATFORM_ADMIN_PERMISSIONS.ADMIN_READ,
    },
    {
      key: 'tenants',
      label: tt(I18N_KEYS.dashboard.cardTenants),
      area: tt(I18N_KEYS.dashboard.cardTenantsArea),
      description: tt(I18N_KEYS.dashboard.cardTenantsDescription),
      route: PLATFORM_ADMIN_ROUTES.TENANTS,
      icon: 'business',
      tone: 'info',
      permission: PLATFORM_ADMIN_PERMISSIONS.TENANT_READ,
    },
    {
      key: 'audit',
      label: tt(I18N_KEYS.dashboard.cardAudit),
      area: tt(I18N_KEYS.dashboard.cardAuditArea),
      description: tt(I18N_KEYS.dashboard.cardAuditDescription),
      route: PLATFORM_ADMIN_ROUTES.AUDIT,
      icon: 'receipt',
      tone: 'warning',
      permission: PLATFORM_ADMIN_PERMISSIONS.AUDIT_READ,
    },
    {
      key: 'change-pw',
      label: tt(I18N_KEYS.dashboard.cardChangePassword),
      area: tt(I18N_KEYS.dashboard.cardChangePasswordArea),
      description: tt(I18N_KEYS.dashboard.cardChangePasswordDescription),
      route: PLATFORM_ADMIN_ROUTES.CHANGE_OWN_PASSWORD,
      icon: 'vpn_key',
      tone: 'success',
      // Always visible — every authenticated admin can change their own password.
    },
  ];

  const visible = cards.filter(
    (card) => !card.permission || hasPermission(card.permission),
  );

  const roleLabel = user?.roles?.[0] ?? tt(I18N_KEYS.dashboard.roleFallback);

  const accentByTone: Record<DashboardCardSpec['tone'], string> = {
    brand: t.colors.brand.primary,
    info: t.colors.status.info,
    warning: t.colors.status.warning,
    success: t.colors.status.success,
  };

  const panelRadius = `calc(${t.shape.lg} + ${t.space.xs})`;
  const softBorder = `color-mix(in srgb, ${t.colors.border.default} 48%, transparent)`;
  // 劳模模式：卡片与分割线使用品牌主色 35% 透明边，在 surface.elevated 背景上清晰可辨（R-6：零硬编码）。
  const primaryBorder = `color-mix(in srgb, ${t.colors.brand.primary} 35%, transparent)`;
  const softTextShadow = 'none';

  return (
    <div
      style={{
        minHeight: '100%',
        background: 'transparent',
        padding: `${t.space.xl} ${t.space.xl}`,
      }}
    >
      <div style={{ maxWidth: 1160, margin: '0 auto' }}>
        <header
          aria-labelledby="platform-dashboard-title"
          style={{
            display: 'flex',
            alignItems: 'flex-end',
            justifyContent: 'space-between',
            gap: t.space.md,
            flexWrap: 'wrap',
            paddingBottom: t.space.xl,
            marginBottom: t.space.xl,
            borderBottom: `1px solid ${primaryBorder}`,
          }}
        >
          <div style={{ display: 'grid', gap: t.space.sm, minWidth: 260 }}>
            <h1
              id="platform-dashboard-title"
              style={{
                margin: 0,
                color: t.colors.text.primary,
                fontSize: '2rem',
                fontWeight: 750,
                lineHeight: 1.18,
                textShadow: softTextShadow,
              }}
            >
              {tt(I18N_KEYS.dashboard.title)}
            </h1>
            <p
              style={{
                margin: 0,
                color: t.colors.text.secondary,
                fontSize: t.typography.bodyMedium.fontSize,
                fontWeight: t.typography.bodyMedium.fontWeight,
                lineHeight: t.typography.bodyMedium.lineHeight,
              }}
            >
              {tt(I18N_KEYS.dashboard.welcome, {
                name: user?.displayName ?? user?.username ?? '',
              })}
            </p>
          </div>
          <div
            aria-label="Session summary"
            style={{
              display: 'flex',
              gap: t.space.sm,
              flexWrap: 'wrap',
              justifyContent: 'flex-end',
              alignItems: 'center',
            }}
          >
            <span
              style={{
                padding: `${t.space.xs} ${t.space.md}`,
                borderRadius: t.shape.full,
                border: `1px solid ${softBorder}`,
                // 劳模模式：内容面板是 surface.card，待辨别的彽章必须用 elevated。
                background: t.colors.surface.elevated,
                color: t.colors.text.primary,
                fontSize: t.typography.labelSmall.fontSize,
                fontWeight: 700,
                lineHeight: t.typography.labelSmall.lineHeight,
                whiteSpace: 'nowrap',
                boxShadow: 'none',
              }}
            >
              {roleLabel}
            </span>
            <span
              style={{
                padding: `${t.space.xs} ${t.space.md}`,
                borderRadius: t.shape.full,
                border: `1px solid ${softBorder}`,
                background: t.colors.surface.elevated,
                color: t.colors.text.secondary,
                fontSize: t.typography.labelSmall.fontSize,
                fontWeight: 600,
                lineHeight: t.typography.labelSmall.lineHeight,
                whiteSpace: 'nowrap',
              }}
            >
              {tt(I18N_KEYS.dashboard.workspaceCount, { count: visible.length })}
            </span>
          </div>
        </header>

        <section
          aria-labelledby="platform-dashboard-operations"
          style={{
            display: 'grid',
            gap: t.space.md,
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: t.space.md,
              flexWrap: 'wrap',
            }}
          >
            <h2
              id="platform-dashboard-operations"
              style={{
                margin: 0,
                color: t.colors.text.primary,
                fontSize: '1.125rem',
                fontWeight: 700,
                lineHeight: 1.35,
              }}
            >
              {tt(I18N_KEYS.dashboard.sectionTitle)}
            </h2>
            <span
              style={{
                color: t.colors.text.secondary,
                fontSize: t.typography.label.fontSize,
                fontWeight: t.typography.label.fontWeight,
                lineHeight: t.typography.label.lineHeight,
              }}
            >
              {tt(I18N_KEYS.dashboard.workspaceCount, { count: visible.length })}
            </span>
          </div>

          <div
            style={{
              display: 'grid',
              gap: t.space.md,
              gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 220px), 1fr))',
            }}
          >
            {visible.map((card, index) => {
              const accent = accentByTone[card.tone];
              return (
                <Card
                  key={card.key}
                  elevation={0}
                  style={{
                    background: t.colors.surface.elevated,
                    border: `1px solid ${primaryBorder}`,
                    borderRadius: panelRadius,
                    boxShadow: 'none',
                    minHeight: 196,
                    overflow: 'hidden',
                  }}
                >
                  <div
                    style={{
                      minHeight: 196,
                      display: 'flex',
                      flexDirection: 'column',
                      gap: t.space.md,
                      padding: t.space.lg,
                    }}
                  >
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: t.space.md,
                      }}
                    >
                      <span
                        style={{
                          color: t.colors.text.secondary,
                          fontSize: t.typography.labelSmall.fontSize,
                          fontWeight: 700,
                          lineHeight: t.typography.labelSmall.lineHeight,
                        }}
                      >
                        {String(index + 1).padStart(2, '0')}
                      </span>
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: t.space.xs,
                          padding: `${t.space.xs} ${t.space.sm}`,
                          border: `1px solid color-mix(in srgb, ${accent} 22%, ${softBorder})`,
                          borderRadius: t.shape.full,
                          // 劳模模式：卡片本就是 surface.card，标签需 elevated 底色 + 10% accent 色调以区分功能区。
                          background: `color-mix(in srgb, ${accent} 10%, ${t.colors.surface.elevated})`,
                          color: t.colors.text.primary,
                          fontSize: t.typography.labelSmall.fontSize,
                          fontWeight: 700,
                          lineHeight: t.typography.labelSmall.lineHeight,
                          whiteSpace: 'nowrap',
                        }}
                      >
                        <Icon
                          name={card.icon}
                          size="small"
                          color={accent}
                          aria-label={card.area}
                        />
                        {card.area}
                      </span>
                    </div>

                    <div style={{ display: 'grid', gap: t.space.sm }}>
                      <h3
                        style={{
                          margin: 0,
                          color: t.colors.text.primary,
                          fontSize: '1.18rem',
                          fontWeight: 750,
                          lineHeight: 1.32,
                        }}
                      >
                        {card.label}
                      </h3>
                      <p
                        style={{
                          margin: 0,
                          color: t.colors.text.secondary,
                          fontSize: t.typography.bodySmall.fontSize,
                          fontWeight: t.typography.bodySmall.fontWeight,
                          lineHeight: 1.65,
                        }}
                      >
                        {card.description}
                      </p>
                    </div>

                    <div style={{ marginTop: 'auto' }}>
                      <Button
                        variant="secondary"
                        size="small"
                        endIcon="arrow_forward"
                        data-testid={`platform-dashboard-open-${card.key}`}
                        onClick={(event) => {
                          event.stopPropagation();
                          navigate(card.route);
                        }}
                      >
                        {tt(I18N_KEYS.dashboard.openWorkspace)}
                      </Button>
                    </div>
                  </div>
                </Card>
              );
            })}
          </div>
        </section>
      </div>
    </div>
  );
}
