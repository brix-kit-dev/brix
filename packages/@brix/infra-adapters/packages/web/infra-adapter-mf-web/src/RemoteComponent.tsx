/**
 * @file RemoteComponent - Remote Component Loader
 * @description Encapsulates lazy loading logic for Module Federation remote components
 * @module @brix/infra-adapter-mf-web/RemoteComponent
 * @version 3.2.0
 *
 * 【Architectural Position】
 * RemoteComponent is a React component provided by the Infra Adapter layer,
 * used for dynamically loading MF remote modules in the Host/Shell layer.
 *
 * 【Design Principles】
 * - Follows the v3.0.4 blueprint's infrastructure adapter layer positioning
 * - Uses React.lazy + Suspense for lazy loading
 * - Provides loading state and error boundary
 * - Supports component-level fallback display
 * - Built-in smart caching mechanism
 *
 * 【Usage Example】
 * ```tsx
 * import { RemoteComponent } from '@brix/infra-adapter-mf-web';
 *
 * <RemoteComponent
 *   remoteEntry="/plugins/identity/remoteEntry.js"
 *   exposePath="./pages/UserList"
 *   componentProps={{ userId: 123 }}
 *   fallback={<Skeleton />}
 * />
 * ```
 */

import {
  lazy,
  Suspense,
  Component,
  type ReactNode,
  type ComponentType,
  type ErrorInfo,
} from 'react';
import { mfLoader } from './mf-loader';

// ========== Type Definitions ==========

/**
 * RemoteComponent Props
 */
export interface RemoteComponentProps {
  /** Remote entry URL (remoteEntry.js) */
  remoteEntry: string;
  /** Exposed module path (e.g. ./pages/UserList) */
  exposePath: string;
  /** Props to pass to remote component */
  componentProps?: Record<string, unknown>;
  /** Content to display while loading */
  fallback?: ReactNode;
  /** Content to display on error (uses default error UI if not provided) */
  errorFallback?: ReactNode;
  /** Callback when component loads successfully */
  onLoad?: () => void;
  /** Callback when component fails to load */
  onError?: (error: Error) => void;
}

/**
 * Error boundary state
 */
interface ErrorBoundaryState {
  hasError: boolean;
  error?: Error;
}

/**
 * Error boundary props
 */
interface ErrorBoundaryProps {
  children: ReactNode;
  fallback?: ReactNode;
  onError?: (error: Error) => void;
}

// ========== Error Boundary Component ==========

/**
 * Remote Component Error Boundary
 *
 * Catches JavaScript errors in child component tree,
 * and displays a fallback UI instead of the crashed component tree.
 */
class RemoteErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    console.error('[RemoteComponent] Remote component loading failed:', error, errorInfo);
    this.props.onError?.(error);
  }

  render(): ReactNode {
    if (this.state.hasError) {
      // If custom error fallback is provided, use it
      if (this.props.fallback) {
        return this.props.fallback;
      }

      // Determine if this is a plugin unavailable error
      const errorMsg = this.state.error?.message || '';
      const isPluginUnavailable =
        errorMsg.includes('remoteEntry') ||
        errorMsg.includes('Container not found') ||
        errorMsg.includes('Failed to load') ||
        errorMsg.includes('Loading script failed') ||
        errorMsg.includes('Network error');

      // Friendly message for plugin unavailable
      if (isPluginUnavailable) {
        return (
          <div
            style={{
              padding: '48px 24px',
              textAlign: 'center',
              color: '#8c8c8c',
              backgroundColor: '#fafafa',
              borderRadius: '8px',
              margin: '16px',
              border: '1px dashed #d9d9d9',
            }}
          >
            <div style={{ fontSize: '48px', marginBottom: '16px' }}>🔌</div>
            <h3 style={{ margin: '0 0 8px 0', color: '#595959' }}>Plugin Temporarily Unavailable</h3>
            <p style={{ margin: 0, fontSize: '14px' }}>This plugin module is not started or is under development</p>
            <p style={{ margin: '8px 0 0 0', fontSize: '12px', color: '#bfbfbf' }}>
              Please start the corresponding plugin's dev server, or contact the administrator
            </p>
          </div>
        );
      }

      // Generic error message
      return (
        <div
          style={{
            padding: '24px',
            textAlign: 'center',
            color: '#ff4d4f',
            backgroundColor: '#fff2f0',
            borderRadius: '8px',
            margin: '16px',
          }}
        >
          <h3 style={{ margin: '0 0 8px 0' }}>Component Loading Failed</h3>
          <p style={{ margin: 0, fontSize: '14px', color: '#666' }}>
            {this.state.error?.message || 'Unknown error'}
          </p>
        </div>
      );
    }

    return this.props.children;
  }
}

