# @brix-sdk/eslint-config-architecture

> ESLint architectural guard rules for Brix Platform

## Overview

This package provides shared ESLint rules that enforce the architectural red lines defined in the Runtime Shell architecture blueprint. It ensures business modules follow proper separation of concerns and use the Capability abstraction layer.

## Features

### Enforced Red Lines

| Red Line | Rule | Description |
|----------|------|-------------|
| 1 | No direct adapter imports | Business modules must not directly import `@brix/infra-adapter-*` |
| 2 | No middleware clients | Prohibits direct use of `kafkajs`, `ioredis`, `amqplib`, etc. |
| 3 | No HTTP clients | Prohibits direct use of `axios`, `fetch`, `got`, etc. |
| 6 | No direct storage access | Prohibits direct use of `localStorage`/`sessionStorage` |
| 7 | No console output | Only `console.warn`/`console.error` allowed |
| 8 | No EventEmitter | Prohibits Node's `events` and `node:events` by module name, and `eventemitter3`, `mitt`, `tiny-emitter`; must use EventBusCapability instead. A module's own `./events` file (where shared packages keep event types) is not refused |
| 9 | No direct UI library imports | enterprise-solutions must use `useUI()` instead of MUI/Ant Design |

### Layer Guard Rules

- `pages/` directory cannot directly import from `repositories/*`
- `repositories/` directory cannot use `fetch`/`axios` directly
- A Repository's HTTP calls go through an injected `HttpCapability` from the web or the mobile contract (`@brix-sdk/runtime-sdk-api-web`, `@brix-sdk/runtime-sdk-api-mobile`) or a generated client

### BrixUI Governance Rules (v3.3.0)

Enterprise-solutions plugins must obtain UI components via `useUI()` from `@brix-sdk/runtime-sdk-react` instead of directly importing UI libraries:

| Banned Package | Alternative |
|----------------|-------------|
| `@mui/material` | `const { Button, Card } = useUI()` |
| `@mui/icons-material` | `const { Icon } = useUI()` |
| `antd` | `const { Button, Card } = useUI()` |
| `@ant-design/*` | `const { Icon } = useUI()` |
| `element-plus` | `const { Button, Card } = useUI()` |
| `@chakra-ui/*` | `const { Button, Card } = useUI()` |
| `@mantine/*` | `const { Button, Card } = useUI()` |

## Installation

```bash
npm install @brix-sdk/eslint-config-architecture --save-dev
```

## Usage

### Presets (eslint.config.js)

Each preset parses TypeScript itself, matches files relative to the package that runs ESLint, and reports an
`eslint-disable` comment that disables nothing as an error (standard-link queue item 19, D162):

| Preset | For | Rules |
|---|---|---|
| `configs.ui` | UI code of the same JavaScript permission domain: plugin packages and platform consoles | Every rule in this README: red lines, Page/Hook/Repository layers, BrixUI and design tokens, role literals and role checks, the super-admin red lines, the legacy tenant switch, accessibility warnings |
| `configs.platform` | Every other frontend package: runtime, shared runtime, adapters, platform capabilities, tooling | The rules written for every file: the super-admin red lines and the role checks |
| `configs.contract` | Layer 2A contract packages (`runtime-sdk-api-web`, `runtime-sdk-api-mobile`) | `configs.platform`, plus contract layer purity in `src/types/**` (interfaces, types and enums; no object, array or literal constants) and React for types only: no value, default, namespace or side-effect import of `react`, no re-export, no `import('react')` or `require('react')` at run time; `import('react').ReactNode` in a type position is fine |

```javascript
import architecture from '@brix-sdk/eslint-config-architecture';

export default [
  { ignores: ['dist/**', 'node_modules/**'] },
  ...architecture.configs.ui,
];
```

A super-admin action surface also requires `data-testid` on its action elements, for its own files:

