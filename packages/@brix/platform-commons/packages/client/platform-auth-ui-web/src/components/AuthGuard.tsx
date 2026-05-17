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
 * @file Authentication Guard Component
 * @description Protects routes that require authentication
 * @module @brix-sdk/platform-auth-web/components/AuthGuard
 * @version 3.0.0
 * 
 * Usage Scenario:
 * Wraps page content that requires login to access
 */

import { type ReactNode } from 'react';
import type { AuthCapability } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Authentication Guard Props
 */
export interface AuthGuardProps {
  /**
   * Authentication capability instance
   */
  auth: AuthCapability;
  
  /**
   * Child elements (protected content)
   */
  children: ReactNode;
  
  /**
   * Content to display when not authenticated
   */
  fallback?: ReactNode;
  
  /**
   * Redirect URL when not authenticated
   */
  redirectTo?: string;
  
  /**
   * Redirect function (provided externally)
   */
  onRedirect?: (url: string) => void;
}

/**
 * Authentication Guard Component
 * 
 * Protects routes that require authentication, unauthenticated users will see fallback or be redirected
 * 
 * Usage Example:
 * ```tsx
 * <AuthGuard 
 *   auth={authCapability}
 *   fallback={<LoginPrompt />}
 * >
 *   <BookingPage />
 * </AuthGuard>
 * ```
 */
export function AuthGuard({
  auth,
  children,
  fallback,
  redirectTo,
  onRedirect,
}: AuthGuardProps): ReactNode {
  const isAuthenticated = auth.isAuthenticated();
  
  if (!isAuthenticated) {
    // If redirect is configured
    if (redirectTo && onRedirect) {
      onRedirect(redirectTo);
      return null;
    }
    
    // Return fallback content
    return fallback ?? null;
  }
  
  return children;
}
