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
 * useDeepLinking Hook
 *
 * Handles deep link URL parsing and navigation integration
 * for the mobile shell using the React Native Linking API.
 *
 * @module @brix-sdk/platform-frame-mobile/hooks
 * @since 3.3.0
 */

import { useEffect, useCallback, useRef } from 'react';
import { Linking } from 'react-native';
import type { DeepLinkConfig } from '../navigation/types';

/**
 * Deep Link Handler Function
 */
export type DeepLinkHandler = (path: string, params: Record<string, string>) => void;

/**
 * Deep Linking Hook Options
 */
export interface UseDeepLinkingOptions {
  /** Deep link configuration with URL scheme and patterns */
  config: DeepLinkConfig;
  /** Handler for matched deep links */
  onDeepLink: DeepLinkHandler;
  /** Whether deep linking is enabled */
  enabled?: boolean;
}

/**
 * Parse a deep link URL into path and parameters
 */
function parseDeepLinkUrl(url: string, scheme: string): { path: string; params: Record<string, string> } | null {
  const schemePrefix = `${scheme}://`;
  if (!url.startsWith(schemePrefix)) {
    return null;
  }

  const urlWithoutScheme = url.slice(schemePrefix.length);
  const [pathPart, queryPart] = urlWithoutScheme.split('?');
  const path = pathPart.replace(/^\/+|\/+$/g, '');

  const params: Record<string, string> = {};
  if (queryPart) {
    const searchParams = new URLSearchParams(queryPart);
    searchParams.forEach((value, key) => {
      params[key] = value;
    });
  }

  return { path, params };
}

/**
 * Match a URL path against configured patterns
 */
function matchPattern(path: string, patterns: Record<string, string>): { screen: string; params: Record<string, string> } | null {
  for (const [pattern, screen] of Object.entries(patterns)) {
    const patternParts = pattern.replace(/^\/+|\/+$/g, '').split('/');
    const pathParts = path.split('/');

    if (patternParts.length !== pathParts.length) continue;

    const params: Record<string, string> = {};
    let matched = true;

    for (let i = 0; i < patternParts.length; i++) {
      if (patternParts[i].startsWith(':')) {
        params[patternParts[i].slice(1)] = pathParts[i];
      } else if (patternParts[i] !== pathParts[i]) {
        matched = false;
        break;
      }
    }

    if (matched) {
      return { screen, params };
    }
  }

  return null;
}

/**
 * Hook for handling deep links in the mobile shell.
 *
 * Listens for incoming deep link URLs, parses them according
 * to the configured patterns, and invokes the provided handler.
 *
 * @param options Deep linking configuration and handlers
 *
 * @example
 * ```tsx
 * useDeepLinking({
 *   config: {
 *     scheme: 'brixapp',
 *     patterns: {
 *       'home': 'HomeScreen',
 *       'settings/:section': 'SettingsScreen',
 *       'plugin/:id': 'PluginScreen'
 *     }
 *   },
 *   onDeepLink: (path, params) => {
 *     navigation.navigate(path, params);
 *   }
 * });
 * ```
 */
export function useDeepLinking({
  config,
  onDeepLink,
  enabled = true
}: UseDeepLinkingOptions): void {
  const handlerRef = useRef(onDeepLink);
  handlerRef.current = onDeepLink;

  const handleUrl = useCallback((url: string) => {
    const parsed = parseDeepLinkUrl(url, config.prefix);
    if (!parsed) return;

    if (config.routes) {
      const match = matchPattern(parsed.path, config.routes);
      if (match) {
        handlerRef.current(match.screen, { ...parsed.params, ...match.params });
        return;
      }
    }

    handlerRef.current(parsed.path, parsed.params);
  }, [config.prefix, config.routes]);

  useEffect(() => {
    if (!enabled) return;

    // Handle initial URL (app opened via deep link)
    Linking.getInitialURL().then(url => {
      if (url) {
        handleUrl(url);
      }
    });

    // Handle URLs while the app is open
    const subscription = Linking.addEventListener('url', ({ url }) => {
      handleUrl(url);
    });

    return () => {
      subscription.remove();
    };
  }, [enabled, handleUrl]);
}