```javascript
  {
    files: ['src/**/*.{tsx,jsx}'],
    plugins: { '@brix-architecture': architecture.brixArchPlugin },
    rules: { '@brix-architecture/require-testid-on-action': 'error' },
  },
```

The default export (`...architecture`) is the earlier configuration. It sets no parser, and its BrixUI, design token,
role literal, test id and accessibility blocks select files by repository paths (`**/enterprise-solutions/**`,
`**/platform-admin-web/src/**`), which do not match when a package lints its own directory.

## Error Examples

```typescript
// ‚ù?Red Line 1 violation
import { IFrameAdapter } from '@brix/infra-adapter-iframe-web';
// Error: Business modules must not directly depend on infrastructure adapters

// ‚ù?Red Line 3 violation
import axios from 'axios';
// Error: Direct use of HTTP client libraries is prohibited

// ‚ù?Red Line 6 violation
localStorage.setItem('key', 'value');
// Error: Direct use of localStorage is prohibited

// ‚ù?Red Line 9 violation (enterprise-solutions only)
import { Button, Card } from '@mui/material';
// Error: Direct import of @mui/material is forbidden. Use useUI() instead.

// ‚ú?Correct approach - use Capabilities
import { useCapability } from '@brix-sdk/runtime-sdk-react';
const http = useCapability('http');
const state = useCapability('state');

// ‚ú?Correct approach - use UIAdapter (v3.3.0)
import { useUI } from '@brix-sdk/runtime-sdk-react';
const { Button, Card, Typography, Table } = useUI();
```

## Exemptions

For Shell layer code that legitimately needs direct access, use eslint-disable with justification:

```typescript
// eslint-disable-next-line no-restricted-globals -- Shell layer: initializing state adapter
localStorage.setItem('brix-init', 'true');
```

For professional domain components (rich text editors, maps, charts) not covered by UIAdapter:

```typescript
// eslint-disable-next-line no-restricted-imports -- RFC-2026-042 approved: react-quill for rich text
import ReactQuill from 'react-quill';
// Note: Wrap in dedicated file and declare in plugin-manifest.json
```

## Exported Constants

For custom rule composition, the following constants are exported:

```javascript
import {
  BASE_RESTRICTED_IMPORT_PATTERNS,
  BASE_RESTRICTED_IMPORT_PATHS,
  BASE_RESTRICTED_GLOBALS,
  BRIX_UI_RESTRICTED_PATTERNS,  // v3.3.0
  brixArchPlugin,               // v3.4.0 ‚Ä?Design Token Governance plugin
  mergeRestrictedImports,
  mergeRestrictedGlobals,
  ultraThinHostRules
} from '@brix-sdk/eslint-config-architecture';
```

## Authorization Rule (v3.3.0)

### `@brix-architecture/no-role-authorization`

UI code shows, hides and guards content on permission codes, never on a role name (super-admin BRX-SA-AUTH-006,
multi-tenant T-29, frontend BRX-FE-ROUTE-002). The rule refuses:

- a call to, or a reference of, `hasRole`, `hasAnyRole` or `hasAllRoles`, and taking one out of an object
  (`const { hasRole } = useAuth()`);
- `useRole` imported from `@brix-sdk/platform-auth-ui-web`;
- the `role` and `anyRoles` props of `PermissionGate`.

Declaring such a method, as an implementation or a test double does, is no use, and test files are not checked. The
SDK's role checks are deprecated since 3.3.0 and go in 4.0.0.

| Instead of | Use |
|---|---|
| `auth.hasRole('ADMIN')` | `auth.hasPermission('booking:manage')` |
| `const { hasRole } = useAuth()` | `const { hasPermission } = useAuth()` |
| `useRole(auth, 'ADMIN')` | `usePermission(auth, 'booking:manage')` |
| `<PermissionGate role="ADMIN">` | `<PermissionGate permission="booking:manage">` |

