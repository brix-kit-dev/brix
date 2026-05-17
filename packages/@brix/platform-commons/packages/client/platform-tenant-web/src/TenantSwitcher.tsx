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
 * @file TenantSwitcher — Inline Tenant Switch Dropdown Component
 * @description Renders a dropdown/select for switching between tenants.
 * Designed for use in headers, sidebars, or navigation areas where
 * the user needs quick access to tenant switching.
 *
 * @module @brix-sdk/platform-tenant-web/TenantSwitcher
 * @version 3.1.0
 *
 * [Architecture Layer]
 * Layer 2C: Platform Commons — UI component for inline tenant switching.
 *
 * [Design]
 * - Renders a native <select> by default for maximum compatibility
 * - Hidden when only one tenant is available (nothing to switch to)
 * - Uses useTenantSwitch() for switch logic with loading/error state
 *
 * @since 3.1.0
 */

import React, { useCallback } from 'react';
import { useTenant } from './useTenant';
import { useTenantSwitch } from './useTenantSwitch';

/**
 * Props for the TenantSwitcher component.
 */
export interface TenantSwitcherProps {
  /** CSS class for the select element */
  className?: string;

  /** Whether to hide when only one tenant is available (default: true) */
  hideOnSingle?: boolean;

  /** Callback invoked after successful tenant switch */
  onSwitched?: (tenantId: string) => void;
}

/**
 * Inline Tenant Switcher dropdown component.
 *
 * Renders a <select> element with available tenants. Selecting a
 * different tenant triggers switchTenant() and invokes the onSwitched
 * callback after completion.
 *
 * Hidden by default when only one tenant is available (no switching needed).
 *
 * @example
 * ```tsx
 * function AppHeader() {
 *   return (
 *     <header>
 *       <Logo />
 *       <TenantSwitcher
 *         className="tenant-switcher"
 *         onSwitched={(id) => console.log('Switched to', id)}
 *       />
 *       <UserMenu />
 *     </header>
 *   );
 * }
 * ```
 */
export const TenantSwitcher: React.FC<TenantSwitcherProps> = ({
  className,
  hideOnSingle = true,
  onSwitched,
}) => {
  const { tenant, availableTenants, isLoading } = useTenant();
  const { switchTo, isSwitching } = useTenantSwitch();

  const handleChange = useCallback(async (event: React.ChangeEvent<HTMLSelectElement>) => {
    const targetId = event.target.value;
    if (targetId && targetId !== tenant?.id) {
      await switchTo(targetId);
      onSwitched?.(targetId);
    }
  }, [tenant, switchTo, onSwitched]);

  // Hide when only one tenant is available
  if (hideOnSingle && availableTenants.length <= 1) {
    return null;
  }

  if (isLoading) {
    return (
      <select className={className} disabled data-testid="tenant-switcher-loading">
        <option>Loading...</option>
      </select>
    );
  }

  return (
    <select
      className={className}
      value={tenant?.id ?? ''}
      onChange={handleChange}
      disabled={isSwitching}
      data-testid="tenant-switcher"
    >
      {availableTenants.map((t) => (
        <option key={t.id} value={t.id}>
          {t.name}
        </option>
      ))}
    </select>
  );
};
