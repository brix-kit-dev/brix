# @brix-sdk/infra-adapter-ui-mui

> MUI (Material UI) implementation of UIAdapter contract for Brix Platform

## Overview

This package provides a production-grade implementation of the UIAdapter interface using Material UI v5. It implements atomic UI components that are consumed by the Shell layer for layout assembly.

## Architecture Position

According to the Runtime Shell Architecture Blueprint v3.0.4:

- **Layer**: Layer 2.5 (Capability Implementation Layer)
- **Role**: Implements UIAdapter contract from `@brix-sdk/runtime-sdk-api-web`
- **Consumer**: Shell layer obtains components via `useUI()` hook
- **Selection**: Host layer configures which UI adapter to use

## Design Constraints

### ‚ú?Included (Atomic Components)
- Form Components: Button, Input, Select
- Display Components: Card, Avatar, Badge, Tooltip
- Navigation Components: Menu, MenuItem (atomic level)
- Feedback Components: Modal, message API
- Theme System: ThemeProvider, getThemeTokens
- Icon System: Icon (wraps @mui/icons-material)

### ‚ù?Forbidden (Layout Components)
- Sidebar - Assembled in Shell layer
- Header - Assembled in Shell layer
- Layout - Assembled in Shell layer

## Installation

```bash
pnpm add @brix-sdk/infra-adapter-ui-mui
```

## Usage

### In Host Layer Configuration

```typescript
import { muiUIAdapter } from '@brix-sdk/infra-adapter-ui-mui';

// Register as UI capability
context.registerCapability(UICapabilityType, muiUIAdapter);
```

### In Shell Layer Components

```typescript
import { useUI } from '@brix-sdk/runtime-sdk-react';

function AppSidebar({ menuItems, currentPath }) {
  const { Menu, Icon } = useUI();
  
  return (
    <Menu
      items={menuItems}
      selectedKey={currentPath}
      onSelect={(key, item) => navigate(item.path)}
    />
  );
}
```

## Theme Customization

The adapter respects MUI theme tokens. You can customize appearance via UIAdapterConfig:

```typescript
import { createMuiUIAdapter } from '@brix-sdk/infra-adapter-ui-mui';

const customAdapter = createMuiUIAdapter({
  primaryColor: '#7c3aed',
  borderRadius: 12,
  defaultTheme: 'dark',
});
```

## License

Apache-2.0
