/**
 * @file DynamicMenuProvider
 * @description Dynamic Menu React Context Provider
 * @module @brix/platform-navigation-web/manifest/DynamicMenuProvider
 * @version 3.0.0
 *
 * 【Design Notes】
 * Following v3.0.4 blueprint Manifest-Driven architecture:
 * - Provides React Context for menu data
 * - Supports permission-aware menu filtering
 * - Manages menu expand/collapse state
 * - Integrates with MenuRegistry and ManifestAggregator
 *
 * 【Usage】
 * ```tsx
 * // Host layer usage
 * <DynamicMenuProvider pluginConfigs={pluginConfigs} permissionChecker={checkPerm}>
 *   <App />
 * </DynamicMenuProvider>
 *
 * // Component usage
 * const { menus, activeMenuId, expandedKeys } = useDynamicMenu();
 * ```
 */

import {
  createContext,
  useContext,
  useState,
  useEffect,
  useMemo,
  useCallback,
  type ReactNode,
  type FC,
} from 'react';

import type { AggregatedMenu, AggregatedManifest, HostPluginConfig, AggregatedRoute } from './types';
import { MenuRegistry, type PermissionChecker } from './MenuRegistry';
import { ManifestAggregator } from './ManifestAggregator';

/**
 * Menu Context Value
 */
export interface DynamicMenuContextValue {
  /** Visible menu tree (after permission filtering) */
  menus: AggregatedMenu[];

  /** All routes */
  routes: AggregatedRoute[];

  /** Currently active menu ID */
  activeMenuId: string | undefined;

  /** Expanded menu keys */
  expandedKeys: string[];

  /** Set expanded menu keys */
  setExpandedKeys: (keys: string[]) => void;

  /** Toggle menu expanded state */
  toggleExpanded: (menuId: string) => void;

  /** pageId to route path mapping */
  pageIdToPath: Map<string, string>;

  /** Navigate by pageId */
  navigateToPage: (pageId: string) => void;

  /** Menu registry instance (for advanced usage) */
  menuRegistry: MenuRegistry;

  /** Aggregation result */
  aggregatedManifest: AggregatedManifest | null;

  /** Loading state */
  loading: boolean;
}

/**
 * Create Context
 */
const DynamicMenuContext = createContext<DynamicMenuContextValue | null>(null);

/**
 * DynamicMenuProvider Props
 */
export interface DynamicMenuProviderProps {
  /** Child components */
  children: ReactNode;

  /** Plugin configuration list */
  pluginConfigs: HostPluginConfig[];

  /** Permission check function */
  permissionChecker?: PermissionChecker;

  /** Current route path */
  currentPath: string;

  /** Navigation function */
  navigate: (path: string) => void;

  /** Aggregation complete callback */
  onAggregated?: (result: AggregatedManifest) => void;
}

/**
 * Dynamic Menu Provider
 *
 * Provides React Context for menu data, integrating menu aggregation and permission filtering.
 */
export const DynamicMenuProvider: FC<DynamicMenuProviderProps> = ({
  children,
  pluginConfigs,
  permissionChecker,
  currentPath,
  navigate,
  onAggregated,
}) => {
  // State
  const [loading, setLoading] = useState(true);
  const [aggregatedManifest, setAggregatedManifest] = useState<AggregatedManifest | null>(null);
  const [expandedKeys, setExpandedKeys] = useState<string[]>([]);

  // Create MenuRegistry instance
  const menuRegistry = useMemo(() => {
    return new MenuRegistry({
      permissionChecker,
      onMenusChanged: () => {
        // Callback when menus change (can be used for logging/monitoring)
      },
    });
  }, [permissionChecker]);

  // Create ManifestAggregator instance
  const aggregator = useMemo(() => {
    return new ManifestAggregator({
      onAggregated: (result) => {
        onAggregated?.(result);
      },
    });
  }, [onAggregated]);

  // Aggregate plugin manifests
  useEffect(() => {
    setLoading(true);
    try {
      const result = aggregator.aggregate(pluginConfigs);
      setAggregatedManifest(result);
      menuRegistry.register(result.menuTree);

      // Initialize expanded menus
      const initialExpanded = menuRegistry.getExpandedKeys(currentPath);
      setExpandedKeys(initialExpanded);
    } catch (error) {
      console.error('[DynamicMenuProvider] Aggregation failed:', error);
    } finally {
      setLoading(false);
    }
  }, [pluginConfigs, aggregator, menuRegistry, currentPath]);

  // Get visible menus
  const menus = useMemo(() => {
    return menuRegistry.getVisibleMenus();
  }, [menuRegistry, aggregatedManifest]);

  // Get all routes
  const routes = useMemo(() => {
    return aggregatedManifest?.routes ?? [];
  }, [aggregatedManifest]);

  // Currently active menu
  const activeMenuId = useMemo(() => {
    return menuRegistry.getActiveMenuId(currentPath);
  }, [menuRegistry, currentPath]);

  // Update expanded state when path changes
  useEffect(() => {
    const newExpanded = menuRegistry.getExpandedKeys(currentPath);
    // Merge new expanded state, preserving user-manually-expanded menus
    setExpandedKeys(prev => {
      const merged = new Set([...prev, ...newExpanded]);
      return Array.from(merged);
    });
  }, [currentPath, menuRegistry]);

  // pageId to path mapping
  const pageIdToPath = useMemo(() => {
    return aggregatedManifest?.pageIdToPath ?? new Map();
  }, [aggregatedManifest]);

  // Toggle expanded state
  const toggleExpanded = useCallback((menuId: string) => {
    setExpandedKeys(prev => {
      if (prev.includes(menuId)) {
        return prev.filter(id => id !== menuId);
      }
      return [...prev, menuId];
    });
  }, []);

  // Navigate by pageId
  const navigateToPage = useCallback((pageId: string) => {
    const path = pageIdToPath.get(pageId);
    if (path) {
      navigate(path);
    } else {
      console.warn(`[DynamicMenuProvider] Page not found: ${pageId}`);
    }
  }, [pageIdToPath, navigate]);

  const value: DynamicMenuContextValue = {
    menus,
    routes,
    activeMenuId,
    expandedKeys,
    setExpandedKeys,
    toggleExpanded,
    pageIdToPath,
    navigateToPage,
    menuRegistry,
    aggregatedManifest,
    loading,
  };

  return (
    <DynamicMenuContext.Provider value={value}>
      {children}
    </DynamicMenuContext.Provider>
  );
};

/**
 * Use Dynamic Menu Hook
 *
 * @throws Throws error if used outside DynamicMenuProvider
 */
export function useDynamicMenu(): DynamicMenuContextValue {
  const context = useContext(DynamicMenuContext);
  if (!context) {
    throw new Error('useDynamicMenu must be used within DynamicMenuProvider');
  }
  return context;
}

/**
 * Use Menu Data (Simplified Hook)
 *
 * Returns only the data needed for rendering menus
 */
export function useMenuData() {
  const { menus, activeMenuId, expandedKeys, toggleExpanded } = useDynamicMenu();
  return { menus, activeMenuId, expandedKeys, toggleExpanded };
}

/**
 * Use Route Data (Simplified Hook)
 *
 * Returns only route-related data
 */
export function useRouteData() {
  const { routes, pageIdToPath, navigateToPage } = useDynamicMenu();
  return { routes, pageIdToPath, navigateToPage };
}
