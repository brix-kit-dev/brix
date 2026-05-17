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
 * @file MobileTenantSelector — Full-screen Tenant Selection Component
 * @description Renders a full-screen selector for users who belong to multiple
 * tenants and need to choose which one to enter on mobile.
 *
 * @module @brix-sdk/platform-tenant-mobile/MobileTenantSelector
 * @version 3.2.0
 *
 * [Architecture Layer]
 * Layer 2C: Platform Commons — React Native UI component for tenant selection.
 *
 * [Design]
 * - Framework-agnostic styling via style props
 * - Uses useMobileTenant() for available tenants
 * - Uses useMobileTenantSwitch() for switching
 * - Full-screen layout suitable for mobile navigation
 *
 * @since 3.2.0
 */

import React, { useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  FlatList,
  ActivityIndicator,
  type ViewStyle,
  type TextStyle,
} from 'react-native';
import type { TenantInfo } from '@brix-sdk/runtime-sdk-api-mobile';
import { useMobileTenant } from './hooks/useMobileTenant';
import { useMobileTenantSwitch } from './hooks/useMobileTenantSwitch';
import type { MobileTenantSelectorProps } from './types/MobileTenantTypes';

/**
 * Full-screen Mobile Tenant Selector component.
 *
 * Displays a scrollable list of available tenants. When the user taps a
 * tenant card, the tenant switch is initiated. After successful switch,
 * the onSelected callback is invoked.
 *
 * @example
 * ```tsx
 * function LoginLanding() {
 *   const navigation = useNavigation();
 *
 *   return (
 *     <MobileTenantSelector
 *       title="Welcome Back"
 *       subtitle="Choose an organization to continue"
 *       onSelected={() => navigation.navigate('Dashboard')}
 *     />
 *   );
 * }
 * ```
 *
 * @since 3.2.0
 */
export const MobileTenantSelector: React.FC<MobileTenantSelectorProps> = ({
  title = 'Select Organization',
  subtitle,
  style,
  itemStyle,
  onSelected,
}) => {
  const { availableTenants, isLoading } = useMobileTenant();
  const { switchTo, isSwitching } = useMobileTenantSwitch();

  const handleSelect = useCallback(async (tenantId: string) => {
    await switchTo(tenantId);
    onSelected?.(tenantId);
  }, [switchTo, onSelected]);

  const renderItem = useCallback(({ item }: { item: TenantInfo }) => (
    <TouchableOpacity
      style={[styles.item, itemStyle as ViewStyle]}
      onPress={() => handleSelect(item.id)}
      disabled={isSwitching}
      testID={`tenant-selector-item-${item.id}`}
      accessibilityLabel={`Select ${item.name}`}
      accessibilityRole="button"
    >
      <Text style={styles.tenantName}>{item.name}</Text>
      <Text style={styles.tenantCode}>{item.code}</Text>
    </TouchableOpacity>
  ), [handleSelect, isSwitching, itemStyle]);

  if (isLoading) {
    return (
      <View style={[styles.container, style as ViewStyle]} testID="tenant-selector-loading">
        <ActivityIndicator size="large" />
        <Text style={styles.loadingText}>Loading tenants...</Text>
      </View>
    );
  }

  if (availableTenants.length === 0) {
    return (
      <View style={[styles.container, style as ViewStyle]} testID="tenant-selector-empty">
        <Text style={styles.emptyText}>No tenants available</Text>
      </View>
    );
  }

  return (
    <View style={[styles.container, style as ViewStyle]} testID="tenant-selector">
      <Text style={styles.title}>{title}</Text>
      {subtitle && <Text style={styles.subtitle}>{subtitle}</Text>}
      <FlatList
        data={availableTenants as TenantInfo[]}
        renderItem={renderItem}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
      />
    </View>
  );
};

// =========================================
// Default Styles
// =========================================

const styles: Record<string, ViewStyle | TextStyle> = {
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  title: {
    fontSize: 24,
    fontWeight: '600',
    marginBottom: 8,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    marginBottom: 24,
    textAlign: 'center',
  },
  listContent: {
    paddingVertical: 16,
    width: '100%',
  },
  item: {
    padding: 16,
    marginBottom: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#e0e0e0',
    backgroundColor: '#fff',
  },
  tenantName: {
    fontSize: 18,
    fontWeight: '500',
    marginBottom: 4,
  },
  tenantCode: {
    fontSize: 14,
    color: '#888',
  },
  loadingText: {
    marginTop: 16,
    fontSize: 16,
    color: '#666',
  },
  emptyText: {
    fontSize: 16,
    color: '#888',
  },
};
