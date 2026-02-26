/**
 * @file React Router Adapter
 * @description Adapts react-router-dom to RouterService interface
 * @module @brix/platform-router-web/ReactRouterAdapter
 * @version 3.0.0
 * 
 * 【Architecture Notes】
 * ReactRouterAdapter is the react-router-dom implementation of RouterService.
 * Through the adapter pattern, it wraps react-router-dom API into a unified interface.
 * 
 * 【Initialization Flow】
 * 1. Host application creates ReactRouterAdapter instance within RouterProvider
 * 2. Uses RouterInjector component to inject navigate function
 * 3. NavigationCapability implementation performs navigation through this adapter
 * 
 * 【Technical Details】
 * - react-router-dom v6's navigate function can only be obtained within components
 * - navigate function is injected via setNavigator() method with delayed injection
 * - History listening is implemented via window.addEventListener('popstate')
 */

import type { NavigateFunction } from 'react-router-dom';
import type { RouterService, NavigateOptions, UrlChangeListener, Unsubscribe } from './RouterService';

/**
 * React Router Adapter
 * 
 * Wraps react-router-dom API into RouterService interface.
 * 
 * 【Usage Example】
 * ```typescript
 * // Initialize in Host application
 * const routerAdapter = new ReactRouterAdapter();
 * 
 * // Inject navigate within RouterProvider
 * function RouterInjector() {
 *   const navigate = useNavigate();
 *   useEffect(() => {
 *     routerAdapter.setNavigator(navigate);
 *   }, [navigate]);
 *   return null;
 * }
 * ```
 */
export class ReactRouterAdapter implements RouterService {
  /**
   * react-router-dom's navigate function
   * Injected by Host during initialization
   */
  private navigateFn: NavigateFunction | null = null;
  
  /**
   * URL change listener set
   */
  private listeners: Set<UrlChangeListener> = new Set();
  
  /**
   * popstate event handler
   */
  private popstateHandler: (() => void) | null = null;
  
  /**
   * Constructor
   * 
   * Initializes URL change listening
   */
  constructor() {
    this.setupPopstateListener();
  }
  
  /**
   * Set navigate function
   * 
   * Called by Host inside RouterProvider to inject react-router's navigate function.
   * This is a required initialization step, otherwise navigation operations will fail.
   * 
   * 【Technical Reason】
   * react-router-dom v6's useNavigate() can only be used inside Router component.
   * Therefore, navigate function must be obtained in component and injected to adapter.
   * 
   * @param navigate - react-router-dom's navigate function
   */
  setNavigator(navigate: NavigateFunction): void {
    this.navigateFn = navigate;
  }
  
  /**
   * Check if navigate function is injected
   * 
   * @throws Throws error if navigate is not injected
   */
  private ensureNavigator(): void {
    if (!this.navigateFn) {
      throw new Error(
        '[RouterService] navigate function not initialized, ' +
        'please ensure setNavigator() is called within RouterProvider'
      );
    }
  }
  
  /**
   * Navigate to specified URL
   * 
   * @param url - Target URL path
   * @param options - Navigation options
   */
  navigate(url: string, options?: NavigateOptions): void {
    this.ensureNavigator();
    
    this.navigateFn!(url, {
      replace: options?.replace ?? false,
      state: options?.state,
    });
    
    // Notify listeners
    this.notifyListeners();
  }
  
  /**
   * Replace current URL (no history entry)
   * 
   * @param url - Target URL path
   * @param state - State data to pass to target page
   */
  replace(url: string, state?: unknown): void {
    this.ensureNavigator();
    
    this.navigateFn!(url, {
      replace: true,
      state,
    });
    
    // Notify listeners
    this.notifyListeners();
  }
  
  /**
   * Go back to previous page
   */
  goBack(): void {
    window.history.back();
  }
  
  /**
   * Go forward to next page
   */
  goForward(): void {
    window.history.forward();
  }
  
  /**
   * Navigate to specific position in history
   * 
   * @param delta - Offset relative to current position
   */
  go(delta: number): void {
    window.history.go(delta);
  }
  
  /**
   * Get current URL
   * 
   * @returns Current URL path (including query string)
   */
  getCurrentUrl(): string {
    return window.location.pathname + window.location.search + window.location.hash;
  }
  
  /**
   * Get current path
   * 
   * @returns Current path (without query string and hash)
   */
  getCurrentPath(): string {
    return window.location.pathname;
  }
  
  /**
   * Get current query parameters
   * 
   * @returns Query parameters object
   */
  getQueryParams(): Record<string, string> {
    const params: Record<string, string> = {};
    const searchParams = new URLSearchParams(window.location.search);
    
    searchParams.forEach((value, key) => {
      params[key] = value;
    });
    
    return params;
  }
  
  /**
   * Get current hash
   * 
   * @returns Current hash (without # sign)
   */
  getHash(): string {
    return window.location.hash.slice(1);
  }
  
  /**
   * Subscribe to URL change events
   * 
   * @param listener - URL change listener
   * @returns Unsubscribe function
   */
  onUrlChange(listener: UrlChangeListener): Unsubscribe {
    this.listeners.add(listener);
    
    return () => {
      this.listeners.delete(listener);
    };
  }
  
  /**
   * Check if specified path matches current URL
   * 
   * Supports simple wildcard matching:
   * - `/booking/*` matches all paths starting with /booking/
   * - `/booking/:id` matches /booking/123 etc.
   * 
   * @param pattern - Path pattern
   * @returns Whether it matches
   */
  isActive(pattern: string): boolean {
    const currentPath = this.getCurrentPath();
    
    // Exact match
    if (pattern === currentPath) {
      return true;
    }
    
    // Wildcard match
    if (pattern.endsWith('/*')) {
      const prefix = pattern.slice(0, -2);
      return currentPath.startsWith(prefix);
    }
    
    // Parameter path matching (simple implementation)
    if (pattern.includes(':')) {
      const patternParts = pattern.split('/');
      const pathParts = currentPath.split('/');
      
      if (patternParts.length !== pathParts.length) {
        return false;
      }
      
      return patternParts.every((part, index) => {
        return part.startsWith(':') || part === pathParts[index];
      });
    }
    
    return false;
  }
  
  /**
   * Set up popstate event listener
   * 
   * Listens for browser forward/back operations
   */
  private setupPopstateListener(): void {
    this.popstateHandler = () => {
      this.notifyListeners();
    };
    
    window.addEventListener('popstate', this.popstateHandler);
  }
  
  /**
   * Notify all listeners that URL has changed
   */
  private notifyListeners(): void {
    const currentUrl = this.getCurrentUrl();
    
    this.listeners.forEach(listener => {
      try {
        listener(currentUrl);
      } catch (error) {
        console.error('[RouterService] URL change listener execution error:', error);
      }
    });
  }
  
  /**
   * Destroy adapter
   * 
   * Cleans up event listeners, releases resources
   */
  destroy(): void {
    if (this.popstateHandler) {
      window.removeEventListener('popstate', this.popstateHandler);
      this.popstateHandler = null;
    }
    
    this.listeners.clear();
    this.navigateFn = null;
  }
}
