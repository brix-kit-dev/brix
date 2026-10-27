/**
 * @file Hosted setup page layout
 * @description The layout of the platform administrator setup page (platform-admin-web
 * PlatformSetupPage): a page header, one card with the connected step progress, the account and
 * side-by-side panels. Tenant owners set up their account on a page that looks like the one
 * platform administrators use, rather than on the sign-in card. Rules that need media queries
 * live in styles.css, because the strict CSP of the hosted pages allows no inline style element.
 */

import type { CSSProperties, ReactNode } from 'react';
import { useUI } from '@brix-sdk/runtime-sdk-react';
import { hostedLoginTokens as tokens } from '../theme';

/** A bordered panel inside the setup card, as on the platform setup page. */
export const setupPanelStyle: CSSProperties = {
  minWidth: 0,
  boxSizing: 'border-box',
  padding: tokens.space.lg,
  borderRadius: tokens.shape.md,
  border: `1px solid ${tokens.colors.border.subtle}`,
  background: tokens.colors.surface.elevated,
};

export const setupHeadingStyle: CSSProperties = {
  margin: 0,
  color: tokens.colors.text.primary,
  fontSize: tokens.typography.titleSmall.fontSize,
  fontWeight: 700,
  lineHeight: tokens.typography.titleSmall.lineHeight,
};

export const setupSmallTextStyle: CSSProperties = {
  margin: 0,
  color: tokens.colors.text.secondary,
  fontSize: tokens.typography.bodySmall.fontSize,
  lineHeight: tokens.typography.bodySmall.lineHeight,
};

export function SetupPage({ title, subtitle, children }: {
  readonly title: string;
  readonly subtitle?: string;
  readonly children: ReactNode;
}) {
  const { Card } = useUI();
  return (
    <main
      className="hosted-setup-shell"
      style={{
        background: tokens.colors.surface.card,
        color: tokens.colors.text.primary,
        fontFamily: tokens.typography.fontFamily,
      }}
    >
      <div className="hosted-setup-content">
        <header
          style={{
            marginBottom: tokens.space.lg,
            paddingBottom: tokens.space.sm,
            borderBottom: `1px solid color-mix(in srgb, ${tokens.colors.brand.primary} 28%, transparent)`,
          }}
        >
          <h1
            style={{
              margin: 0,
              color: tokens.colors.text.primary,
              fontSize: tokens.typography.titleMedium.fontSize,
              fontWeight: 750,
              lineHeight: tokens.typography.titleMedium.lineHeight,
              overflowWrap: 'break-word',
            }}
          >
            {title}
          </h1>
          {subtitle ? (
            <p
              style={{
                margin: `${tokens.space.xs} 0 0`,
                color: tokens.colors.text.secondary,
                lineHeight: tokens.typography.bodySmall.lineHeight,
                overflowWrap: 'break-word',
              }}
            >
              {subtitle}
            </p>
          ) : null}
        </header>
        <Card
          className="hosted-setup-card"
          style={{
            width: '100%',
            maxWidth: '100%',
            boxSizing: 'border-box',
            overflow: 'hidden',
            padding: tokens.space.lg,
            border: `1px solid ${tokens.colors.border.subtle}`,
            boxShadow: tokens.shadows.sm,
            background: tokens.colors.surface.elevated,
          }}
        >
          {children}
        </Card>
      </div>
    </main>
  );
}

export type SetupStepState = 'done' | 'current' | 'pending';

