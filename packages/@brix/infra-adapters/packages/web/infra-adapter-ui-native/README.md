# @brix-sdk/infra-adapter-ui-native

> Native UI Adapter - Pure CSS atomic components implementing UIAdapter contract

## Overview

This package provides a complete UIAdapter implementation using pure CSS components with **zero external UI library dependencies**. It implements the UIAdapter contract from `@brix-sdk/runtime-sdk-api-web`.

## Features

- âœ?**Zero Dependencies** - No MUI, Ant Design, or other UI libraries required
- âœ?**Full UIAdapter Contract** - Implements all required components and APIs
- âœ?**Pure CSS Styling** - All components use inline styles and CSS custom properties
- âœ?**SVG Icons** - Inline SVG icons with Material Design icon set
- âœ?**Theme Support** - Light and dark themes via CSS variables
- âœ?**Accessibility** - Full keyboard navigation and ARIA support

## Installation

```bash
pnpm add @brix-sdk/infra-adapter-ui-native
```

## Usage

### Host Layer Registration

```typescript
import { nativeUIAdapter } from '@brix-sdk/infra-adapter-ui-native';

const hostConfig = {
  uiAdapter: nativeUIAdapter,
};
```

### Shell Layer Component Usage

```tsx
import { useUI } from '@brix-sdk/runtime-sdk-web';

function AppSidebar() {
  const { Menu, Icon } = useUI();
  
  return (
    <aside>
      <Menu
        items={menuItems}
        selectedKey={currentPath}
        onSelect={(key, item) => navigate(item.path)}
      />
    </aside>
  );
}
```

## Components

### Form Components

- `NativeButton` - Primary action button with variants
- `NativeInput` - Text input with label and validation
- `NativeSelect` - Native select dropdown

### Display Components

- `NativeCard` - Content container with elevation
- `NativeAvatar` - User avatar with fallback
- `NativeBadge` - Status indicator with count
- `NativeTooltip` - Hover information popup

### Navigation Components

- `NativeMenu` - Hierarchical navigation menu
- `NativeMenuItem` - Individual menu item

### Feedback Components

- `NativeModal` - Dialog/overlay modal
- `nativeMessageAPI` - Toast notification system

### Theme System

- `NativeThemeProvider` - CSS variable injection
- `getNativeThemeTokens()` - Get current theme tokens

### Icon System

- `NativeIcon` - Inline SVG icon component
- `getIconDef()` - Icon registry lookup

## Architecture

This adapter follows the v3.0.4 Runtime Shell Architecture:

```
Host Layer (Configuration)
    â”?
    â”œâ”€â”€ Selects: nativeUIAdapter
    â”?
Shell Layer (Layout Assembly)
    â”?
    â”œâ”€â”€ Uses: useUI() â†?{ Button, Menu, Icon, ... }
    â”?
    â””â”€â”€ Assembles: AppSidebar, AppHeader, AppLayout
```

## License

Apache-2.0
