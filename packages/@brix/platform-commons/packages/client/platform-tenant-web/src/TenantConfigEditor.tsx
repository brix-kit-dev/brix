/**
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file TenantConfigEditor
 * @description Minimal tenant configuration editor for locale/timezone/theme.
 */

import React, { useEffect, useMemo, useState } from 'react';
import { useTenantConfig, useTheme, useUIOptional } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens, TenantSettings } from '@brix-sdk/runtime-sdk-api-web';

export interface TenantConfigEditorProps {
  readonly canEdit?: boolean;
  readonly onSaved?: () => void;
}

const LOCALE_OPTIONS = ['zh-CN', 'en-US', 'ja-JP'];
const TIMEZONE_OPTIONS = ['Asia/Shanghai', 'Asia/Tokyo', 'UTC', 'America/Los_Angeles'];
const THEME_OPTIONS = ['system', 'light', 'dark'];

function pickInitial(settings: TenantSettings | null, field: keyof TenantSettings): string {
  const value = settings?.[field];
  return typeof value === 'string' ? value : '';
}

/**
 * Edits tenant defaults that participate in the three-layer merge.
 */
export function TenantConfigEditor(props: TenantConfigEditorProps): JSX.Element {
  const ui = useUIOptional();
  const { tokens } = useTheme();
  const t = tokens as DesignTokens;
  const tenantConfig = useTenantConfig();
  const [form, setForm] = useState({
    defaultLocale: '',
    defaultTimezone: '',
    defaultTheme: '',
  });
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<Error | null>(null);

  useEffect(() => {
    setForm({
      defaultLocale: pickInitial(tenantConfig.tenantSettings, 'defaultLocale'),
      defaultTimezone: pickInitial(tenantConfig.tenantSettings, 'defaultTimezone'),
      defaultTheme: pickInitial(tenantConfig.tenantSettings, 'defaultTheme'),
    });
  }, [tenantConfig.tenantSettings]);

  const styles = useMemo(() => ({
    panel: {
      display: 'grid',
      gap: t.space.md,
      padding: t.space.lg,
      borderRadius: t.shape.md,
      border: `1px solid ${t.colors.border.default}`,
      background: t.colors.surface.elevated,
      fontFamily: t.typography.fontFamily,
    } as React.CSSProperties,
    grid: {
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 180px), 1fr))',
      gap: t.space.md,
    } as React.CSSProperties,
    label: {
      display: 'grid',
      gap: t.space.xs,
      color: t.colors.text.secondary,
      fontSize: t.typography.label.fontSize,
      fontWeight: 650,
    } as React.CSSProperties,
    select: {
      minHeight: 40,
      borderRadius: t.shape.sm,
      border: `1px solid ${t.colors.border.default}`,
      padding: `0 ${t.space.sm}`,
      color: t.colors.text.primary,
      background: t.colors.surface.elevated,
    } as React.CSSProperties,
    error: {
      padding: `${t.space.xs} ${t.space.sm}`,
      borderRadius: t.shape.sm,
      color: t.colors.status.error,
      background: `color-mix(in srgb, ${t.colors.status.error} 8%, ${t.colors.surface.elevated})`,
      border: `1px solid color-mix(in srgb, ${t.colors.status.error} 34%, ${t.colors.border.default})`,
      fontWeight: 600,
    } as React.CSSProperties,
    actions: {
      display: 'flex',
      gap: t.space.sm,
      justifyContent: 'flex-end',
      flexWrap: 'wrap',
    } as React.CSSProperties,
    button: {
      minHeight: 38,
      borderRadius: t.shape.sm,
      border: 0,
      padding: `0 ${t.space.md}`,
      color: t.colors.brand.primaryContrast,
      background: t.colors.brand.primary,
      fontWeight: 700,
      cursor: 'pointer',
    } as React.CSSProperties,
  }), [t]);

  async function save() {
    setSaving(true);
    setSaveError(null);
    try {
      await tenantConfig.updateSettings({
        defaultLocale: form.defaultLocale || undefined,
        defaultTimezone: form.defaultTimezone || undefined,
        defaultTheme: form.defaultTheme || undefined,
      });
      props.onSaved?.();
    } catch (error) {
      setSaveError(error instanceof Error ? error : new Error(String(error)));
    } finally {
      setSaving(false);
    }
  }

  function renderSelect(
    field: keyof typeof form,
    label: string,
    options: readonly string[],
  ) {
    if (ui?.Select) {
      return (
        <ui.Select
          label={label}
          value={form[field]}
          options={[
            { value: '', label: '继承上级默认值' },
            ...options.map((value) => ({ value, label: value })),
          ]}
          onChange={(value) => setForm((current) => ({
            ...current,
            [field]: String(value),
          }))}
          disabled={tenantConfig.isLoading || saving || props.canEdit === false}
          fullWidth
        />
      );
    }
    return (
      <label style={styles.label}>
        {label}
        <select
          style={styles.select}
          value={form[field]}
          onChange={(event) => setForm((current) => ({
            ...current,
            [field]: event.target.value,
          }))}
          disabled={tenantConfig.isLoading || saving || props.canEdit === false}
        >
          <option value="">继承上级默认值</option>
          {options.map((value) => (
            <option key={value} value={value}>{value}</option>
          ))}
        </select>
      </label>
    );
  }

  return (
    <section style={styles.panel} aria-busy={tenantConfig.isLoading || saving}>
      <div>
        <h2 style={{ margin: 0, color: t.colors.text.primary }}>租户默认配置</h2>
        <p style={{ margin: `${t.space.xs} 0 0`, color: t.colors.text.secondary }}>
          当前有效配置会按用户偏好、租户默认值、平台默认值三层合并。
        </p>
      </div>
      {tenantConfig.error || saveError ? (
        <div role="alert" style={styles.error}>
          {(tenantConfig.error ?? saveError)?.message}
        </div>
      ) : null}
      <div style={styles.grid}>
        {renderSelect('defaultLocale', '默认语言', LOCALE_OPTIONS)}
        {renderSelect('defaultTimezone', '默认时区', TIMEZONE_OPTIONS)}
        {renderSelect('defaultTheme', '默认主题', THEME_OPTIONS)}
      </div>
      <div style={styles.actions}>
        {ui?.Button ? (
          <ui.Button
            variant="primary"
            loading={saving}
            disabled={tenantConfig.isLoading || saving || props.canEdit === false}
            onClick={() => void save()}
          >
            保存配置
          </ui.Button>
        ) : (
          <button
            type="button"
            style={styles.button}
            disabled={tenantConfig.isLoading || saving || props.canEdit === false}
            onClick={() => void save()}
          >
            {saving ? '保存中...' : '保存配置'}
          </button>
        )}
      </div>
    </section>
  );
}
