/**
 * @file Hosted page frame
 * @description The SHINWA-branded shell every hosted login, setup and invitation page renders in:
 * the same layout, design tokens and brand footer as the platform console's sign-in page, so
 * every console's users meet one sign-in look. Layout rules live in styles.css, because the
 * strict CSP of the hosted pages allows no inline style element.
 */

import type { CSSProperties, ReactNode } from 'react';
import { useI18n, useUI } from '@brix-sdk/runtime-sdk-react';
import { HOSTED_LOGIN_NAMESPACE } from '../i18n/messages';
import { hostedLoginTokens as tokens } from '../theme';
import shinwaLogoUrl from '../assets/shinwa.png';

const accent = tokens.colors.brand.primary;

/** Primary actions carry the brand accent, as on the platform sign-in page. */
export const primaryButtonStyle: CSSProperties = {
  minHeight: 50,
  borderRadius: tokens.shape.md,
  background: accent,
  color: tokens.colors.brand.primaryContrast,
  boxShadow: `0 12px 24px color-mix(in srgb, ${accent} 28%, transparent)`,
  fontSize: tokens.typography.bodyLarge?.fontSize ?? '1rem',
  fontWeight: 700,
  textTransform: 'none',
};

export function Frame({ title, subtitle, children }: {
  readonly title?: string;
  readonly subtitle?: string;
  readonly children: ReactNode;
}) {
  const { Card, Icon } = useUI();
  const { t } = useI18n(HOSTED_LOGIN_NAMESPACE);
  const shownSubtitle = subtitle ?? (title ? undefined : t('subtitle'));
  const { surface, text, border } = tokens.colors;
  return (
    <main
      className="hosted-shell"
      style={{
        background: `linear-gradient(118deg, ${surface.page} 0%, color-mix(in srgb, ${accent} 6%, ${surface.page}) 34%, `
          + `color-mix(in srgb, ${accent} 14%, ${surface.page}) 62%, color-mix(in srgb, ${accent} 30%, ${surface.page}) 100%)`,
        fontFamily: tokens.typography.fontFamily,
      }}
    >
      <NetworkBackdrop color={accent} surfaceColor={surface.card} />
      <div className="hosted-stage" style={{ color: text.primary }}>
        <section className="hosted-brand" aria-label={t('brand.product')} style={{ gap: tokens.space.lg, paddingLeft: tokens.space.sm }}>
          <div>
            <div
              style={{
                color: accent,
                fontSize: tokens.typography.titleSmall.fontSize,
                fontWeight: 600,
                lineHeight: tokens.typography.titleSmall.lineHeight,
                textTransform: 'uppercase',
              }}
            >
              {t('brand.label')}
            </div>
            <div
              aria-hidden="true"
              style={{
                width: 48,
                height: 4,
                borderRadius: tokens.shape.full,
                background: accent,
                marginTop: tokens.space.sm,
                marginBottom: tokens.space.lg,
              }}
            />
            <p className="hosted-brand-title" style={{ margin: 0, color: text.primary, fontWeight: 600 }}>
              {t('brand.tagline').split('\n').map((part) => (
                <span key={part} style={{ display: 'block' }}>{part}</span>
              ))}
            </p>
            <p style={{ margin: `${tokens.space.md} 0 0`, color: text.secondary, fontSize: '1.125rem', fontWeight: 500, lineHeight: 1.5 }}>
              {t('brand.features')}
              <br />
              {t('brand.console')}
            </p>
            <p
              style={{
                margin: `${tokens.space.md} 0 0`,
                maxWidth: 500,
                color: text.secondary,
                fontSize: tokens.typography.bodyLarge?.fontSize ?? '1rem',
                fontWeight: tokens.typography.bodyMedium.fontWeight,
                lineHeight: 1.7,
              }}
            >
              {t('brand.description')}
            </p>
          </div>
        </section>

        <Card
          className="hosted-card"
          style={{
            width: '100%',
            maxWidth: 428,
            padding: '38px 28px 28px',
            border: `1px solid ${border.subtle}`,
            borderRadius: tokens.shape.md,
            background: surface.card,
            boxShadow: tokens.shadows.xl,
          }}
        >
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
            <div
              style={{
                width: 52,
                height: 52,
                display: 'grid',
                placeItems: 'center',
                borderRadius: tokens.shape.full,
                background: `color-mix(in srgb, ${accent} 10%, transparent)`,
                border: `1px solid color-mix(in srgb, ${accent} 16%, transparent)`,
                color: accent,
                marginBottom: 16,
              }}
            >
              <Icon name="security" size={24} color={accent} />
            </div>
            <h1 className="hosted-title" style={{ margin: 0, color: text.primary, fontSize: '1.75rem', fontWeight: 700, lineHeight: 1.18 }}>
              {title ?? t('title')}
            </h1>
            {shownSubtitle ? (
              <p
                style={{
                  color: text.secondary,
                  margin: '6px 0 0',
                  fontSize: tokens.typography.bodyLarge?.fontSize ?? '1rem',
                  fontWeight: 500,
                  lineHeight: 1.5,
                }}
              >
                {shownSubtitle}
              </p>
            ) : null}
          </div>

          <div style={{ marginTop: 24 }}>{children}</div>

          <footer
            className="hosted-footer"
            style={{
              marginTop: 26,
              paddingTop: 18,
              borderTop: `1px solid ${border.subtle}`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: tokens.space.md,
            }}
          >
            <span
              style={{
                color: text.secondary,
                fontSize: tokens.typography.labelSmall.fontSize,
                fontWeight: 500,
                lineHeight: tokens.typography.labelSmall.lineHeight,
              }}
            >
              {t('brand.poweredBy')}
            </span>
            <img src={shinwaLogoUrl} alt="SHINWA" width={116} height={32} style={{ objectFit: 'contain', display: 'block' }} />
          </footer>
        </Card>
      </div>
    </main>
  );
}

/** The network-of-cubes backdrop of the platform sign-in page. */
function NetworkBackdrop({ color, surfaceColor }: { readonly color: string; readonly surfaceColor: string }) {
  return (
    <svg
      className="hosted-backdrop"
      aria-hidden="true"
      focusable="false"
      width="100%"
      height="100%"
      viewBox="0 0 1600 900"
      preserveAspectRatio="xMidYMid slice"
    >
      <defs>
        <symbol id="hosted-login-cube" viewBox="0 0 220 260">
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
      <g color={color} opacity="0.14">
        <use href="#hosted-login-cube" x="-60" y="44" width="310" height="366" />
      </g>
      <g color={color} opacity="0.16">
        <use href="#hosted-login-cube" x="298" y="630" width="270" height="319" />
      </g>
      <g color={surfaceColor} opacity="0.62">
        <use href="#hosted-login-cube" x="806" y="520" width="330" height="390" />
      </g>
      <g color={color} opacity="0.12">
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
