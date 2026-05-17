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
 * Settings Screen - User Preferences and Configuration
 *
 * Provides a default settings screen with toggle switches
 * and grouped configuration sections.
 *
 * @module @brix-sdk/platform-frame-mobile/screens
 * @since 3.3.0
 */

import React, { useState, useCallback } from 'react';
import { View, Text, ScrollView, Switch, TouchableOpacity, StyleSheet, Platform } from 'react-native';

/**
 * Settings Item Configuration
 */
export interface SettingsItemConfig {
  /** Item identifier */
  key: string;
  /** Display label */
  label: string;
  /** Item description */
  description?: string;
  /** Item type */
  type: 'toggle' | 'action' | 'info';
  /** Current value for toggle type */
  value?: boolean;
  /** Display value for info type */
  displayValue?: string;
  /** Handler for toggle change or action press */
  onPress?: () => void;
  /** Handler for toggle value change */
  onValueChange?: (value: boolean) => void;
}

/**
 * Settings Section Configuration
 */
export interface SettingsSectionConfig {
  /** Section identifier */
  key: string;
  /** Section title */
  title: string;
  /** Section items */
  items: SettingsItemConfig[];
}

/**
 * Settings Screen Props
 */
export interface SettingsScreenProps {
  /** Settings sections to display */
  sections?: SettingsSectionConfig[];
  /** Header content for the settings screen */
  renderHeader?: () => React.ReactNode;
}

/**
 * Individual Settings Item Component
 */
function SettingsItem({ item }: { item: SettingsItemConfig }): JSX.Element {
  const [toggleValue, setToggleValue] = useState(item.value ?? false);

  const handleToggle = useCallback((value: boolean) => {
    setToggleValue(value);
    item.onValueChange?.(value);
  }, [item]);

  return (
    <TouchableOpacity
      style={styles.settingsItem}
      onPress={item.type === 'action' ? item.onPress : undefined}
      disabled={item.type !== 'action'}
      activeOpacity={item.type === 'action' ? 0.7 : 1}
    >
      <View style={styles.settingsItemContent}>
        <Text style={styles.settingsItemLabel}>{item.label}</Text>
        {item.description && (
          <Text style={styles.settingsItemDescription}>{item.description}</Text>
        )}
      </View>
      {item.type === 'toggle' && (
        <Switch
          value={toggleValue}
          onValueChange={handleToggle}
          trackColor={{ false: '#d0d0d0', true: '#a8c7fa' }}
          thumbColor={toggleValue ? '#1a73e8' : '#f4f3f4'}
        />
      )}
      {item.type === 'info' && item.displayValue && (
        <Text style={styles.settingsItemValue}>{item.displayValue}</Text>
      )}
      {item.type === 'action' && (
        <Text style={styles.settingsItemChevron}>›</Text>
      )}
    </TouchableOpacity>
  );
}

/**
 * SettingsScreen Component
 *
 * Default settings screen for the mobile shell with
 * grouped sections and configurable items.
 *
 * @example
 * ```tsx
 * <SettingsScreen
 *   sections={[
 *     {
 *       key: 'preferences',
 *       title: 'Preferences',
 *       items: [
 *         { key: 'darkMode', label: 'Dark Mode', type: 'toggle', value: false },
 *         { key: 'language', label: 'Language', type: 'action', onPress: handleLanguage }
 *       ]
 *     }
 *   ]}
 * />
 * ```
 */
export function SettingsScreen({
  sections = [],
  renderHeader
}: SettingsScreenProps): JSX.Element {
  const defaultSections: SettingsSectionConfig[] = sections.length > 0 ? sections : [
    {
      key: 'preferences',
      title: 'Preferences',
      items: [
        { key: 'notifications', label: 'Notifications', type: 'toggle', value: true },
        { key: 'darkMode', label: 'Dark Mode', type: 'toggle', value: false }
      ]
    },
    {
      key: 'account',
      title: 'Account',
      items: [
        { key: 'profile', label: 'Profile', type: 'action' },
        { key: 'security', label: 'Security', type: 'action' }
      ]
    },
    {
      key: 'about',
      title: 'About',
      items: [
        { key: 'version', label: 'Version', type: 'info', displayValue: '3.3.0' },
        { key: 'licenses', label: 'Open Source Licenses', type: 'action' }
      ]
    }
  ];

  return (
    <ScrollView style={styles.container}>
      {/* Header */}
      {renderHeader ? (
        renderHeader()
      ) : (
        <View style={styles.header}>
          <Text style={styles.headerTitle}>Settings</Text>
        </View>
      )}

      {/* Sections */}
      {defaultSections.map(section => (
        <View key={section.key} style={styles.section}>
          <Text style={styles.sectionTitle}>{section.title}</Text>
          <View style={styles.sectionContent}>
            {section.items.map((item, index) => (
              <React.Fragment key={item.key}>
                <SettingsItem item={item} />
                {index < section.items.length - 1 && <View style={styles.separator} />}
              </React.Fragment>
            ))}
          </View>
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5'
  },
  header: {
    padding: 16,
    paddingTop: 8
  },
  headerTitle: {
    fontSize: 28,
    fontWeight: '700',
    color: '#1a1a1a',
    ...Platform.select({
      android: { fontFamily: 'sans-serif-medium' }
    })
  },
  section: {
    marginBottom: 24,
    paddingHorizontal: 16
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666666',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 8,
    paddingHorizontal: 4
  },
  sectionContent: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    overflow: 'hidden',
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 1
  },
  settingsItem: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16
  },
  settingsItemContent: {
    flex: 1
  },
  settingsItemLabel: {
    fontSize: 16,
    color: '#1a1a1a'
  },
  settingsItemDescription: {
    fontSize: 13,
    color: '#888888',
    marginTop: 2
  },
  settingsItemValue: {
    fontSize: 15,
    color: '#888888'
  },
  settingsItemChevron: {
    fontSize: 22,
    color: '#cccccc',
    fontWeight: '400'
  },
  separator: {
    height: 1,
    backgroundColor: '#f0f0f0',
    marginLeft: 16
  }
});
