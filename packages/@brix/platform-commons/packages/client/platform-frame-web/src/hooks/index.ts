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
 * @file Hooks Export
 * @description Re-export hooks from @brix-sdk/runtime-sdk-react for backward compatibility
 * @module @brix-sdk/platform-frame-web/hooks
 * @version 3.1.0
 *
 * [v3.1 Migration Notes]
 * These hooks have been migrated to @brix-sdk/runtime-sdk-react as part of SDK consolidation.
 * This file re-exports them for backward compatibility.
 * New code should import directly from @brix-sdk/runtime-sdk-react.
 *
 * 【v3.1 迁移说明】
 * 这些 hooks 已迁移到 @brix-sdk/runtime-sdk-react 作为 SDK 整合的一部分。
 * 本文件重新导出它们以保持向后兼容性。
 * 新代码应直接从 @brix-sdk/runtime-sdk-react 导入。
 */

// Re-export from runtime-sdk-react
export {
  // Layout Hooks
  useLayout,
  useResponsive,
  
  // Theme Hooks
  useTheme,
  
  // Types
  type UseLayoutResult,
  type UseResponsiveResult,
  type UseThemeResult,
} from '@brix-sdk/runtime-sdk-react';
