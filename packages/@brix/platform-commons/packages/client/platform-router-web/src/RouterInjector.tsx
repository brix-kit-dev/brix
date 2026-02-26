/**
 * @file Router Injector Component
 * @description Provides a component to inject navigate function within RouterProvider
 * @module @brix/platform-router-web/RouterInjector
 * @version 3.0.0
 * 
 * 【Architecture Notes】
 * RouterInjector is a helper component that obtains the navigate function inside
 * react-router's RouterProvider and injects it into ReactRouterAdapter.
 * 
 * 【Usage】
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
 * 【Usage Example】
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
