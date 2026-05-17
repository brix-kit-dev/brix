/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file Internal UI helpers — token-styled primitives reused by every page.
 *
 * The {@link UIAdapter} contract intentionally does NOT expose a `Table`
 * primitive (see SSOT §11 R-7 — atomic components only). For tabular data
 * we render a semantic `<table>` element styled exclusively from
 * `useTheme().tokens` so that hosts swapping themes (light/dark/tenant)
 * automatically restyle these tables without code changes.
 */

import { type CSSProperties, type ReactNode } from 'react';
import { useTheme, useUI } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';

/* ------------------------------------------------------------------ *
 * Page shell and operational dashboard primitives
 * ------------------------------------------------------------------ */

export interface AdminPageShellProps {
  children: ReactNode;
  maxWidth?: number;
}

export function AdminPageShell(props: AdminPageShellProps): JSX.Element {
  const { tokens } = useTheme();
  const designTokens = tokens as DesignTokens;
  return (
    <div
      className="platform-admin-page-shell"
      style={{
        minHeight: '100%',
        background: 'transparent',
        padding: `${designTokens.space.xl} ${designTokens.space.xl}`,
        fontFamily: designTokens.typography.fontFamily,
      }}
    >
      <style>
        {`
          .platform-admin-page-shell * { box-sizing: border-box; }
          @media (max-width: ${designTokens.breakpoints.sm}px) {
            .platform-admin-page-shell { padding: ${designTokens.space.lg} ${designTokens.space.md} !important; }
          }
        `}
      </style>
      <div style={{ maxWidth: props.maxWidth ?? 1160, margin: '0 auto' }}>
        {props.children}
      </div>
    </div>
  );
}

export interface ToolbarPanelProps {
  children: ReactNode;
}

export function ToolbarPanel(props: ToolbarPanelProps): JSX.Element {
  const { tokens } = useTheme();
  const designTokens = tokens as DesignTokens;
  return (
    <div
      style={{
        display: 'flex',
        gap: designTokens.space.md,
        marginBottom: designTokens.space.md,
        padding: designTokens.space.md,
        flexWrap: 'wrap',
        alignItems: 'flex-end',
        borderRadius: designTokens.shape.lg,
        border: `1px solid color-mix(in srgb, ${designTokens.colors.border.default} 58%, transparent)`,
        background: designTokens.colors.surface.elevated,
      }}
    >
      {props.children}
    </div>
  );
}

export type SummaryTone = StatusKind | 'brand' | 'neutral';

export interface SummaryMetric {
  label: ReactNode;
  value: ReactNode;
  helper?: ReactNode;
  tone?: SummaryTone;
}

export interface SummaryGridProps {
  items: ReadonlyArray<SummaryMetric>;
}

