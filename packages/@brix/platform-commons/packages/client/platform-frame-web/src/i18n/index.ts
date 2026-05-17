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
 * @file Frame-web i18n resource registration
 * @description Registers translation bundles for enterprise-frame-web shell components.
 *   Called once during Host bootstrap before any shell UI renders.
 * @module @brix-sdk/platform-frame-web/i18n
 * @version 3.3.0
 *
 * Architecture:
 * - Translation resources are registered via I18nCapability.addResourceBundle()
 * - Namespace: "frame" — all keys prefixed with "frame." (e.g. frame.error.404.title)
 * - Supports zh-CN and en-US out of the box
 *
 * @see I18nCapability in runtime-sdk-api-web for the contract
 * @see bootstrap.tsx where this is called during Host initialization
 */

import type { I18nCapability } from '@brix-sdk/runtime-sdk-api-web';
import zhCN from './zh-CN';
import enUS from './en-US';

/**
 * Register enterprise-frame-web translation bundles with the I18n capability.
 *
 * Must be called once before shell components render. Typically invoked
 * from the Host bootstrap sequence after I18nCapability is registered.
 *
 * @param i18n - The I18nCapability instance from RuntimeContext
 */
export function registerFrameI18n(i18n: I18nCapability): void {
  i18n.addResourceBundle({
    locale: 'zh-CN',
    namespace: 'frame',
    resources: zhCN,
  });
  i18n.addResourceBundle({
    locale: 'en-US',
    namespace: 'frame',
    resources: enUS,
  });
}

export { zhCN, enUS };