The rule's block matches every source file wherever a package runs ESLint, and both presets carry it. ESLint has to
parse the file first: the presets set the TypeScript parser; with the default export a TypeScript package needs a
parser in its own config, or every `.ts` file stops at a parsing error and no rule runs.

## Design Token Governance Rules (v3.4.0)

Three custom AST-based rules enforce the Brix three-layer design token architecture within enterprise-solutions plugins. These rules are registered as the `@brix-architecture` ESLint plugin.

### `@brix-architecture/no-plugin-theme-tokens` (Rule 7.1)

Forbid plugins from defining platform-level ThemeTokens objects with `palette`/`typography`/`shape` structures.

| Detection | Pattern | Example |
|-----------|---------|---------|
| Name-based | `export const *ThemeTokens = ...` | `export const partnerThemeTokens = { ... }` |
| Structure-based | Object with ‚â? of: palette.primary, typography.fontFamily, shape.borderRadius | `export const cfg = { palette: { primary: {} }, shape: { borderRadius: 8 } }` |

**Allowed**: Business semantic colors like `PARTNER_TYPE_COLORS`, `CASE_STATUS_COLORS`.

### `@brix-architecture/no-direct-design-tokens-import` (Rule 7.2)

Forbid plugins from directly importing `@brix-sdk/platform-design-tokens` (primitive static tokens). Plugins must use `useTheme().tokens` for runtime-resolved semantic tokens.

```typescript
// ‚ù?WRONG: Static primitive tokens (don't respond to dark mode or tenant branding)
import { brandColors } from '@brix-sdk/platform-design-tokens';

// ‚ú?CORRECT: Runtime semantic tokens
const { tokens } = useTheme();
```

### `@brix-architecture/no-mui-in-plugins` (Rule 7.3)

Forbid MUI-specific APIs, palette access patterns, theme references, and variable names.

| Detection | Pattern | Example |
|-----------|---------|---------|
| Import | `@mui/*` packages | `import { Button } from '@mui/material'` |
| Palette access | `palette.<color>.main` | `palette.primary.main` |
| Theme reference | `theme.palette`, `theme.typography`, etc. | `theme.palette.primary.main` |
| Variable name | `muiTheme`, `MuiTheme`, `muiPalette` | `const muiTheme = createTheme()` |

**Migration mapping**:
```
palette.primary.main     ‚Ü?tokens.colors.brand.primary
palette.background.paper ‚Ü?tokens.colors.surface.card
palette.text.primary     ‚Ü?tokens.colors.text.primary
shape.borderRadius       ‚Ü?tokens.shape.md
theme.spacing(2)         ‚Ü?tokens.space.md
typography.h1            ‚Ü?tokens.typography.displayLarge
```

## Changelog

### v3.4.0

- **New**: Design Token Governance ‚Ä?3 custom AST-based ESLint rules (Phase 7 of UI Design Token Reform Plan)
  - `no-plugin-theme-tokens`: Forbid plugin-level ThemeTokens objects (name + structure detection)
  - `no-direct-design-tokens-import`: Forbid direct import of primitive @brix-sdk/platform-design-tokens
  - `no-mui-in-plugins`: Forbid MUI-specific API references, palette access, and variable naming
- **New**: `@brix-architecture` ESLint plugin registered in flat config for custom rule support
- **New**: `brixArchPlugin` export for standalone plugin usage
- **New**: Comprehensive unit tests for all 3 rules (vitest + ESLint RuleTester)

### v3.3.0

- **New**: Added Red Line 9 - BrixUI Unified Governance (Constraint 9 from v3.0.8 Blueprint)
- **New**: `BRIX_UI_RESTRICTED_PATTERNS` constant for enterprise-solutions UI library restrictions
- **New**: enterprise-solutions/**/*.{ts,tsx,js,jsx} files now enforce no-direct-ui-import rule

### v3.2.0

- **Fix**: Layer-specific rules now correctly merge base red line patterns instead of replacing them

## License

Apache-2.0
