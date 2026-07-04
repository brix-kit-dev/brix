/**
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * @file TenantSelector.tsx
 * @description S5+ — 登录第二阶段（SELECT_TENANT）租户选择 UI。
 *   本组件是纯展示组件：不持有 fetch / token / 路由，只渲染列表并把
 *   选择回调暴露给上层（典型上层是 {@link useLoginCoordinator}）。
 *
 * @module @brix-sdk/platform-auth-ui-web/components/TenantSelector
 * @since 3.2.0
 */
import React, { useCallback } from 'react';
import type { TenantOption } from '@brix-sdk/runtime-sdk-api-web';

export interface TenantSelectorLabels {
  readonly title?: string;
  readonly subtitle?: string;
  readonly emptyHint?: string;
  readonly errorRetry?: string;
  readonly roleActor?: string;
  readonly roleSubject?: string;
}

export interface TenantSelectorProps {
  /** 后端 `/api/auth/login` 返回的 `tenantOptions`。 */
  readonly options: readonly TenantOption[];
  /** Legacy tenant-id selection callback. Used only when no selectionTicket exists. */
  readonly onSelect: (tenantId: string, option: TenantOption) => void;
  /** Phase 3 context-ticket selection callback. Preferred when selectionTicket exists. */
  readonly onSelectContext?: (selectionTicket: string, option: TenantOption) => void;
  /** 选择正在提交时禁用列表（典型为 `/select-tenant` 请求 in-flight）。 */
  readonly loading?: boolean;
  /** 上一次选择失败的错误消息（由上层透传，用于在头部展示）。 */
  readonly error?: string | null;
  /** 文案覆盖，默认中文。 */
  readonly labels?: TenantSelectorLabels;
  /** 自定义类名，便于宿主应用接管样式。 */
  readonly className?: string;
}

const DEFAULT_LABELS: Required<TenantSelectorLabels> = {
  title: '选择租户',
  subtitle: '您归属于多个租户，请选择本次登录使用的租户。',
  emptyHint: '当前账号未归属于任何活跃租户，请联系管理员。',
  errorRetry: '请重试或更换租户。',
  roleActor: 'B 端从业者',
  roleSubject: 'C 端服务对象',
};

/**
 * 渲染租户列表，每行展示：租户名 + 编码 + 角色类型徽标 + 最近访问时间。
 * 点击/回车任一项触发 `onSelect`。
 */
export const TenantSelector: React.FC<TenantSelectorProps> = ({
  options,
  onSelect,
  onSelectContext,
  loading = false,
  error,
  labels,
  className,
}) => {
  const text = { ...DEFAULT_LABELS, ...(labels ?? {}) };

  const handleSelect = useCallback(
    (option: TenantOption) => {
      if (loading) return;
      if (option.selectionTicket && onSelectContext) {
        onSelectContext(option.selectionTicket, option);
        return;
      }
      onSelect(option.tenantId, option);
    },
    [loading, onSelect, onSelectContext],
  );

  if (options.length === 0) {
    return (
      <div className={className} role="status">
        <h2>{text.title}</h2>
        <p>{text.emptyHint}</p>
      </div>
    );
  }

  return (
    <div className={className} aria-busy={loading}>
      <h2>{text.title}</h2>
      <p>{text.subtitle}</p>
      {error ? (
        <div role="alert" style={{ color: '#c0392b', marginBottom: 12 }}>
          {error}
          <span style={{ marginLeft: 8, opacity: 0.7 }}>{text.errorRetry}</span>
        </div>
      ) : null}
      <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
        {options.map((opt) => {
          const roleLabel =
            opt.roleType === 'actor' ? text.roleActor : text.roleSubject;
          return (
            <li key={opt.tenantId} style={{ marginBottom: 8 }}>
              <button
                type="button"
                disabled={loading}
                onClick={() => handleSelect(opt)}
                style={{
                  width: '100%',
                  textAlign: 'left',
                  padding: '12px 16px',
                  border: '1px solid #d0d7de',
                  borderRadius: 6,
                  background: loading ? '#f6f8fa' : '#fff',
                  cursor: loading ? 'not-allowed' : 'pointer',
                }}
              >
                <div style={{ fontWeight: 600 }}>
                  {opt.tenantName}
                  <span
                    style={{
                      marginLeft: 8,
                      fontSize: 12,
                      color: '#57606a',
                      fontWeight: 400,
                    }}
                  >
                    @{opt.tenantCode}
                  </span>
                </div>
                <div style={{ fontSize: 12, color: '#57606a', marginTop: 4 }}>
                  <span
                    style={{
                      display: 'inline-block',
                      padding: '2px 6px',
                      borderRadius: 10,
                      background: opt.roleType === 'actor' ? '#dbeafe' : '#fef3c7',
                      color: opt.roleType === 'actor' ? '#1e40af' : '#92400e',
                      marginRight: 8,
                    }}
                  >
                    {roleLabel}
                  </span>
                  {opt.role ?? opt.subRole ? <span>{opt.role ?? opt.subRole}</span> : null}
                  {opt.lastAccessAt ? (
                    <span style={{ float: 'right' }}>
                      {new Date(opt.lastAccessAt).toLocaleString()}
                    </span>
                  ) : null}
                </div>
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
};

export default TenantSelector;
