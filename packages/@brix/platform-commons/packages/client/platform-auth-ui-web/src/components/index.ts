/**
 * @file Authentication Components Export
 * @description Export all authentication-related components
 * @module @brix/platform-auth-web/components
 * @version 3.0.0
 */

export { AuthGuard, type AuthGuardProps } from './AuthGuard';
export { PermissionGate, type PermissionGateProps } from './PermissionGate';
export { 
  LoginForm, 
  type LoginFormProps,
  type LoginFormData,
  type LoginFormResult,
  type LoginFormBranding,
  type LoginFormLabels,
  type LoginFormFeatures,
  type SocialProvider,
} from './LoginForm';
export {
  RegisterForm,
  type RegisterFormProps,
  type RegisterFormData,
  type RegisterFormResult,
} from './RegisterForm';
