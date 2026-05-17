# @brix-sdk/platform-shared

> Shared utilities for Brix Platform capabilities

## Overview

This package provides shared utilities, types, and helpers used across Brix Platform capability implementations.

## Features

- Common utility functions
- Shared type definitions
- Error handling utilities
- Logging helpers
- Validation utilities

## Installation

```bash
npm install @brix-sdk/platform-shared
```

## Usage

```typescript
import {
  createLogger,
  validateConfig,
  formatError,
  deepMerge
} from '@brix-sdk/platform-shared';

// Create a namespaced logger
const logger = createLogger('my-capability');
logger.info('Capability initialized');
logger.error('Something went wrong', error);

// Validate configuration
const config = validateConfig(userConfig, schema);

// Deep merge objects
const merged = deepMerge(defaults, overrides);
```

## Utilities

| Utility | Description |
|---------|-------------|
| `createLogger` | Namespaced logging with levels |
| `validateConfig` | JSON schema validation |
| `formatError` | Error formatting and serialization |
| `deepMerge` | Deep object merging |
| `debounce` | Function debouncing |
| `throttle` | Function throttling |

## License

Apache-2.0
