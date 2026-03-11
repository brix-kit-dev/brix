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
 * @file useHttp Hook
 * @description HTTP Capability React Hook
 * @module @brix/runtime-sdk-react/hooks/useHttp
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Migrated from @brix/runtime-sdk-api-web to a standalone React binding package.
 */

import { useMemo, useState, useCallback } from 'react';
import type { 
  HttpCapability,
  HttpRequestConfig,
  HttpResponse 
} from '@brix/runtime-sdk-api-web';
import { useRuntimeContext } from './useRuntimeContext';

/**
 * HTTP Capability Type Identifier
 * @internal
 */
const HttpCapabilityType = Symbol.for('HttpCapability');

/**
 * HTTP Request State
 */
export interface HttpRequestState<T> {
  /** Response data */
  data: T | null;
  /** Whether loading */
  isLoading: boolean;
  /** Error message */
  error: Error | null;
}

/**
 * useHttp Hook Return Type
 */
export interface UseHttpResult {
  /** Send GET request */
  get: <T>(url: string, config?: Partial<HttpRequestConfig>) => Promise<HttpResponse<T>>;
  /** Send POST request */
  post: <T>(url: string, data?: unknown, config?: Partial<HttpRequestConfig>) => Promise<HttpResponse<T>>;
  /** Send PUT request */
  put: <T>(url: string, data?: unknown, config?: Partial<HttpRequestConfig>) => Promise<HttpResponse<T>>;
  /** Send DELETE request */
  delete: <T>(url: string, config?: Partial<HttpRequestConfig>) => Promise<HttpResponse<T>>;
  /** Send PATCH request */
  patch: <T>(url: string, data?: unknown, config?: Partial<HttpRequestConfig>) => Promise<HttpResponse<T>>;
  /** Generic request method */
  request: <T>(config: HttpRequestConfig) => Promise<HttpResponse<T>>;
}

/**
 * Get HTTP Capability Hook
 *
 * <p>Get HTTP request capability in React components.</p>
 *
 * <h3>Usage Example</h3>
 * ```tsx
 * function MyComponent() {
 *   const http = useHttp();
 *   const [users, setUsers] = useState([]);
 *   
 *   useEffect(() => {
 *     const loadUsers = async () => {
 *       const response = await http.get<User[]>('/api/users');
 *       setUsers(response.data);
 *     };
 *     loadUsers();
 *   }, [http]);
 *   // ...
 * }
 * ```
 *
 * @returns UseHttpResult HTTP methods
 * @throws Error if used outside RuntimeContextProvider
 * @throws Error if HTTP capability is not registered
 */
export function useHttp(): UseHttpResult {
  const context = useRuntimeContext();

  const httpCapability = useMemo(() => {
    const capability = context.getCapability<HttpCapability>(HttpCapabilityType);
    if (!capability) {
      throw new Error(
        '[runtime-sdk-react] HttpCapability is not registered in RuntimeContext'
      );
    }
    return capability;
  }, [context]);

  const request = useCallback(<T>(config: HttpRequestConfig): Promise<HttpResponse<T>> => {
    return httpCapability.request<T>(config);
  }, [httpCapability]);

  const get = useCallback(<T>(url: string, config?: Partial<HttpRequestConfig>): Promise<HttpResponse<T>> => {
    return httpCapability.request<T>({ ...config, url, method: 'GET' });
  }, [httpCapability]);

  const post = useCallback(<T>(url: string, data?: unknown, config?: Partial<HttpRequestConfig>): Promise<HttpResponse<T>> => {
    return httpCapability.request<T>({ ...config, url, method: 'POST', data });
  }, [httpCapability]);

  const put = useCallback(<T>(url: string, data?: unknown, config?: Partial<HttpRequestConfig>): Promise<HttpResponse<T>> => {
    return httpCapability.request<T>({ ...config, url, method: 'PUT', data });
  }, [httpCapability]);

  const del = useCallback(<T>(url: string, config?: Partial<HttpRequestConfig>): Promise<HttpResponse<T>> => {
    return httpCapability.request<T>({ ...config, url, method: 'DELETE' });
  }, [httpCapability]);

  const patch = useCallback(<T>(url: string, data?: unknown, config?: Partial<HttpRequestConfig>): Promise<HttpResponse<T>> => {
    return httpCapability.request<T>({ ...config, url, method: 'PATCH', data });
  }, [httpCapability]);

  return {
    get,
    post,
    put,
    delete: del,
    patch,
    request,
  };
}

/**
 * HTTP Request Hook with State Management
 *
 * <p>Send HTTP requests in React components with automatic loading state management.</p>
 *
 * <h3>Usage Example</h3>
 * ```tsx
 * function MyComponent() {
 *   const { data, isLoading, error, execute } = useHttpRequest<User[]>();
 *   const http = useHttp();
 *   
 *   useEffect(() => {
 *     execute(() => http.get<User[]>('/api/users'));
 *   }, [execute, http]);
 *   
 *   if (isLoading) return <Loading />;
 *   if (error) return <Error message={error.message} />;
 *   return <UserList users={data} />;
 * }
 * ```
 *
 * @typeParam T - Response data type
 * @returns Request state and execution method
 */
export function useHttpRequest<T>(): HttpRequestState<T> & {
  execute: (request: () => Promise<HttpResponse<T>>) => Promise<void>;
  reset: () => void;
} {
  const [state, setState] = useState<HttpRequestState<T>>({
    data: null,
    isLoading: false,
    error: null,
  });

  const execute = useCallback(async (request: () => Promise<HttpResponse<T>>) => {
    setState({ data: null, isLoading: true, error: null });
    try {
      const response = await request();
      setState({ data: response.data, isLoading: false, error: null });
    } catch (err) {
      setState({ 
        data: null, 
        isLoading: false, 
        error: err instanceof Error ? err : new Error(String(err)) 
      });
    }
  }, []);

  const reset = useCallback(() => {
    setState({ data: null, isLoading: false, error: null });
  }, []);

  return {
    ...state,
    execute,
    reset,
  };
}
