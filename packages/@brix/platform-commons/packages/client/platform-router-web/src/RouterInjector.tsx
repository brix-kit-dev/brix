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
 * @file Router Injector Component
 * @description Provides a component to inject navigate function within RouterProvider
 * @module @brix-sdk/platform-router-web/RouterInjector
 * @version 3.0.0
 * 
 * ¡¾Architecture Notes¡¿
 * RouterInjector is a helper component that obtains the navigate function inside
 * react-router's RouterProvider and injects it into ReactRouterAdapter.
 * 
 * ¡¾Usage¡¿
 * This component should be placed inside RouterProvider as a child component.
 * It renders no UI, only handles the injection functionality.
 */

import { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import type { ReactRouterAdapter } from './ReactRouterAdapter';

/**
 * RouterInjector component props
 */
export interface RouterInjectorProps {
  /**
   * ReactRouterAdapter instance
   * 
   * The navigate function will be injected into this adapter
   */
  adapter: ReactRouterAdapter;
  
  /**
   * URL change callback
   * 
   * Triggered on each URL change, can be used to update navigation history
   */
  onUrlChange?: (url: string) => void;
}

/**
 * Router Injector Component
 * 
 * Obtains navigate function inside RouterProvider and injects it into ReactRouterAdapter.
 * 
 * ¡¾Usage Example¡¿
 * ```tsx
 * const routerAdapter = new ReactRouterAdapter();
 * 
 * function App() {
 *   return (
 *     <RouterProvider router={router}>
 *       <RouterInjector adapter={routerAdapter} />
 *       <AppContent />
 *     </RouterProvider>
 *   );
 * }
 * ```
 * 
 * @param props - Component props
 * @returns null (renders nothing)
 */
export function RouterInjector({ adapter, onUrlChange }: RouterInjectorProps): null {
  const navigate = useNavigate();
  const location = useLocation();
  
  // Inject navigate function
  useEffect(() => {
    adapter.setNavigator(navigate);
  }, [adapter, navigate]);
  
  // Listen for URL changes
  useEffect(() => {
    if (onUrlChange) {
      const url = location.pathname + location.search + location.hash;
      onUrlChange(url);
    }
  }, [location, onUrlChange]);
  
  return null;
}