export function SummaryGrid(props: SummaryGridProps): JSX.Element {
  const { tokens } = useTheme();
  const designTokens = tokens as DesignTokens;
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 170px), 1fr))',
        gap: designTokens.space.md,
        marginBottom: designTokens.space.md,
      }}
    >
      {props.items.map((item, index) => {
        const accent = summaryToneColor(designTokens, item.tone ?? 'neutral');
        return (
          <div
            key={index}
            style={{
              minHeight: 92,
              padding: designTokens.space.md,
              borderRadius: designTokens.shape.lg,
              border: `1px solid color-mix(in srgb, ${accent} 24%, ${designTokens.colors.border.default})`,
              background: `linear-gradient(180deg, color-mix(in srgb, ${accent} 8%, ${designTokens.colors.surface.elevated}) 0%, ${designTokens.colors.surface.elevated} 100%)`,
            }}
          >
            <div
              style={{
                color: designTokens.colors.text.secondary,
                fontSize: designTokens.typography.labelSmall.fontSize,
                fontWeight: 700,
                lineHeight: designTokens.typography.labelSmall.lineHeight,
              }}
            >
              {item.label}
            </div>
            <div
              style={{
                marginTop: designTokens.space.xs,
                color: designTokens.colors.text.primary,
                fontSize: designTokens.typography.titleLarge.fontSize,
                fontWeight: 750,
                lineHeight: designTokens.typography.titleLarge.lineHeight,
              }}
            >
              {item.value}
            </div>
            {item.helper ? (
              <div
                style={{
                  marginTop: designTokens.space.xs,
                  color: designTokens.colors.text.secondary,
                  fontSize: designTokens.typography.bodySmall.fontSize,
                  lineHeight: designTokens.typography.bodySmall.lineHeight,
                }}
              >
                {item.helper}
              </div>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}

function summaryToneColor(tokens: DesignTokens, tone: SummaryTone): string {
  switch (tone) {
    case 'brand':
      return tokens.colors.brand.primary;
    case 'success':
      return tokens.colors.status.success;
    case 'warning':
      return tokens.colors.status.warning;
    case 'error':
      return tokens.colors.status.error;
    case 'info':
      return tokens.colors.status.info;
    default:
      return tokens.colors.border.strong;
  }
}

/* ------------------------------------------------------------------ *
 * Tri-state container — renders Loading / Empty / Error / Content
 * ------------------------------------------------------------------ */

export interface TriStateProps<T> {
  loading: boolean;
  error: Error | null;
  data: T | null | undefined;
  isEmpty?: (d: T) => boolean;
  loadingNode?: ReactNode;
  errorNode?: (e: Error) => ReactNode;
  emptyNode?: ReactNode;
  children: (data: T) => ReactNode;
}

export function TriState<T>(props: TriStateProps<T>): JSX.Element {
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const center: CSSProperties = {
    padding: t.space.xl,
    textAlign: 'center',
    color: t.colors.text.secondary,
  };

  if (props.loading && (props.data === null || props.data === undefined)) {
    return <div style={center}>{props.loadingNode ?? 'Loading…'}</div>;
  }
  if (props.error) {
    return (
      <div
        role="alert"
        style={{
          ...center,
          borderRadius: t.shape.lg,
          border: `1px solid color-mix(in srgb, ${t.colors.status.error} 36%, ${t.colors.border.default})`,
          background: `color-mix(in srgb, ${t.colors.status.error} 8%, ${t.colors.surface.elevated})`,
          color: t.colors.status.error,
          fontWeight: 600,
        }}
      >
        {props.errorNode ? props.errorNode(props.error) : props.error.message}
      </div>
    );
  }
  if (props.data === null || props.data === undefined) {
    return <div style={center}>{props.emptyNode ?? 'No data'}</div>;
  }
  if (props.isEmpty?.(props.data)) {
    return <div style={center}>{props.emptyNode ?? 'No data'}</div>;
  }
  return <>{props.children(props.data)}</>;
}

/* ------------------------------------------------------------------ *
 * Token-styled DataTable — semantic <table>, fully theme-driven.
 * ------------------------------------------------------------------ */

export interface DataTableColumn<R> {
  key: string;
  header: ReactNode;
  render: (row: R) => ReactNode;
  /** Optional fixed width (CSS length). */
  width?: string;
}

export interface DataTableProps<R> {
  columns: ReadonlyArray<DataTableColumn<R>>;
  rows: ReadonlyArray<R>;
  rowKey: (row: R) => string | number;
  /** Optional dense mode (smaller padding) for log-style data. */
  dense?: boolean;
}

export function DataTable<R>(props: DataTableProps<R>): JSX.Element {
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const cellPad = props.dense
    ? `${t.space.xs} ${t.space.sm}`
    : `${t.space.sm} ${t.space.md}`;

  return (
    <div
      style={{
        overflowX: 'auto',
        borderRadius: t.shape.lg,
        border: `1px solid color-mix(in srgb, ${t.colors.border.default} 72%, transparent)`,
        background: t.colors.surface.elevated,
      }}
    >
      <style>
        {`
          .platform-admin-data-table tbody tr {
            transition: background ${t.motion.durationShort} ${t.motion.easing};
          }
          .platform-admin-data-table tbody tr:hover {
            background: color-mix(in srgb, ${t.colors.brand.primary} 5%, ${t.colors.surface.elevated});
          }
        `}
      </style>
      <table
        className="platform-admin-data-table"
        style={{
          width: '100%',
          borderCollapse: 'collapse',
          fontFamily: t.typography.fontFamily,
        }}
      >
        <thead>
          <tr style={{ background: `color-mix(in srgb, ${t.colors.brand.primary} 6%, ${t.colors.surface.elevated})` }}>
            {props.columns.map((c) => (
              <th
                key={c.key}
                style={{
                  textAlign: 'left',
                  padding: cellPad,
                  width: c.width,
                  color: t.colors.text.secondary,
                  fontWeight: 700,
                  borderBottom: `1px solid color-mix(in srgb, ${t.colors.border.default} 72%, transparent)`,
                  whiteSpace: 'nowrap',
                }}
              >
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {props.rows.map((r) => (
            <tr key={props.rowKey(r)}>
              {props.columns.map((c) => (
                <td
                  key={c.key}
                  style={{
                    padding: cellPad,
                    color: t.colors.text.primary,
                    borderBottom: `1px solid ${t.colors.border.subtle}`,
                    verticalAlign: 'top',
                  }}
                >
                  {c.render(r)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Page chrome — title + actions row used by every full-page view.
 * ------------------------------------------------------------------ */

export interface PageHeaderProps {
  title: ReactNode;
  subtitle?: ReactNode;
  actions?: ReactNode;
}

export function PageHeader(props: PageHeaderProps): JSX.Element {
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  return (
    <header
      style={{
        display: 'flex',
        alignItems: 'flex-end',
        justifyContent: 'space-between',
        gap: t.space.md,
        flexWrap: 'wrap',
        marginBottom: t.space.lg,
        paddingBottom: t.space.md,
        borderBottom: `1px solid color-mix(in srgb, ${t.colors.brand.primary} 28%, transparent)`,
      }}
    >
      <div style={{ minWidth: 240 }}>
        <h1
          style={{
            margin: 0,
            color: t.colors.text.primary,
            fontSize: t.typography.titleLarge?.fontSize,
            fontWeight: 750,
            lineHeight: t.typography.titleLarge.lineHeight,
          }}
        >
          {props.title}
        </h1>
        {props.subtitle ? (
          <p style={{ margin: `${t.space.xs} 0 0`, color: t.colors.text.secondary }}>
            {props.subtitle}
          </p>
        ) : null}
      </div>
      {props.actions ? (
        <div style={{ display: 'flex', gap: t.space.sm, flexWrap: 'wrap', justifyContent: 'flex-end' }}>{props.actions}</div>
      ) : null}
    </header>
  );
}

/* ------------------------------------------------------------------ *
 * StatusBadge — coloured pill with semantic colour mapping.
 * ------------------------------------------------------------------ */

export type StatusKind = 'success' | 'warning' | 'error' | 'info' | 'neutral';

export interface StatusBadgeProps {
  kind: StatusKind;
  children: ReactNode;
}

export function StatusBadge(props: StatusBadgeProps): JSX.Element {
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const palette: Record<StatusKind, { bg: string; fg: string }> = {
    success: { bg: t.colors.status.success, fg: t.colors.brand.primaryContrast },
    warning: { bg: t.colors.status.warning, fg: t.colors.brand.primaryContrast },
    error: { bg: t.colors.status.error, fg: t.colors.brand.primaryContrast },
    info: { bg: t.colors.status.info, fg: t.colors.brand.primaryContrast },
    neutral: { bg: t.colors.border.default, fg: t.colors.text.primary },
  };
  const { bg, fg } = palette[props.kind];
  return (
    <span
      style={{
        display: 'inline-block',
        padding: `2px ${t.space.sm}`,
        borderRadius: t.shape.full,
        background: bg,
        color: fg,
        fontSize: '0.75rem',
        fontWeight: 600,
        whiteSpace: 'nowrap',
      }}
    >
      {props.children}
    </span>
  );
}

/* ------------------------------------------------------------------ *
 * useUIStrict — like useUI() but throws with a friendlier error if the
 * UICapability is missing (typically a host-config issue).
 * ------------------------------------------------------------------ */

export function useUIStrict() {
  const ui = useUI();
  if (!ui) {
    throw new Error(
      '[platform-admin-web] UICapability is not registered. ' +
        'Wrap your app with a host that provides UIAdapter via RuntimeContextProvider.',
    );
  }
  return ui;
}
