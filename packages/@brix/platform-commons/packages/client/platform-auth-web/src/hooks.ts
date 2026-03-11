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
 * @file Re-export entry point for authentication hooks
 * @description Backward-compatible re-export from @brix/platform-auth-ui-web
 * @module @brix/platform-auth-web/hooks
 * @version 3.2.0
 * 
 * 【架构说明 Architecture Note】
 * v3.2 采用三包分离架构，Hooks 实际定义在 @brix/platform-auth-ui-web。
 * 本文件提供向后兼容的重导出入口。
 * 
 * In v3.2 architecture, hooks are defined in @brix/platform-auth-ui-web.
 * This file provides backward-compatible re-exports.
 * 
 * @example
 * ```typescript
 * // Legacy import (still works)
 * import { useAuth, usePermission } from '@brix/platform-auth-web/hooks';
 * 
 * // Recommended: import directly from sub-package
 * import { useAuth, usePermission } from '@brix/platform-auth-ui-web';
 * ```
 */

export {
  useAuth,
  type UseAuthResult,
  usePermission,
  useAnyPermission,
  useAllPermissions,
  useRole,
  type UsePermissionResult,
} from '@brix/platform-auth-ui-web';
