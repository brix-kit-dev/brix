# @brix/infra-adapter-http-web

Brix Platform HTTP Infrastructure Adapter - HTTP library-agnostic capabilities for web applications.

## Overview

This package provides common HTTP utilities that are decoupled from any specific HTTP library implementation. It can be used with `fetch`, `axios`, or any other HTTP client.

## Features

- **Retry Mechanism**: Exponential backoff with random jitter
- **Cache Capability**: TTL-based in-memory caching with LFU eviction
- **Error Handling**: Unified HTTP error representation
- **Type Safety**: Full TypeScript support

## Installation

```bash
pnpm add @brix/infra-adapter-http-web
```

## Architecture

This package belongs to the infrastructure adapter layer (Layer 2.5) of v3.0 architecture:

```
Layer 3:     Host (Configuration Only)
Layer 2.5:   infra-adapter-http-web ← This package
Layer 2:     runtime-sdk-api-web (Capability Contracts)
```

## Usage

### Retry Mechanism

```typescript
import { withRetry, createRetryable } from '@brix/infra-adapter-http-web';

// Basic retry
const result = await withRetry(
  () => fetch('/api/users'),
  { maxRetries: 3 }
);

// With callbacks
const result = await withRetry(
  () => fetch('/api/data'),
  {
    maxRetries: 3,
    baseDelay: 1000,
    onRetry: (error, attempt, delay) => {
      console.log(`Retry ${attempt}, waiting ${delay}ms`);
    }
  }
);

// Pre-configured retryer
const retryable = createRetryable({
  maxRetries: 5,
  baseDelay: 500
});

const users = await retryable(() => fetchUsers());
const orders = await retryable(() => fetchOrders());
```

### Cache

```typescript
import { SimpleCache, withCache, generateCacheKey } from '@brix/infra-adapter-http-web';

// Create cache instance
const cache = new SimpleCache({
  defaultTTL: 60000,  // 1 minute
  maxSize: 100
});

// Set/Get cache
cache.set('users:list', userData);
const users = cache.get<User[]>('users:list');

// With async function
const result = await withCache(
  'users:list',
  () => fetch('/api/users').then(r => r.json()),
  cache,
  30000  // 30 second TTL
);

// Clear by prefix
cache.clearByPrefix('users:');

// Destroy instance
cache.destroy();
```

### Error Handling

```typescript
import { HttpError, HttpErrorCode } from '@brix/infra-adapter-http-web';

try {
  await fetch('/api/data');
} catch (error) {
  if (error instanceof HttpError) {
    if (error.retryable) {
      // Can retry
    }
    
    switch (error.code) {
      case HttpErrorCode.NETWORK_ERROR:
        // Handle network error
        break;
      case HttpErrorCode.TIMEOUT:
        // Handle timeout
        break;
    }
  }
}

// Create specific errors
const networkError = HttpError.networkError('Connection failed');
const timeoutError = HttpError.timeoutError(5000);
```

## API Reference

### Retry

| Export | Type | Description |
|--------|------|-------------|
| `withRetry` | Function | Wraps async function with retry capability |
| `createRetryable` | Function | Creates pre-configured retry wrapper |
| `calculateBackoffDelay` | Function | Calculates exponential backoff delay |
| `shouldRetry` | Function | Determines if error is retryable |
| `delay` | Function | Promise-based delay utility |
| `RetryOptions` | Interface | Retry configuration options |
| `DEFAULT_RETRY_OPTIONS` | Object | Default retry options |

### Cache

| Export | Type | Description |
|--------|------|-------------|
| `SimpleCache` | Class | In-memory cache with TTL and LFU |
| `withCache` | Function | Wraps async function with caching |
| `generateCacheKey` | Function | Generates consistent cache keys |
| `CacheOptions` | Interface | Cache configuration options |
| `DEFAULT_CACHE_OPTIONS` | Object | Default cache options |

### Error Handling

| Export | Type | Description |
|--------|------|-------------|
| `HttpError` | Class | Unified HTTP error class |
| `HttpErrorCode` | Object | Error code constants |
| `RETRYABLE_STATUS_CODES` | Array | HTTP status codes suitable for retry |
| `RETRYABLE_NETWORK_ERRORS` | Array | Network error types suitable for retry |

### Types

| Export | Type | Description |
|--------|------|-------------|
| `HttpMethod` | Type | HTTP method string literals |
| `RequestConfig` | Interface | HTTP request configuration |
| `RequestInterceptor` | Interface | Request interceptor definition |
| `ResponseInterceptor` | Interface | Response interceptor definition |
| `InterceptorManager` | Interface | Interceptor management interface |

## License

Apache-2.0
