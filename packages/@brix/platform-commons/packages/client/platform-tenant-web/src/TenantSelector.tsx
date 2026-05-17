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
 * @file TenantSelector — Full-page Tenant Selection Component
 * @description Renders a full-page selector for users who belong to multiple
 * tenants and need to choose which one to enter. Typically shown after login
 * when no lastTenant is found or when the user's last tenant is no longer available.
 *
 * @module @brix-sdk/platform-tenant-web/TenantSelector
 * @version 3.1.0
 *
 * [Architecture Layer]
 * Layer 2C: Platform Commons — UI component for tenant selection.
 *
 * [Design]
 * - Framework-agnostic styling via CSS classes (no CSS-in-JS dependency)
 * - Renders headless by default; consumers can style via className props
 * - Uses useTenant() for available tenants and useTenantSwitch() for switching
 *
 * @since 3.1.0
 */

import React, { useCallback } from 'react';
import { useTenant } from './useTenant';
import { useTenantSwitch } from './useTenantSwitch';

/**
 * Props for the TenantSelector component.
 */
export interface TenantSelectorProps {
  /** Optional title text (default: "Select a Tenant") */
  title?: string;

  /** Optional subtitle text */
  subtitle?: string;

  /** CSS class for the outer container */
  className?: string;

  /** CSS class for each tenant card */
  itemClassName?: string;

  /** Callback invoked after successful tenant selection */
  onSelected?: (tenantId: string) => void;
}

/**
 * Full-page Tenant Selector component.
 *
 * Displays a list of available tenants as cards. When the user clicks
 * a card, the tenant switch is initiated. After successful switch,
 * the onSelected callback is invoked.
 *
 * @example
 * ```tsx
 * function LoginLanding() {
 *   const navigate = useNavigate();
 *
 *   return (
 *     <TenantSelector
 *       title="Welcome Back"
 *       subtitle="Choose an organization to continue"
 *       onSelected={() => navigate('/dashboard')}
 *     />
 *   );
 * }
 * ```
 */
export const TenantSelector: React.FC<TenantSelectorProps> = ({
  title = 'Select a Tenant',
  subtitle,
  className,
  itemClassName,
  onSelected,
}) => {
  const { availableTenants, isLoading } = useTenant();
  const { switchTo, isSwitching } = useTenantSwitch();

  const handleSelect = useCallback(async (tenantId: string) => {
    await switchTo(tenantId);
    onSelected?.(tenantId);
  }, [switchTo, onSelected]);

  if (isLoading) {
    return (
      <div className={className} data-testid="tenant-selector-loading">
        <div>Loading tenants...</div>
      </div>
    );
  }

  if (availableTenants.length === 0) {
    return (
      <div className={className} data-testid="tenant-selector-empty">
        <div>No tenants available</div>
      </div>
    );
  }

  return (
    <div className={className} data-testid="tenant-selector">
      <h2>{title}</h2>
      {subtitle && <p>{subtitle}</p>}
      <div>
        {availableTenants.map((t) => (
          <button
            key={t.id}
            className={itemClassName}
            onClick={() => handleSelect(t.id)}
            disabled={isSwitching}
            data-testid={`tenant-selector-item-${t.id}`}
          >
            <span>{t.name}</span>
            <span>{t.code}</span>
          </button>
        ))}
      </div>
    </div>
  );
};
