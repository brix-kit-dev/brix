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

import { type CSSProperties, type MouseEvent, type ReactNode } from "react";
import { useTheme, useUI } from "@brix-sdk/runtime-sdk-react";
import type { DesignTokens } from "@brix-sdk/runtime-sdk-api-web";

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
        minHeight: "100vh",
        background: "transparent",
        padding: `${designTokens.space.xl} ${designTokens.space.md}`,
        fontFamily: designTokens.typography.fontFamily,
      }}
    >
      <style>
        {`
          .platform-admin-page-shell * { box-sizing: border-box; }
          .platform-admin-sr-only {
            position: absolute;
            width: 1px;
            height: 1px;
            padding: 0;
            margin: -1px;
            overflow: hidden;
            clip: rect(0, 0, 0, 0);
            white-space: nowrap;
            border: 0;
          }
          .platform-admin-icon-action {
            transition: background ${designTokens.motion.durationShort} ${designTokens.motion.easing},
              border-color ${designTokens.motion.durationShort} ${designTokens.motion.easing},
              color ${designTokens.motion.durationShort} ${designTokens.motion.easing};
          }
          .platform-admin-icon-action:hover:not(:disabled) {
            background: color-mix(in srgb, ${designTokens.colors.brand.primary} 8%, transparent) !important;
            border-color: color-mix(in srgb, ${designTokens.colors.brand.primary} 34%, ${designTokens.colors.border.default}) !important;
          }
          .platform-admin-icon-action-danger:hover:not(:disabled) {
            background: color-mix(in srgb, ${designTokens.colors.status.error} 10%, transparent) !important;
            border-color: color-mix(in srgb, ${designTokens.colors.status.error} 38%, ${designTokens.colors.border.default}) !important;
          }
          @media (max-width: ${designTokens.breakpoints.sm}px) {
            .platform-admin-page-shell { padding: ${designTokens.space.md} ${designTokens.space.xs} !important; }
          }
        `}
      </style>
      <div
        style={{
          width: "100%",
          maxWidth: props.maxWidth ?? 1440,
          margin: "0 auto",
        }}
      >
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
        display: "flex",
        gap: designTokens.space.sm,
        marginBottom: designTokens.space.sm,
        padding: designTokens.space.sm,
        flexWrap: "wrap",
        alignItems: "flex-end",
        borderRadius: designTokens.shape.lg,
        border: `1px solid color-mix(in srgb, ${designTokens.colors.border.default} 58%, transparent)`,
        background: designTokens.colors.surface.elevated,
      }}
    >
      {props.children}
    </div>
  );
}

