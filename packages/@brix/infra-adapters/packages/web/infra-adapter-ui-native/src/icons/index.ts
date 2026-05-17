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
 * @file Icons Module Exports
 * @description Exports for the icon system including NativeIcon component and SVG registry.
 * @module @brix-sdk/infra-adapter-ui-native/icons
 * @version 3.1.0
 */

// Component export
export { NativeIcon, default } from './NativeIcon';

// SVG registry exports
export {
  type SvgIconDef,
  SVG_ICON_REGISTRY,
  getIconDef,
  hasIconDef,
  getAvailableIconNames,
  navigationIcons,
  applicationIcons,
  userIcons,
  businessIcons,
  fileIcons,
  actionIcons,
  statusIcons,
} from './svg-icons';
