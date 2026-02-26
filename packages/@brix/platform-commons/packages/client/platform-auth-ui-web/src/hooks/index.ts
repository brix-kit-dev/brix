/**
 * @file 认证 Hooks 导出
 * @description 导出所有认证相Hooks
 * @module @brix/platform-auth-web/hooks
 * @version 3.0.0
 */

export { useAuth, type UseAuthResult } from './useAuth';
export { 
  usePermission, 
  useAnyPermission, 
  useAllPermissions, 
  useRole,
  type UsePermissionResult,
} from './usePermission';
