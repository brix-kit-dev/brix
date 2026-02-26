/**
 * @file Google OAuth Factory
 * @description Singleton factory functions for GoogleOAuthService
 * @module @brix/platform-auth-web/services/google-oauth/google-oauth-factory
 * @version 3.2.0
 * 
 * Extracted from GoogleOAuthService.ts as part of v3.2 architecture refactoring
 * to keep each file under 500 lines per code quality guidelines.
 * 
 * 【中文技术要点】
 * 单例工厂模式确保整个应用共享一个 OAuth 服务实例。
 * 这对于维护一致的认证状态和避免重复初始化很重要。
 */

import type { GoogleOAuthConfig } from './google-types';
import { GoogleOAuthService } from './GoogleOAuthService';

/** Singleton instance */
let googleAuthInstance: GoogleOAuthService | null = null;

/**
 * Get Google OAuth service instance
 * 
 * Returns the singleton instance of GoogleOAuthService.
 * If not initialized, creates one with the provided config.
 * 
 * @param config - Configuration (required on first call)
 * @returns GoogleOAuthService instance
 * @throws Error when not initialized and no config provided
 */
export function getGoogleAuthService(config?: GoogleOAuthConfig): GoogleOAuthService {
  if (!googleAuthInstance && config) {
    googleAuthInstance = new GoogleOAuthService(config);
  }
  if (!googleAuthInstance) {
    throw new Error('Google OAuth service not initialized, please provide configuration first');
  }
  return googleAuthInstance;
}

/**
 * Initialize Google OAuth service
 * 
 * Creates or replaces the singleton instance with a new configuration.
 * 
 * @param config - Google OAuth configuration
 * @returns GoogleOAuthService instance
 */
export function initGoogleAuth(config: GoogleOAuthConfig): GoogleOAuthService {
  googleAuthInstance = new GoogleOAuthService(config);
  return googleAuthInstance;
}

/**
 * Reset Google OAuth service
 * 
 * Clears the singleton instance. Mainly for testing purposes.
 */
export function resetGoogleAuth(): void {
  googleAuthInstance = null;
}