export type SummaryTone = StatusKind | "brand" | "neutral";

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
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 170px), 1fr))",
        gap: designTokens.space.sm,
        marginBottom: designTokens.space.sm,
      }}
    >
      {props.items.map((item, index) => {
        const accent = summaryToneColor(designTokens, item.tone ?? "neutral");
        return (
          <div
            key={index}
            style={{
              minHeight: 78,
              padding: `${designTokens.space.sm} ${designTokens.space.md}`,
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
    case "brand":
      return tokens.colors.brand.primary;
    case "success":
      return tokens.colors.status.success;
    case "warning":
      return tokens.colors.status.warning;
    case "error":
      return tokens.colors.status.error;
    case "info":
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
    textAlign: "center",
    color: t.colors.text.secondary,
  };

  if (props.loading && (props.data === null || props.data === undefined)) {
    return <div style={center}>{props.loadingNode ?? "Loading…"}</div>;
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
    return <div style={center}>{props.emptyNode ?? "No data"}</div>;
  }
  if (props.isEmpty?.(props.data)) {
    return <div style={center}>{props.emptyNode ?? "No data"}</div>;
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
  align?: "left" | "center" | "right";
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
    : `${t.space.xs} ${t.space.md}`;

  return (
    <div
      style={{
        overflowX: "auto",
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
          width: "100%",
          borderCollapse: "collapse",
          fontFamily: t.typography.fontFamily,
        }}
      >
        <thead>
          <tr
            style={{
              background: `color-mix(in srgb, ${t.colors.brand.primary} 6%, ${t.colors.surface.elevated})`,
            }}
          >
            {props.columns.map((c) => (
              <th
                key={c.key}
                style={{
                  textAlign: c.align ?? "left",
                  padding: cellPad,
                  width: c.width,
                  color: t.colors.text.secondary,
                  fontWeight: 700,
                  borderBottom: `1px solid color-mix(in srgb, ${t.colors.border.default} 72%, transparent)`,
                  whiteSpace: "nowrap",
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
                    textAlign: c.align ?? "left",
                    verticalAlign: "middle",
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
        display: "flex",
        alignItems: "flex-end",
        justifyContent: "space-between",
        gap: t.space.md,
        flexWrap: "wrap",
        marginBottom: t.space.lg,
        paddingBottom: t.space.sm,
        borderBottom: `1px solid color-mix(in srgb, ${t.colors.brand.primary} 28%, transparent)`,
      }}
    >
      <div style={{ minWidth: 0, maxWidth: "100%" }}>
        <h1
          style={{
            margin: 0,
            color: t.colors.text.primary,
            fontSize: t.typography.titleMedium.fontSize,
            fontWeight: 750,
            lineHeight: t.typography.titleMedium.lineHeight,
            overflowWrap: "break-word",
          }}
        >
          {props.title}
        </h1>
        {props.subtitle ? (
          <p
            style={{
              margin: `${t.space.xs} 0 0`,
              color: t.colors.text.secondary,
              lineHeight: t.typography.bodySmall.lineHeight,
              overflowWrap: "break-word",
            }}
          >
            {props.subtitle}
          </p>
        ) : null}
      </div>
      {props.actions ? (
        <div
          style={{
            display: "flex",
            gap: t.space.sm,
            flexWrap: "wrap",
            justifyContent: "flex-end",
          }}
        >
          {props.actions}
        </div>
      ) : null}
    </header>
  );
}

export type ConnectedStepState = "done" | "current" | "pending";

export interface ConnectedStepItem {
  label: ReactNode;
  state: ConnectedStepState;
}

export interface ConnectedStepsProps {
  items: ReadonlyArray<ConnectedStepItem>;
  label: string;
}

export function ConnectedSteps(props: ConnectedStepsProps): JSX.Element {
  const { Icon } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;

  return (
    <div
      aria-label={props.label}
      style={{
        display: "grid",
        gridTemplateColumns: `repeat(${props.items.length}, minmax(0, 1fr))`,
        gap: 0,
        alignItems: "start",
        width: "100%",
        maxWidth: 760,
        margin: "0 auto",
      }}
    >
      {props.items.map((item, index) => {
        const done = item.state === "done";
        const current = item.state === "current";
        const active = done || current;
        const tone = done
          ? t.colors.status.success
          : current
            ? t.colors.brand.primary
            : t.colors.text.disabled;
        const previousDone = props.items[index - 1]?.state === "done";
        const nextActive =
          props.items[index + 1]?.state !== "pending" &&
          props.items[index + 1] !== undefined;
        const leftConnectorTone =
          previousDone && active
            ? t.colors.brand.primary
            : t.colors.border.default;
        const rightConnectorTone =
          done && nextActive ? t.colors.brand.primary : t.colors.border.default;

        return (
          <div key={index} style={{ minWidth: 0 }}>
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "minmax(0, 1fr) 34px minmax(0, 1fr)",
                alignItems: "center",
              }}
            >
              <div
                aria-hidden="true"
                style={{
                  height: 2,
                  borderRadius: t.shape.full,
                  background: index === 0 ? "transparent" : leftConnectorTone,
                }}
              />
              <div
                style={{
                  width: 34,
                  height: 34,
                  borderRadius: t.shape.full,
                  display: "grid",
                  placeItems: "center",
                  flex: "0 0 auto",
                  color: active
                    ? t.colors.brand.primaryContrast
                    : t.colors.text.secondary,
                  border: `1px solid ${active ? tone : t.colors.border.default}`,
                  background: active
                    ? `color-mix(in srgb, ${tone} 88%, ${t.colors.surface.elevated})`
                    : t.colors.surface.page,
                }}
              >
                {done ? (
                  <Icon
                    name="check"
                    size={18}
                    color={t.colors.brand.primaryContrast}
                  />
                ) : (
                  <span
                    style={{
                      fontSize: t.typography.labelSmall.fontSize,
                      fontWeight: 800,
                      lineHeight: 1,
                    }}
                  >
                    {index + 1}
                  </span>
                )}
              </div>
              <div
                aria-hidden="true"
                style={{
                  height: 2,
                  borderRadius: t.shape.full,
                  background:
                    index < props.items.length - 1
                      ? rightConnectorTone
                      : "transparent",
                }}
              />
            </div>
            <div
              style={{
                marginTop: t.space.xs,
                color: active ? t.colors.text.primary : t.colors.text.secondary,
                fontSize: t.typography.labelSmall.fontSize,
                fontWeight: active ? 700 : 600,
                lineHeight: t.typography.labelSmall.lineHeight,
                textAlign: "center",
                padding: `0 ${t.space.xs}`,
              }}
            >
              {item.label}
            </div>
          </div>
        );
      })}
    </div>
  );
}

export interface CircularIconBadgeProps {
  icon: string;
  tone?: "success" | "brand" | "info";
  label: string;
}

export function CircularIconBadge(props: CircularIconBadgeProps): JSX.Element {
  const { Icon } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tone =
    props.tone === "brand"
      ? t.colors.brand.primary
      : props.tone === "info"
        ? t.colors.status.info
        : t.colors.status.success;

  return (
    <div
      aria-label={props.label}
      style={{
        width: 48,
        height: 48,
        borderRadius: t.shape.full,
        display: "grid",
        placeItems: "center",
        color: tone,
        background: `color-mix(in srgb, ${tone} 12%, ${t.colors.surface.elevated})`,
        border: `1px solid color-mix(in srgb, ${tone} 30%, ${t.colors.border.default})`,
      }}
    >
      <Icon name={props.icon} size={24} color={tone} />
    </div>
  );
}

export interface MailDeliveryVisualProps {
  label: string;
}

export function MailDeliveryVisual(
  props: MailDeliveryVisualProps,
): JSX.Element {
  const { Icon } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;

  return (
    <div
      aria-label={props.label}
      style={{
        position: "relative",
        width: 96,
        maxWidth: "100%",
        height: 96,
        borderRadius: t.shape.md,
        border: `1px solid ${t.colors.border.subtle}`,
        background: t.colors.surface.elevated,
        display: "grid",
        placeItems: "center",
        boxShadow: t.shadows.sm,
      }}
    >
      <Icon name="mail" size={34} color={t.colors.brand.primary} />
      <div
        aria-hidden="true"
        style={{
          position: "absolute",
          right: -6,
          bottom: -6,
          width: 30,
          height: 30,
          borderRadius: t.shape.full,
          display: "grid",
          placeItems: "center",
          border: `1px solid ${t.colors.border.default}`,
          background: t.colors.surface.elevated,
        }}
      >
        <Icon name="check" size={18} color={t.colors.brand.primary} />
      </div>
    </div>
  );
}

export interface InsightPanelItem {
  icon: string;
  title: ReactNode;
  body: ReactNode;
  tone?: StatusKind | "brand";
}

export interface InsightPanelProps {
  title: ReactNode;
  items: ReadonlyArray<InsightPanelItem>;
}

export function InsightPanel(props: InsightPanelProps): JSX.Element {
  const { Icon } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;

  return (
    <aside
      style={{
        minWidth: 0,
        boxSizing: "border-box",
        padding: t.space.lg,
        borderRadius: t.shape.md,
        border: `1px solid color-mix(in srgb, ${t.colors.brand.primary} 18%, ${t.colors.border.default})`,
        background: `color-mix(in srgb, ${t.colors.brand.primary} 4%, ${t.colors.surface.elevated})`,
      }}
    >
      <h2
        style={{
          margin: 0,
          color: t.colors.text.primary,
          fontSize: t.typography.titleSmall.fontSize,
          fontWeight: 750,
          lineHeight: t.typography.titleSmall.lineHeight,
        }}
      >
        {props.title}
      </h2>
      <div style={{ display: "grid", gap: t.space.md, marginTop: t.space.lg }}>
        {props.items.map((item, index) => {
          const color = insightToneColor(t, item.tone ?? "brand");
          return (
            <div
              key={index}
              style={{
                display: "grid",
                gridTemplateColumns: "34px minmax(0, 1fr)",
                gap: t.space.sm,
                alignItems: "start",
                minWidth: 0,
              }}
            >
              <div
                style={{
                  width: 34,
                  height: 34,
                  borderRadius: t.shape.full,
                  display: "grid",
                  placeItems: "center",
                  background: `color-mix(in srgb, ${color} 10%, ${t.colors.surface.elevated})`,
                  border: `1px solid color-mix(in srgb, ${color} 28%, ${t.colors.border.default})`,
                  color,
                }}
              >
                <Icon name={item.icon} size={18} color={color} />
              </div>
              <div style={{ minWidth: 0 }}>
                <div
                  style={{
                    color: t.colors.text.primary,
                    fontSize: t.typography.label.fontSize,
                    fontWeight: 700,
                    lineHeight: t.typography.label.lineHeight,
                  }}
                >
                  {item.title}
                </div>
                <div
                  style={{
                    marginTop: 2,
                    color: t.colors.text.secondary,
                    fontSize: t.typography.bodySmall.fontSize,
                    lineHeight: t.typography.bodySmall.lineHeight,
                  }}
                >
                  {item.body}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </aside>
  );
}

function insightToneColor(
  tokens: DesignTokens,
  tone: StatusKind | "brand",
): string {
  if (tone === "brand") return tokens.colors.brand.primary;
  if (tone === "neutral") return tokens.colors.border.strong;
  return tokens.colors.status[tone];
}

export interface IconActionButtonProps {
  label: string;
  icon: string;
  tone?: "default" | "danger";
  disabled?: boolean;
  onClick?: (event: MouseEvent<HTMLButtonElement>) => void;
  "data-testid"?: string;
}

export function IconActionButton(props: IconActionButtonProps): JSX.Element {
  const { Button, Icon, Tooltip } = useUIStrict();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const danger = props.tone === "danger";
  const color = props.disabled
    ? t.colors.text.disabled
    : danger
      ? t.colors.status.error
      : t.colors.text.secondary;

  return (
    <Tooltip
      title={props.label}
      placement="top"
      arrow
      disabled={props.disabled}
    >
      <span style={{ display: "inline-flex" }}>
        <Button
          variant="text"
          size="small"
          disabled={props.disabled}
          onClick={props.onClick}
          className={`platform-admin-icon-action${danger ? " platform-admin-icon-action-danger" : ""}`}
          style={{
            minWidth: 34,
            width: 34,
            height: 34,
            padding: 0,
            borderRadius: t.shape.sm,
            border: `1px solid ${t.colors.border.subtle}`,
            color,
          }}
          data-testid={props["data-testid"]}
        >
          <Icon
            name={props.icon}
            size={18}
            color={color}
            aria-label={props.label}
          />
          <span className="platform-admin-sr-only">{props.label}</span>
        </Button>
      </span>
    </Tooltip>
  );
}

/* ------------------------------------------------------------------ *
 * StatusBadge — coloured pill with semantic colour mapping.
 * ------------------------------------------------------------------ */

export type StatusKind = "success" | "warning" | "error" | "info" | "neutral";

export interface StatusBadgeProps {
  kind: StatusKind;
  children: ReactNode;
}

export function StatusBadge(props: StatusBadgeProps): JSX.Element {
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const palette: Record<StatusKind, { bg: string; fg: string }> = {
    success: {
      bg: t.colors.status.success,
      fg: t.colors.brand.primaryContrast,
    },
    warning: {
      bg: t.colors.status.warning,
      fg: t.colors.brand.primaryContrast,
    },
    error: { bg: t.colors.status.error, fg: t.colors.brand.primaryContrast },
    info: { bg: t.colors.status.info, fg: t.colors.brand.primaryContrast },
    neutral: { bg: t.colors.border.default, fg: t.colors.text.primary },
  };
  const { bg, fg } = palette[props.kind];
  return (
    <span
      style={{
        display: "inline-block",
        padding: `2px ${t.space.sm}`,
        borderRadius: t.shape.full,
        background: bg,
        color: fg,
        fontSize: "0.75rem",
        fontWeight: 600,
        whiteSpace: "nowrap",
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
      "[platform-admin-web] UICapability is not registered. " +
        "Wrap your app with a host that provides UIAdapter via RuntimeContextProvider.",
    );
  }
  return ui;
}