// ========== Loading State Component ==========

/**
 * Default Loading Component
 *
 * Displays a spinning loading indicator
 */
function DefaultLoadingFallback(): ReactNode {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        padding: '48px',
        color: '#999',
      }}
    >
      <div
        style={{
          width: '32px',
          height: '32px',
          border: '3px solid #f0f0f0',
          borderTop: '3px solid #1890ff',
          borderRadius: '50%',
          animation: 'remote-component-spin 1s linear infinite',
        }}
      />
      <style>{`
        @keyframes remote-component-spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
}

// ========== Component Cache ==========

/**
 * Lazy Load Component Cache
 *
 * Uses remoteEntry + exposePath as cache key,
 * ensuring lazy load wrapper is created only once for the same remote component.
 */
const lazyComponentCache = new Map<string, ComponentType<Record<string, unknown>>>();

/**
 * Get or create lazy load component
 *
 * @param remoteEntry - Remote entry URL
 * @param exposePath - Exposed module path
 * @returns React lazy load component
 */
function getLazyComponent(
  remoteEntry: string,
  exposePath: string
): ComponentType<Record<string, unknown>> {
  const cacheKey = `${remoteEntry}::${exposePath}`;

  if (!lazyComponentCache.has(cacheKey)) {
    const LazyComponent = lazy(async () => {
      const module = await mfLoader(remoteEntry, exposePath);
      return { default: module.default as ComponentType<Record<string, unknown>> };
    });

    lazyComponentCache.set(cacheKey, LazyComponent);
  }

  return lazyComponentCache.get(cacheKey)!;
}

/**
 * Clear lazy load component cache
 *
 * Used for hot reload or plugin reload scenarios
 *
 * @param remoteEntry - Optional, specify to clear cache for a specific remote entry
 */
export function clearRemoteComponentCache(remoteEntry?: string): void {
  if (remoteEntry) {
    // Clear cache with specific prefix
    for (const key of lazyComponentCache.keys()) {
      if (key.startsWith(`${remoteEntry}::`)) {
        lazyComponentCache.delete(key);
      }
    }
  } else {
    // Clear all cache
    lazyComponentCache.clear();
  }
}

/**
 * Get current cached component count
 *
 * @returns Number of cached components
 */
export function getRemoteComponentCacheSize(): number {
  return lazyComponentCache.size;
}

// ========== Main Component ==========

/**
 * Remote Component Loader
 *
 * Dynamically loads remote components using Module Federation,
 * automatically handling loading state and error boundary.
 *
 * 【Features】
 * - React.lazy + Suspense lazy loading
 * - Built-in error boundary with graceful fallback
 * - Smart caching to avoid duplicate loading
 * - Friendly message when plugin unavailable
 * - Support for custom loading and error UI
 *
 * @example
 * ```tsx
 * // Basic usage
 * <RemoteComponent
 *   remoteEntry="http://localhost:3001/remoteEntry.js"
 *   exposePath="./pages/UserList"
 * />
 *
 * // Full usage
 * <RemoteComponent
 *   remoteEntry={plugin.remoteEntry}
 *   exposePath={route.exposePath}
 *   componentProps={{ userId: 123 }}
 *   fallback={<Skeleton />}
 *   errorFallback={<CustomError />}
 *   onLoad={() => console.log('Component loaded')}
 *   onError={(err) => reportError(err)}
 * />
 * ```
 *
 * @param props - Component props
 * @returns React node
 */
export function RemoteComponent({
  remoteEntry,
  exposePath,
  componentProps = {},
  fallback,
  errorFallback,
  onLoad,
  onError,
}: RemoteComponentProps): ReactNode {
  const LazyComponent = getLazyComponent(remoteEntry, exposePath);

  return (
    <RemoteErrorBoundary fallback={errorFallback} onError={onError}>
      <Suspense fallback={fallback ?? <DefaultLoadingFallback />}>
        <LazyComponent {...componentProps} ref={onLoad ? () => onLoad() : undefined} />
      </Suspense>
    </RemoteErrorBoundary>
  );
}

export default RemoteComponent;