/** Numbered steps joined by connectors; a finished step shows a check. */
export function SetupSteps({ items, label }: {
  readonly items: ReadonlyArray<{ readonly label: string; readonly state: SetupStepState }>;
  readonly label: string;
}) {
  const { Icon } = useUI();
  return (
    <ol
      aria-label={label}
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${items.length}, minmax(0, 1fr))`,
        alignItems: 'start',
        width: '100%',
        maxWidth: 760,
        margin: '0 auto',
        padding: 0,
        listStyle: 'none',
      }}
    >
      {items.map((item, index) => {
        const done = item.state === 'done';
        const active = done || item.state === 'current';
        const tone = done ? tokens.colors.status.success : active ? tokens.colors.brand.primary : tokens.colors.text.disabled;
        const previousDone = items[index - 1]?.state === 'done';
        const next = items[index + 1];
        const leftConnector = previousDone && active ? tokens.colors.brand.primary : tokens.colors.border.default;
        const rightConnector = done && next !== undefined && next.state !== 'pending'
          ? tokens.colors.brand.primary
          : tokens.colors.border.default;
        return (
          <li key={item.label} aria-current={item.state === 'current' ? 'step' : undefined} style={{ minWidth: 0 }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 34px minmax(0, 1fr)', alignItems: 'center' }}>
              <div
                aria-hidden="true"
                style={{ height: 2, borderRadius: tokens.shape.full, background: index === 0 ? 'transparent' : leftConnector }}
              />
              <div
                style={{
                  width: 34,
                  height: 34,
                  borderRadius: tokens.shape.full,
                  display: 'grid',
                  placeItems: 'center',
                  color: active ? tokens.colors.brand.primaryContrast : tokens.colors.text.secondary,
                  border: `1px solid ${active ? tone : tokens.colors.border.default}`,
                  background: active
                    ? `color-mix(in srgb, ${tone} 88%, ${tokens.colors.surface.elevated})`
                    : tokens.colors.surface.page,
                }}
              >
                {done ? (
                  <Icon name="check" size={18} color={tokens.colors.brand.primaryContrast} aria-label="" />
                ) : (
                  <span style={{ fontSize: tokens.typography.labelSmall.fontSize, fontWeight: 800, lineHeight: 1 }}>
                    {index + 1}
                  </span>
                )}
              </div>
              <div
                aria-hidden="true"
                style={{
                  height: 2,
                  borderRadius: tokens.shape.full,
                  background: index < items.length - 1 ? rightConnector : 'transparent',
                }}
              />
            </div>
            <div
              style={{
                marginTop: tokens.space.xs,
                color: active ? tokens.colors.text.primary : tokens.colors.text.secondary,
                fontSize: tokens.typography.labelSmall.fontSize,
                fontWeight: active ? 700 : 600,
                lineHeight: tokens.typography.labelSmall.lineHeight,
                textAlign: 'center',
                padding: `0 ${tokens.space.xs}`,
              }}
            >
              {item.label}
            </div>
          </li>
        );
      })}
    </ol>
  );
}

/** "OK" once a rule is met, "-" until then, as in the password policy of the platform setup page. */
export function RuleBadge({ met }: { readonly met: boolean }) {
  return (
    <span
      style={{
        display: 'inline-block',
        padding: `2px ${tokens.space.sm}`,
        borderRadius: tokens.shape.full,
        background: met ? tokens.colors.status.success : tokens.colors.border.default,
        color: met ? tokens.colors.brand.primaryContrast : tokens.colors.text.primary,
        fontSize: '0.75rem',
        fontWeight: 600,
        whiteSpace: 'nowrap',
      }}
    >
      {met ? 'OK' : '-'}
    </span>
  );
}

/** A round icon badge, for example the broken link of an invalid setup link. */
export function IconBadge({ icon, tone }: { readonly icon: string; readonly tone: string }) {
  const { Icon } = useUI();
  return (
    <div
      aria-hidden="true"
      style={{
        width: 48,
        height: 48,
        borderRadius: tokens.shape.full,
        display: 'grid',
        placeItems: 'center',
        background: `color-mix(in srgb, ${tone} 12%, ${tokens.colors.surface.elevated})`,
        border: `1px solid color-mix(in srgb, ${tone} 30%, ${tokens.colors.border.default})`,
      }}
    >
      <Icon name={icon} size={24} color={tone} aria-label="" />
    </div>
  );
}
