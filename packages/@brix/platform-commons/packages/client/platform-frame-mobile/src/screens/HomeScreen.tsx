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
 * Home Screen - Default Landing Screen
 *
 * Provides a default home screen with quick actions grid
 * and system status display for the mobile shell.
 *
 * @module @brix-sdk/platform-frame-mobile/screens
 * @since 3.3.0
 */

import React, { useContext, useMemo } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Platform } from 'react-native';
import { ShellContext } from '../providers/ShellProvider';

/**
 * Quick Action Configuration
 */
export interface QuickAction {
  /** Action identifier */
  key: string;
  /** Display label */
  label: string;
  /** Icon name */
  icon?: string;
  /** Action handler */
  onPress: () => void;
}

/**
 * Home Screen Props
 */
export interface HomeScreenProps {
  /** Quick actions to display in the grid */
  quickActions?: QuickAction[];
  /** Header content */
  renderHeader?: () => React.ReactNode;
  /** Whether to show system status */
  showStatus?: boolean;
}

/**
 * HomeScreen Component
 *
 * Default home screen for the mobile shell with configurable
 * quick actions and status display.
 *
 * @example
 * ```tsx
 * <HomeScreen
 *   quickActions={[
 *     { key: 'scan', label: 'Scan', onPress: handleScan },
 *     { key: 'search', label: 'Search', onPress: handleSearch }
 *   ]}
 *   showStatus
 * />
 * ```
 */
export function HomeScreen({
  quickActions = [],
  renderHeader,
  showStatus = false
}: HomeScreenProps): JSX.Element {
  const shellContext = useContext(ShellContext);

  const statusItems = useMemo(() => {
    if (!shellContext || !showStatus) return [];
    return [
      { label: 'Status', value: shellContext.state.initialized ? 'Ready' : 'Loading' },
      { label: 'Plugins', value: shellContext.state.pluginsLoaded ? 'Loaded' : 'Loading' }
    ];
  }, [shellContext, showStatus]);

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
      {/* Header */}
      {renderHeader ? (
        renderHeader()
      ) : (
        <View style={styles.header}>
          <Text style={styles.headerTitle}>
            {shellContext?.config.appName ?? 'Brix Platform'}
          </Text>
          <Text style={styles.headerSubtitle}>Welcome back</Text>
        </View>
      )}

      {/* Status Cards */}
      {showStatus && statusItems.length > 0 && (
        <View style={styles.statusContainer}>
          {statusItems.map(item => (
            <View key={item.label} style={styles.statusCard}>
              <Text style={styles.statusValue}>{item.value}</Text>
              <Text style={styles.statusLabel}>{item.label}</Text>
            </View>
          ))}
        </View>
      )}

      {/* Quick Actions Grid */}
      {quickActions.length > 0 && (
        <View style={styles.actionsContainer}>
          <Text style={styles.sectionTitle}>Quick Actions</Text>
          <View style={styles.actionsGrid}>
            {quickActions.map(action => (
              <TouchableOpacity
                key={action.key}
                style={styles.actionItem}
                onPress={action.onPress}
                activeOpacity={0.7}
              >
                <View style={styles.actionIconContainer}>
                  <Text style={styles.actionIcon}>
                    {action.icon ?? action.label.charAt(0).toUpperCase()}
                  </Text>
                </View>
                <Text style={styles.actionLabel} numberOfLines={1}>
                  {action.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5'
  },
  contentContainer: {
    padding: 16
  },
  header: {
    marginBottom: 24
  },
  headerTitle: {
    fontSize: 28,
    fontWeight: '700',
    color: '#1a1a1a',
    ...Platform.select({
      android: { fontFamily: 'sans-serif-medium' }
    })
  },
  headerSubtitle: {
    fontSize: 16,
    color: '#666666',
    marginTop: 4
  },
  statusContainer: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 24
  },
  statusCard: {
    flex: 1,
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2
  },
  statusValue: {
    fontSize: 24,
    fontWeight: '600',
    color: '#1a73e8'
  },
  statusLabel: {
    fontSize: 12,
    color: '#666666',
    marginTop: 4
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1a1a1a',
    marginBottom: 12
  },
  actionsContainer: {
    marginBottom: 24
  },
  actionsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12
  },
  actionItem: {
    width: '30%',
    alignItems: 'center',
    padding: 12
  },
  actionIconContainer: {
    width: 56,
    height: 56,
    borderRadius: 16,
    backgroundColor: '#e8f0fe',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8
  },
  actionIcon: {
    fontSize: 20,
    color: '#1a73e8',
    fontWeight: '600'
  },
  actionLabel: {
    fontSize: 12,
    color: '#333333',
    textAlign: 'center'
  }
});
