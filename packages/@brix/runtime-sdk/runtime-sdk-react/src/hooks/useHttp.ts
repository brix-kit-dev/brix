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
 * @module @brix-sdk/runtime-sdk-react/hooks/useHttp
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Migrated from @brix-sdk/runtime-sdk-api-web to a standalone React binding package.
 */

import { useMemo, useState, useCallback, useRef } from 'react';
import type { 
  HttpCapability,
  HttpRequestConfig,
  HttpResponse 
} from '@brix-sdk/runtime-sdk-api-web';
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
 *
 * Convenience methods (get, post, put, delete, patch) delegate to the
 * underlying {@link HttpCapability} and return unwrapped response data `T`.
 * Use {@link UseHttpResult.request} when you need the full
 * {@link HttpResponse} envelope (status, headers, etc.).
 */
export interface UseHttpResult {
  /** Send GET request — returns unwrapped response data */
  get: <T>(url: string, params?: Record<string, unknown>) => Promise<T>;
  /** Send POST request — returns unwrapped response data */
  post: <T>(url: string, data?: unknown) => Promise<T>;
  /** Send PUT request — returns unwrapped response data */
  put: <T>(url: string, data?: unknown) => Promise<T>;
  /** Send DELETE request — returns unwrapped response data */
  delete: <T>(url: string) => Promise<T>;
  /** Send PATCH request — returns unwrapped response data */
  patch: <T>(url: string, data?: unknown) => Promise<T>;
  /** Generic request method — returns full HttpResponse envelope */
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
 *       const users = await http.get<User[]>('/api/users');
 *       setUsers(users);
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

  // Stabilize httpCapability via useRef to prevent cascading reference changes.
  // context.getCapability returns the same singleton instance, but useMemo([context])
  // alone doesn't guarantee referential stability of the wrapper methods below.
  // Using a ref ensures the capability reference is always current without 
  // triggering dependency chain re-evaluations.
  const httpCapability = useMemo(() => {
    const capability = context.getCapability<HttpCapability>(HttpCapabilityType);
    if (!capability) {
      throw new Error(
        '[runtime-sdk-react] HttpCapability is not registered in RuntimeContext'
      );
    }
    return capability;
  }, [context]);

  const httpCapabilityRef = useRef(httpCapability);
  httpCapabilityRef.current = httpCapability;

  // Use stable callbacks that read from ref — these never change identity,
  // so downstream useMemo/useCallback/useEffect won't re-trigger.
  // Convenience methods delegate to HttpCapability's own methods which
  // properly unwrap HttpResponse and return T directly.
  const request = useCallback(<T>(config: HttpRequestConfig): Promise<HttpResponse<T>> => {
    return httpCapabilityRef.current.request<T>(config);
  }, []);

  const get = useCallback(<T>(url: string, params?: Record<string, unknown>): Promise<T> => {
    return httpCapabilityRef.current.get<T>(url, params);
  }, []);

  const post = useCallback(<T>(url: string, data?: unknown): Promise<T> => {
    return httpCapabilityRef.current.post<T>(url, data);
  }, []);

  const put = useCallback(<T>(url: string, data?: unknown): Promise<T> => {
    return httpCapabilityRef.current.put<T>(url, data);
  }, []);

  const del = useCallback(<T>(url: string): Promise<T> => {
    return httpCapabilityRef.current.delete<T>(url);
  }, []);

  const patch = useCallback(<T>(url: string, data?: unknown): Promise<T> => {
    return httpCapabilityRef.current.patch<T>(url, data);
  }, []);

  // Memoize the returned object so consumers (e.g. useMemo([http])) receive
  // a referentially stable value. Without this, every render creates a new
  // wrapper object, causing downstream dependency chains to cascade:
  //   new http → new repository → new fetchData → useEffect re-run → infinite loop
  return useMemo(() => ({
    get,
    post,
    put,
    delete: del,
    patch,
    request,
  }), [get, post, put, del, patch, request]);
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
 *     execute(() => http.request<User[]>({ url: '/api/users', method: 'GET' }));
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
