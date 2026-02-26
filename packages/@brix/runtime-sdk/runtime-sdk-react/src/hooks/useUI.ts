/**
 * @file useUI Hook
 * @description React hook for accessing UIAdapter from RuntimeContext
 * @module @brix/runtime-sdk-react/hooks/useUI
 * @version 3.2.0
 *
 * [Architectural Position]
 * This hook provides React components with access to the UIAdapter capability.
 * Shell layer components use this hook to obtain atomic UI components
 * (Button, Menu, Icon, etc.) without direct dependency on UI libraries.
 *
 * [Design Principles]
 * - Provides type-safe access to UIAdapter components
 * - Throws if used outside RuntimeContext provider
 * - Supports fallback adapter for graceful degradation
 *
 * [Usage Example]
 * ```tsx
 * import { useUI } from '@brix/runtime-sdk-react';
 *
 * const MyComponent: FC = () => {
 *   const { Button, Menu, Icon } = useUI();
 *   return (
 *     <Button variant="primary" onClick={handleClick}>
 *       <Icon name="add" /> Add Item
 *     </Button>
 *   );
 * };
 * ```
 */

import { useContext } from 'react';
import { RuntimeContextReact } from '../context/RuntimeContextReact';
import type { UIAdapter } from '@brix/runtime-sdk-api-web';
import { UICapabilityType } from '@brix/runtime-sdk-api-web';

// ============================================================================
// Hook Interface
// ============================================================================

/**
 * useUI Result Interface
 *
 * <p>Complete UIAdapter with all atomic components and utilities.</p>
 */
export type UseUIResult = UIAdapter;

// ============================================================================
// Hook Implementation
// ============================================================================

/**
 * useUI Hook
 *
 * <p>React hook for accessing UIAdapter from RuntimeContext.
 * Provides atomic UI components for Shell layer assembly.</p>
 *
 * <h3>Architectural Note</h3>
 * <p>This hook enables Shell layer components to use UI library components
 * without direct import dependencies. The actual UI implementation is injected
 * via RuntimeContext at runtime, allowing different adapters (MUI, Native CSS, etc.)</p>
 *
 * @returns UIAdapter instance with all atomic components
 * @throws Error if used outside RuntimeContext provider or UIAdapter not registered
 *
 * @example
 * ```tsx
 * // In Shell layer component (e.g., SimpleSidebar)
 * const { Menu, MenuItem, Icon } = useUI();
 *
 * return (
 *   <Menu items={menuItems} selectedKey={currentKey} onSelect={handleSelect}>
 *     {items.map(item => (
 *       <MenuItem key={item.key} icon={item.icon}>
 *         {item.label}
 *       </MenuItem>
 *     ))}
 *   </Menu>
 * );
 * ```
 */
export function useUI(): UseUIResult {
  const context = useContext(RuntimeContextReact);

  if (!context) {
    throw new Error(
      'useUI must be used within RuntimeContextProvider. ' +
      'Ensure your component is wrapped with <RuntimeContextProvider value={context}>.'
    );
  }

  const uiAdapter = context.getCapability<UIAdapter>(UICapabilityType);

  if (!uiAdapter) {
    throw new Error(
      'UIAdapter capability not found in RuntimeContext. ' +
      'Ensure UIAdapter is registered via context.registerCapability(UICapabilityType, adapter).'
    );
  }

  return uiAdapter;
}

/**
 * useUIOptional Hook
 *
 * <p>Optional version of useUI that returns null instead of throwing
 * when context or adapter is not available.</p>
 *
 * @returns UIAdapter instance or null
 *
 * @example
 * ```tsx
 * const ui = useUIOptional();
 * if (ui) {
 *   const { Button } = ui;
 *   return <Button>Click me</Button>;
 * }
 * return <button>Fallback button</button>;
 * ```
 */
export function useUIOptional(): UIAdapter | null {
  const context = useContext(RuntimeContextReact);

  if (!context) {
    return null;
  }

  return context.getCapability<UIAdapter>(UICapabilityType) ?? null;
}
