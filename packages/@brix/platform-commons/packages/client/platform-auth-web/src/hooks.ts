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
