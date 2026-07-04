/**
 * @brix/eslint-config-architecture
 *
 * Shared ESLint architectural guard rules, corresponding to the 9 red lines (TypeScript side)
 * defined in the v3.0 Runtime Shell Architecture Blueprint.
 *
 * [Covered Red Lines]
 * - Red Line 1: Prohibit direct import of infrastructure adapters (@brix/infra-adapter-*)
 * - Red Line 2: Prohibit direct use of middleware clients (kafkajs/ioredis/amqplib, etc.)
 * - Red Line 3: Prohibit direct use of HTTP clients (axios/fetch/got/undici)
 * - Red Line 6: Prohibit direct access to local storage (localStorage/sessionStorage)
 * - Red Line 7: Prohibit console output (allow warn/error)
 * - Red Line 8: Prohibit direct use of EventEmitter (must use EventBusCapability)
 * - Red Line 9: Prohibit direct import of UI libraries in enterprise-solutions (must use useUI())
 *
 * [Layer Guard Rules]
 * - pages/ directory prohibits direct import of repositories/*
 * - repositories/ directory prohibits direct use of fetch/axios
 *
 * [BrixUI Governance Rules] (v3.3.0)
 * - enterprise-solutions plugins must obtain UI components via useUI() from @brix-sdk/runtime-sdk-react
 * - Direct imports of @mui/material, @mui/icons-material, antd, element-plus, @ant-design are forbidden
 *
 * [Design Token Governance Rules] (v3.4.0 �?UI Design Token Reform Phase 7)
 * Three custom AST-based rules enforce the Brix three-layer design token architecture:
 * - no-plugin-theme-tokens: Forbid plugins from defining platform-level ThemeTokens objects
 * - no-direct-design-tokens-import: Forbid plugins from importing @brix-sdk/platform-design-tokens directly
 * - no-mui-in-plugins: Forbid MUI-specific API references, palette access, and variable naming
 *
 * These rules are registered as the `@brix-architecture` ESLint plugin and applied to
 * enterprise-solutions/**\/*.{ts,tsx,js,jsx} via flat config file matching.
 *
 * [v3.4.0 New]
 * Added Design Token Governance rules (Phase 7 of UI Design Token Reform Plan):
 * - Custom ESLint plugin with 3 AST-based rules for enterprise-solutions plugins
 * - Catches MUI coupling beyond import level: palette access patterns, theme references,
 *   MUI variable names, and platform-level ThemeTokens object definitions
 * - Blocks direct @brix-sdk/platform-design-tokens import (static tokens don't respond to dark mode)
 *
 * [v3.3.0 New]
 * Added no-direct-ui-import rule to enforce BrixUI Unified Governance (Constraint 9) from
 * v3.0.8 Runtime Shell Architecture Blueprint. This ensures all enterprise-solutions plugins
 * use the UIAdapter abstraction layer instead of directly coupling to specific UI frameworks.
 *
 * [v3.2.0 Fix]
 * Fixed an issue where layer-specific rules in ESLint flat config would replace (not merge)
 * base red line patterns. Now all layer rules correctly inherit and merge base red line patterns,
 * ensuring CI catches real violations.
 *
 * Usage:
 *   // eslint.config.js
 *   import architectureRules from '@brix/eslint-config-architecture';
 *   export default [...architectureRules];
 *
 * @version 3.4.0
 * @author Brix Architecture Team
 * @see v3.0.9 Runtime Shell Architecture Blueprint - Constraint 9: BrixUI Unified Governance
 * @see UI设计令牌改造方�?v2.0.md - Phase 7: ESLint Governance Rules
 */

// ============================================================================
// Base Red Line Patterns (extracted as constants for correct layer rule merging)
// ============================================================================

// ============================================================================
// Design Token Governance �?Custom ESLint Plugin (v3.4.0)
// ============================================================================
/**
 * Custom ESLint plugin containing AST-based rules for Design Token governance.
 *
 * [Architecture]
 * ESLint flat config requires custom rules to be registered through a plugin object.
 * This plugin is referenced in config blocks with `plugins: { '@brix-architecture': brixArchPlugin }`
 * and rules are activated with `'@brix-architecture/rule-name': 'error'`.
 *
 * [Rules]
 * - no-plugin-theme-tokens: Forbid export of platform-level ThemeTokens objects
 * - no-direct-design-tokens-import: Forbid direct import of @brix-sdk/platform-design-tokens
 * - no-mui-in-plugins: Forbid MUI-specific API patterns in code
 *
 * @see UI设计令牌改造方�?v2.0.md �?Phase 7: ESLint Governance Rules
 */
const brixArchPlugin = {
  rules: {
    'no-plugin-theme-tokens': require('./rules/no-plugin-theme-tokens'),
    'no-direct-design-tokens-import': require('./rules/no-direct-design-tokens-import'),
    'no-mui-in-plugins': require('./rules/no-mui-in-plugins'),
    'no-role-string-literal': require('./rules/no-role-string-literal'),
    'no-temp-password-in-response-type': require('./rules/no-temp-password-in-response-type'),
    'no-platform-admin-mode-flag': require('./rules/no-platform-admin-mode-flag'),
    'no-permission-or-true': require('./rules/no-permission-or-true'),
    'no-legacy-tenant-switch': require('./rules/no-legacy-tenant-switch'),
    'require-testid-on-action': require('./rules/require-testid-on-action'),
  },
};

// ============================================================================
// jsx-a11y plugin (v3.3.0 Frontend Stability Reform Plan v1.0 — C-9)
// ----------------------------------------------------------------------------
// Loaded lazily so consumers that never opt into the a11y baseline (e.g.
// ESLint runs scoped to non-React workspaces) do not pay the resolution cost
// nor crash if the optional peer dep is missing. When the plugin cannot be
// resolved we surface `null` and skip wiring the rule block — this preserves
// the v1.0 risk-mitigation directive that "new rules ship at warn level
// for one week before promotion to error".
// ============================================================================
let jsxA11yPlugin = null;
try {
  jsxA11yPlugin = require('eslint-plugin-jsx-a11y');
} catch (_err) {
  // Optional dependency not installed — the baseline block below will be
  // omitted from the exported config. Consumers can install
  // `eslint-plugin-jsx-a11y` when they're ready to enable the rules.
  jsxA11yPlugin = null;
}

// ============================================================================
// Base Red Line Patterns (extracted as constants for correct layer rule merging)
// ============================================================================

/**
 * Base red line patterns - all business modules must comply
 * 
 * [Design Principle]
 * Extract red line patterns as independent arrays so layer rules can merge them
 * using the spread operator (...) instead of replacing. This is the correct
 * merge pattern for ESLint flat config.
 */
const BASE_RESTRICTED_IMPORT_PATTERNS = [
  // ==================== Red Line 1: Prohibit direct import of infrastructure adapters ====================
  {
    group: ['@brix/infra-adapter-*'],
    message: '[Red Line 1] Business modules must not directly depend on infrastructure adapters. Use Capability interfaces instead.'
  },
  // ==================== Red Line 3: Prohibit direct use of HTTP clients ====================
  {
    group: ['axios', 'node-fetch', 'got', 'undici', 'ky', 'superagent'],
    message: '[Red Line 3] Direct use of HTTP client libraries is prohibited. Use HttpCapability for requests.'
  },
  // ==================== Red Line 2: Prohibit direct use of middleware clients ====================
  {
    group: ['kafkajs', 'kafka-node'],
    message: '[Red Line 2] Direct use of Kafka clients is prohibited. Use EventBusCapability for event communication.'
  },
  {
    group: ['ioredis', 'redis', 'redis-client'],
    message: '[Red Line 2] Direct use of Redis clients is prohibited. Use CacheCapability / StateCapability for state management.'
  },
  {
    group: ['amqplib', 'amqp-connection-manager', 'rhea'],
    message: '[Red Line 2] Direct use of AMQP/RabbitMQ clients is prohibited. Use EventBusCapability for event communication.'
  },
  // ==================== Red Line 8: Prohibit direct use of EventEmitter ====================
  {
    group: ['events', 'node:events'],
    message: '[Red Line 8] Direct use of EventEmitter is prohibited. Use @brix/platform-eventbus-web for event communication.'
  },
  {
    group: ['eventemitter3', 'mitt', 'tiny-emitter'],
    message: '[Red Line 8] Direct use of third-party event libraries is prohibited. Use @brix/platform-eventbus-web for event communication.'
  }
];

/**
 * Base red line paths - prohibit specific named imports
 */
const BASE_RESTRICTED_IMPORT_PATHS = [
  { name: 'events', message: '[Red Line 8] Direct use of EventEmitter is prohibited. Use @brix/platform-eventbus-web' },
  { name: 'node:events', message: '[Red Line 8] Direct use of EventEmitter is prohibited. Use @brix/platform-eventbus-web' }
];

/**
 * Base red line globals - prohibited global variables
 */
const BASE_RESTRICTED_GLOBALS = [
  { name: 'localStorage', message: '[Red Line 6] Direct use of localStorage is prohibited. Use StateCapability for state management.' },
  { name: 'sessionStorage', message: '[Red Line 6] Direct use of sessionStorage is prohibited. Use StateCapability for state management.' },
  { name: 'fetch', message: '[Red Line 3] Direct use of fetch is prohibited. Use HttpCapability for requests. If Shell layer exemption is needed, add eslint-disable comment with justification.' }
];

// ============================================================================
// BrixUI Governance Patterns (Constraint 9 - v3.0.8)
// ============================================================================

/**
 * Banned UI library import patterns for enterprise-solutions plugins.
 * 
 * [Design Rationale - v3.0.8 Architecture Blueprint Constraint 9]
 * - enterprise-solutions plugins MUST obtain UI components via useUI() from @brix-sdk/runtime-sdk-react
 * - Direct coupling to MUI/Ant Design/Element Plus prevents UI library replacement
 * - Theme/color schemes cannot be unified if plugins import UI libraries directly
 * - Bundle size bloats when multiple UI libraries are bundled together
 * - Frontend architecture must align with backend "plugins only depend on capability contracts"
 * 
 * [Escape Hatch]
 * - Professional domain components (rich text editors, maps, chart libraries) may be imported directly
 * - Escape hatch components must be wrapped in dedicated files and declared in plugin-manifest
 * - Use eslint-disable with RFC approval reference for legitimate escape hatch usage
 * 
 * @see v3.0.8 Runtime Shell Architecture Blueprint - Constraint 9: BrixUI Unified Governance
 */
const BRIX_UI_RESTRICTED_PATTERNS = [
  // MUI (Material-UI) - All packages
  {
    group: ['@mui/material', '@mui/material/**'],
    message: '[Red Line 9 - BrixUI] Direct import of @mui/material is forbidden in enterprise-solutions. Use useUI() from @brix-sdk/runtime-sdk-react instead. Example: const { Button, Card } = useUI();'
  },
  {
    group: ['@mui/icons-material', '@mui/icons-material/**'],
    message: '[Red Line 9 - BrixUI] Direct import of @mui/icons-material is forbidden in enterprise-solutions. Use Icon component from useUI() instead. Example: const { Icon } = useUI(); <Icon name="check" />'
  },
  {
    group: ['@mui/lab', '@mui/lab/**'],
    message: '[Red Line 9 - BrixUI] Direct import of @mui/lab is forbidden in enterprise-solutions. Submit RFC to extend UIAdapter if needed.'
  },
  {
    group: ['@mui/x-*', '@mui/x-*/**'],
    message: '[Red Line 9 - BrixUI] Direct import of @mui/x-* packages is forbidden in enterprise-solutions. Submit RFC to extend UIAdapter if needed.'
  },
  {
    group: ['@mui/system', '@mui/system/**'],
    message: '[Red Line 9 - BrixUI] Direct import of @mui/system is forbidden in enterprise-solutions. Use style props or className instead.'
  },
  {
    group: ['@mui/styles', '@mui/styles/**'],
    message: '[Red Line 9 - BrixUI] Direct import of @mui/styles is forbidden in enterprise-solutions. Use CSS-in-JS abstraction from UIAdapter.'
  },
  // Ant Design - All packages
  {
    group: ['antd', 'antd/**'],
    message: '[Red Line 9 - BrixUI] Direct import of antd is forbidden in enterprise-solutions. Use useUI() from @brix-sdk/runtime-sdk-react instead.'
  },
  {
    group: ['@ant-design/*', '@ant-design/**'],
    message: '[Red Line 9 - BrixUI] Direct import of @ant-design packages is forbidden in enterprise-solutions. Use useUI() from @brix-sdk/runtime-sdk-react instead.'
  },
  // Element Plus (Vue-based, but guard for completeness)
  {
    group: ['element-plus', 'element-plus/**'],
    message: '[Red Line 9 - BrixUI] Direct import of element-plus is forbidden in enterprise-solutions. Use useUI() from @brix-sdk/runtime-sdk-react instead.'
  },
  // Additional common UI libraries that should go through adapter
  {
    group: ['@chakra-ui/*', '@chakra-ui/**'],
    message: '[Red Line 9 - BrixUI] Direct import of Chakra UI is forbidden in enterprise-solutions. Use useUI() from @brix-sdk/runtime-sdk-react instead.'
  },
  {
    group: ['@mantine/*', '@mantine/**'],
    message: '[Red Line 9 - BrixUI] Direct import of Mantine is forbidden in enterprise-solutions. Use useUI() from @brix-sdk/runtime-sdk-react instead.'
  }
];

/**
 * Helper function: Merge no-restricted-imports rules
 * 
 * @param {Array} additionalPatterns - Layer-specific additional prohibited patterns
 * @param {Array} additionalPaths - Layer-specific additional prohibited paths
 * @returns {Array} Merged ESLint rule configuration
 * 
 * [Design Notes]
 * In ESLint flat config, rules with the same name are completely replaced by later definitions.
 * This function ensures layer rules correctly **merge** base red lines instead of accidentally **replacing** them.
 */
function mergeRestrictedImports(additionalPatterns = [], additionalPaths = []) {
  return ['error', {
    patterns: [...BASE_RESTRICTED_IMPORT_PATTERNS, ...additionalPatterns],
    paths: [...BASE_RESTRICTED_IMPORT_PATHS, ...additionalPaths]
  }];
}

/**
 * Helper function: Merge no-restricted-globals rules
 * 
 * @param {Array} additionalGlobals - Layer-specific additional prohibited global variables
 * @returns {Array} Merged ESLint rule configuration
 */
function mergeRestrictedGlobals(additionalGlobals = []) {
  return ['error', ...BASE_RESTRICTED_GLOBALS, ...additionalGlobals];
}

/** @type {import('eslint').Linter.FlatConfig[]} */
const baseRules = [
  // ============================================================================
  // Base Rule Set (globally applied)
  // ============================================================================
  {
    name: 'brix/architecture-guard/base',
    rules: {
      // Use merge functions to generate rules for consistency
      'no-restricted-imports': mergeRestrictedImports(),
      'no-restricted-globals': mergeRestrictedGlobals(),

      // ==================== Red Line 7: Prohibit console output ====================
      // ALL console methods are prohibited. Use ObservabilityCapability / LoggerCapability instead.
      // console.warn/error included �?platform provides structured logging via observability layer.
      'no-console': 'error'
    }
  },

  // ============================================================================
  // Layer Guard: pages/ directory rules
  // ============================================================================
  // View layer (pages/) must not call Repository layer directly; must access data through ViewModel layer (hooks/)
  // [v3.2.0 Fix] Use mergeRestrictedImports to ensure all base red lines are inherited
  {
    name: 'brix/architecture-guard/pages-layer',
    files: ['**/pages/**/*.ts', '**/pages/**/*.tsx', '**/views/**/*.ts', '**/views/**/*.tsx'],
    rules: {
      'no-restricted-imports': mergeRestrictedImports([
        // Layer-specific rule: pages prohibit direct calls to repositories
        {
          group: ['**/repositories/**', '**/repository/**', '../repositories/**', '../repository/**'],
          message: '[Layer Violation] pages/ layer must not directly call repositories/. Access data through hooks/ layer. Correct: import { useXxx } from "../hooks/useXxx"'
        },
        {
          group: ['**/services/**', '**/service/**'],
          message: '[Layer Violation] pages/ layer must not directly call services/. Access services through hooks/ layer. Correct: import { useXxxService } from "../hooks/useXxxService"'
        }
      ]),
      // pages layer inherits all base globals restrictions
      'no-restricted-globals': mergeRestrictedGlobals()
    }
  },

  // ============================================================================
  // Layer Guard: repositories/ directory rules
  // ============================================================================
  // Repository layer must not use fetch/axios directly; must access APIs through HttpCapability factory
  // [v3.2.0 Fix] Use mergeRestrictedImports to ensure all base red lines are inherited
  {
    name: 'brix/architecture-guard/repositories-layer',
    files: ['**/repositories/**/*.ts', '**/repositories/**/*.tsx', '**/repository/**/*.ts', '**/repository/**/*.tsx'],
    rules: {
      // repositories layer inherits all base imports restrictions
      'no-restricted-imports': mergeRestrictedImports(),
      // repositories layer uses stricter fetch warning message
      'no-restricted-globals': ['error',
        { name: 'fetch', message: '[Layer Violation] repositories/ must access APIs through HttpCapability factory. Direct use of fetch is prohibited.' },
        { name: 'localStorage', message: '[Red Line 6] Direct use of localStorage is prohibited. Use StateCapability for state management.' },
        { name: 'sessionStorage', message: '[Red Line 6] Direct use of sessionStorage is prohibited. Use StateCapability for state management.' }
      ]
    }
  },

  // ============================================================================
  // Layer Guard: hooks/ directory rules
  // ============================================================================
  // ViewModel layer (hooks/) can call Repository layer, but must not use fetch/infrastructure directly
  // [v3.2.0 Fix] Use mergeRestrictedImports to ensure all base red lines are inherited
  {
    name: 'brix/architecture-guard/hooks-layer',
    files: ['**/hooks/**/*.ts', '**/hooks/**/*.tsx'],
    rules: {
      // hooks layer inherits all base imports restrictions
      'no-restricted-imports': mergeRestrictedImports(),
      // hooks layer uses stricter fetch warning message
      'no-restricted-globals': ['error',
        { name: 'fetch', message: '[Layer Violation] hooks/ must not use fetch directly. Call repositories/ layer instead.' },
        { name: 'localStorage', message: '[Red Line 6] Direct use of localStorage is prohibited. Use StateCapability for state management.' },
        { name: 'sessionStorage', message: '[Red Line 6] Direct use of sessionStorage is prohibited. Use StateCapability for state management.' }
      ]
    }
  },

  // ============================================================================
  // Layer Guard: components/ directory rules (pure presentation components)
  // ============================================================================
  // Pure presentation components should not contain data fetching logic
  // [v3.2.0 Fix] Use mergeRestrictedImports to ensure all base red lines are inherited
  {
    name: 'brix/architecture-guard/components-layer',
    files: ['**/components/**/*.ts', '**/components/**/*.tsx'],
    rules: {
      'no-restricted-imports': mergeRestrictedImports([
        // Layer-specific rule: components prohibit direct calls to repositories
        {
          group: ['**/repositories/**', '**/repository/**'],
          message: '[Layer Violation] components/ is a pure presentation layer. Must not directly call repositories/. For data, pass through props or use hooks/'
        }
      ]),
      // components layer uses stricter fetch warning message
      'no-restricted-globals': ['error',
        { name: 'fetch', message: '[Layer Violation] components/ is a pure presentation layer. Must not directly call fetch. For data, pass through props.' },
        { name: 'localStorage', message: '[Red Line 6] Direct use of localStorage is prohibited.' },
        { name: 'sessionStorage', message: '[Red Line 6] Direct use of sessionStorage is prohibited.' }
      ]
    }
  },

  // ============================================================================
  // BrixUI Governance: enterprise-solutions directory rules (Constraint 9)
  // ============================================================================
  /**
   * enterprise-solutions plugins must use UIAdapter components via useUI() hook.
   * 
   * [Architecture Constraint 9 - BrixUI Unified Governance]
   * This rule enforces that all enterprise-solutions plugins obtain UI components through
   * the @brix-sdk/runtime-sdk-react UIAdapter abstraction layer, rather than directly importing
   * from MUI, Ant Design, Element Plus, or other UI frameworks.
   * 
   * [Why This Matters]
   * 1. UI Library Replacement: Platform can switch from MUI to Ant Design without plugin changes
   * 2. Theme Consistency: All components use platform ThemeProvider for unified styling
   * 3. Bundle Optimization: Single UI library instead of multiple competing frameworks
   * 4. Architecture Symmetry: Frontend aligns with backend "plugins only depend on contracts"
   * 
   * [Correct Usage]
   * ```tsx
   * // WRONG: Direct MUI import
   * import { Button, Card } from '@mui/material';
   * 
   * // CORRECT: UIAdapter abstraction
   * import { useUI } from '@brix-sdk/runtime-sdk-react';
   * const { Button, Card, Typography } = useUI();
   * ```
   * 
   * [Escape Hatch for Professional Domain Components]
   * For components not covered by UIAdapter (rich text editors, maps, charts), use eslint-disable
   * with RFC approval reference and wrap in dedicated files declared in plugin-manifest.
   * 
   * @see v3.0.8 Runtime Shell Architecture Blueprint - Constraint 9
   * @see BrixUI Component Extension & Governance Refactoring Plan v1.0 - Phase 5: ESLint Governance
   */
  {
    name: 'brix/architecture-guard/enterprise-solutions-ui',
    files: [
      '**/enterprise-solutions/**/*.ts',
      '**/enterprise-solutions/**/*.tsx',
      '**/enterprise-solutions/**/*.js',
      '**/enterprise-solutions/**/*.jsx'
    ],
    rules: {
      'no-restricted-imports': mergeRestrictedImports(BRIX_UI_RESTRICTED_PATTERNS),
      // enterprise-solutions layer inherits all base globals restrictions
      'no-restricted-globals': mergeRestrictedGlobals()
    }
  },

  // ============================================================================
  // Design Token Governance: enterprise-solutions directory rules (v3.4.0)
  // ============================================================================
  /**
   * Three custom AST-based rules enforce the Brix three-layer design token architecture
   * within enterprise-solutions plugins.
   *
   * [Architecture �?UI Design Token Reform Plan Phase 7]
   * These rules prevent plugin-level theme token definitions and MUI coupling that
   * cannot be caught by no-restricted-imports alone. They require AST analysis to detect:
   * 1. Exported objects with palette/typography/shape structures (ThemeTokens pattern)
   * 2. Direct imports of the primitive token package (@brix-sdk/platform-design-tokens)
   * 3. MUI-specific code patterns: palette.*.main, theme.palette, muiTheme variables
   *
   * [Correct Usage]
   * ```tsx
   * // Plugins must use runtime semantic tokens via useTheme() hook:
   * const { tokens } = useTheme();
   * <Card style={{
   *   backgroundColor: tokens.colors.surface.card,     // NOT palette.background.paper
   *   borderRadius: tokens.shape.md,                    // NOT shape.borderRadius
   *   color: tokens.colors.text.primary,                // NOT palette.text.primary
   * }} />
   * ```
   *
   * [Escape Hatch]
   * Use eslint-disable with architectural review approval reference.
   *
   * @see UI设计令牌改造方�?v2.0.md �?Phase 7
   * @see v3.0.9 Runtime Shell Architecture Blueprint �?Constraint 9
   */
  {
    name: 'brix/architecture-guard/enterprise-solutions-design-tokens',
    files: [
      '**/enterprise-solutions/**/*.ts',
      '**/enterprise-solutions/**/*.tsx',
      '**/enterprise-solutions/**/*.js',
      '**/enterprise-solutions/**/*.jsx'
    ],
    plugins: {
      '@brix-architecture': brixArchPlugin,
    },
    rules: {
      // Rule 7.1: Forbid plugin-level ThemeTokens objects (palette/typography/shape structures)
      '@brix-architecture/no-plugin-theme-tokens': 'error',
      // Rule 7.2: Forbid direct import of primitive design tokens package
      '@brix-architecture/no-direct-design-tokens-import': 'error',
      // Rule 7.3: Forbid MUI-specific API references, palette access, and variable naming
      '@brix-architecture/no-mui-in-plugins': 'error',
      // Rule R-3 (SSOT v1.0 §11): Forbid hard-coded platform role-code string literals.
      '@brix-architecture/no-role-string-literal': 'error',
    }
  },

  // ============================================================================
  // Phase 3 E2E Selector Governance
  // ============================================================================
  // Stable selectors are required on super-admin action surfaces so the blocking
  // nightly Playwright suite does not depend on translated labels or role text.
  {
    name: 'brix/architecture-guard/phase3-e2e-testid',
    files: [
      '**/platform-admin-web/src/**/*.{tsx,jsx}',
      '**/app-tenant/tenant-ui-web/src/pages/{TenantList,TenantCreate,TenantMembers}/**/*.{tsx,jsx}',
    ],
    plugins: {
      '@brix-architecture': brixArchPlugin,
    },
    rules: {
      '@brix-architecture/require-testid-on-action': 'error',
    },
  },

  // ============================================================================
  // Platform Super-Admin v2.0 Red Lines
  // ============================================================================
  // SSOT v2.0 R-12/R-14/R-3: response contracts must not expose legacy
  // credential fields, platform identity must be derived from token scope, and
  // permission checks must not retain unconditional true fallbacks.
  {
    name: 'brix/architecture-guard/platform-admin-v2-redlines',
    files: ['**/*.{ts,tsx,js,jsx}'],
    plugins: {
      '@brix-architecture': brixArchPlugin,
    },
    rules: {
      '@brix-architecture/no-temp-password-in-response-type': 'error',
      '@brix-architecture/no-platform-admin-mode-flag': 'error',
      '@brix-architecture/no-permission-or-true': 'error',
    },
  },

  // ============================================================================
  // v3.1.3 Multi-Tenant Phase 0: Context switch contract freeze
  // ============================================================================
  // The frozen tenant blueprint replaces tenantId-based switching with Actor
  // contextId switching. UI code must keep Page -> Hook -> Repository ->
  // HttpCapability layering and must not call the old switchTenant(tenantId)
  // flow from pages, components, hooks, or repositories.
  {
    name: 'brix/architecture-guard/v313-no-legacy-tenant-switch',
    files: [
      '**/pages/**/*.{ts,tsx,js,jsx}',
      '**/views/**/*.{ts,tsx,js,jsx}',
      '**/components/**/*.{ts,tsx,js,jsx}',
      '**/hooks/**/*.{ts,tsx,js,jsx}',
      '**/repositories/**/*.{ts,tsx,js,jsx}',
      '**/repository/**/*.{ts,tsx,js,jsx}',
    ],
    plugins: {
      '@brix-architecture': brixArchPlugin,
    },
    rules: {
      '@brix-architecture/no-legacy-tenant-switch': 'error',
    },
  },

  // ==========================================================================
  // [Layer 2A Contract Purity] runtime-sdk-api-web/types contract files
  // ==========================================================================
  // Per Architecture Blueprint v3.0.9 �?Layer 2A is "纯接口定义，零依�?
  // (interface definitions only, zero dependencies). Concrete value exports
  // (`export const FOO = '#7c3aed'`) violate the contract layer's role and
  // re-introduce dual-source-of-truth risk for design tokens.
  //
  // Allowed in these files:
  //   - export interface ...
  //   - export type ...
  //   - export enum ...      (capability-type enums are part of the contract)
  // Forbidden:
  //   - export const ...
  //   - export let ...
  //   - export var ...
  //   - export default <object literal>
  //
  // If you need a concrete value, put it in @brix-sdk/platform-design-tokens
  // (Layer 2C �?implementation/values).
  {
    name: 'brix/architecture-guard/contract-layer-purity',
    files: [
      '**/runtime-sdk-api-web/src/types/**/*.{ts,tsx,js,jsx}',
      '**/runtime-sdk-api-mobile/src/types/**/*.{ts,tsx,js,jsx}',
    ],
    rules: {
      'no-restricted-syntax': [
        'error',
        {
          // Forbid object-literal const exports - these encode design decisions (e.g. theme presets)
          selector:
            "ExportNamedDeclaration > VariableDeclaration > VariableDeclarator[init.type='ObjectExpression']",
          message:
            '[Contract Layer Purity] Layer 2A contract layer forbids exporting object-literal constants. ' +
            'Move design/default values (colors, themes, config presets) to @brix-sdk/platform-design-tokens or another Layer 2C implementation package. ' +
            'The contract layer may only export interface / type / enum, plus capability identifiers in the form Symbol.for(...). ' +
            'See: v3.0.9 Architecture Blueprint section 2.1.3 and Constraint 9 (BrixUI Unified Governance).',
        },
        {
          // Forbid array-literal const exports
          selector:
            "ExportNamedDeclaration > VariableDeclaration > VariableDeclarator[init.type='ArrayExpression']",
          message:
            '[Contract Layer Purity] Layer 2A contract layer forbids exporting array-literal constants. ' +
            'Move them to a Layer 2C implementation package.',
        },
        {
          // Forbid string/number/boolean literal const exports
          selector:
            "ExportNamedDeclaration > VariableDeclaration > VariableDeclarator[init.type='Literal']",
          message:
            '[Contract Layer Purity] Layer 2A contract layer forbids exporting literal constants (string/number/boolean). ' +
            'Move them to @brix-sdk/platform-design-tokens or another Layer 2C implementation package.',
        },
        {
          // Forbid template-literal const exports (e.g., `#${hex}`)
          selector:
            "ExportNamedDeclaration > VariableDeclaration > VariableDeclarator[init.type='TemplateLiteral']",
          message:
            '[Contract Layer Purity] Layer 2A contract layer forbids exporting template-literal constants. ' +
            'Move them to a Layer 2C implementation package.',
        },
        {
          selector: 'ExportDefaultDeclaration > ObjectExpression',
          message:
            '[Contract Layer Purity] Layer 2A contract layer forbids `export default <object literal>`. ' +
            'Move design/default values to a Layer 2C implementation package.',
        },
      ],
    },
  },

  // ==========================================================================
  // Phase 2 / M-1: pact tests and development helpers are allowed to import
  // adapters and touch browser globals when they intentionally exercise wiring.
  // Keep this block after architecture guard blocks so flat config precedence
  // only relaxes these explicitly-scoped files.
  // ============================================================================
  {
    name: 'brix/architecture-guard/pact-and-dev-exemptions',
    files: [
      '**/*.pact.test.ts',
      '**/*.pact.test.tsx',
      '**/*.dev.ts',
      '**/*.dev.tsx',
    ],
    rules: {
      'no-restricted-imports': 'off',
      'no-restricted-globals': 'off',
    },
  }
];

// ============================================================================
// [Ultra-Thin Host Principle] Rule Definitions - For Host layer projects to import separately
// ============================================================================
// Host layer needs to explicitly import these rules and configure correct file paths
const ultraThinHostRules = {
  // [Ultra-Thin Principle 1] Limit file line count
  'max-lines': ['error', { max: 100, skipBlankLines: true, skipComments: true }],
  
  // [Ultra-Thin Principle 2] Limit function line count
  'max-lines-per-function': ['warn', { max: 30, skipBlankLines: true, skipComments: true, IIFEs: false }],
  
  // [Ultra-Thin Principle 3] Limit cyclomatic complexity
  'complexity': ['error', { max: 3 }],
  
  // [Ultra-Thin Principle 4] Limit statement count
  'max-statements': ['error', { max: 10 }],
  
  // [Ultra-Thin Principle 5] Limit nesting depth
  'max-depth': ['error', { max: 2 }],
  
  // [Ultra-Thin Principle 6] Prohibit direct use of React Hooks
  'no-restricted-imports': ['error', {
    paths: [
      { name: 'react', importNames: ['useState', 'useReducer'], message: '[Ultra-Thin Host] Host hooks should not use useState/useReducer. State management belongs in SDK layer.' },
      { name: 'react', importNames: ['useRef'], message: '[Ultra-Thin Host] Host hooks should not use useRef. Ref management belongs in SDK layer.' },
      { name: 'react', importNames: ['useCallback', 'useMemo'], message: '[Ultra-Thin Host] Host hooks should not use useCallback/useMemo. Logic belongs in SDK layer.' },
      { name: 'react', importNames: ['useEffect', 'useLayoutEffect'], message: '[Ultra-Thin Host] Host hooks should not use useEffect/useLayoutEffect. Side effects belong in SDK layer.' }
    ]
  }],
  
  // [Ultra-Thin Principle 7] Prohibit feature implementation syntax
  'no-restricted-syntax': ['error',
    { selector: 'TryStatement', message: '[Ultra-Thin Host] Host hooks should not contain try-catch. Error handling belongs in SDK layer.' },
    { selector: 'FunctionDeclaration:not([id.name=/^use/])', message: '[Ultra-Thin Host] Host hooks should not define helper functions. Helper logic belongs in SDK layer.' },
    { selector: 'VariableDeclarator[init.type="ArrowFunctionExpression"][init.body.type="BlockStatement"][init.body.body.length>3]', message: '[Ultra-Thin Host] Arrow functions in Host hooks should not exceed 3 statements.' },
    { selector: 'ForStatement, ForInStatement, ForOfStatement, WhileStatement, DoWhileStatement', message: '[Ultra-Thin Host] Host hooks should not contain loop statements. Iteration logic belongs in SDK layer.' },
    { selector: 'SwitchStatement', message: '[Ultra-Thin Host] Host hooks should not contain switch statements. Branching logic belongs in SDK layer.' },
    { selector: 'AwaitExpression ~ AwaitExpression', message: '[Ultra-Thin Host] Host hooks should not have multiple awaits. Complex async logic belongs in SDK layer.' },
    { selector: 'CallExpression[callee.object.name="Promise"][callee.property.name=/^(all|race|allSettled)$/]', message: '[Ultra-Thin Host] Host hooks should not use Promise.all/race. Concurrency control belongs in SDK layer.' }
  ]
};

// Exports
module.exports = baseRules;

// ============================================================================
// jsx-a11y baseline append (v3.3.0 Frontend Stability Reform Plan v1.0 — C-9)
// ----------------------------------------------------------------------------
// Per blueprint §6.3, accessibility hygiene is enabled across all enterprise
// plugin source trees at WARN severity for an initial one-week observation
// window before being promoted to ERROR. We restrict the file scope to React
// source files (`.tsx` / `.jsx`) to avoid noise on non-JSX modules.
//
// The block is appended only when `eslint-plugin-jsx-a11y` is resolvable;
// see the lazy require above for rationale. The plugin's "recommended"
// preset is reused verbatim — no custom rule curation here, so future
// upstream improvements flow through automatically. Only the severity is
// downshifted to `warn`.
// ============================================================================
if (jsxA11yPlugin) {
  /** @type {Record<string, [string, ...unknown[]] | string>} */
  const recommendedRules = {};
  const recommendedConfig =
    (jsxA11yPlugin.configs && jsxA11yPlugin.configs.recommended) || {};
  const sourceRules = recommendedConfig.rules || {};
  for (const [ruleName, ruleConfig] of Object.entries(sourceRules)) {
    if (Array.isArray(ruleConfig)) {
      // Preserve rule options but downshift severity to 'warn'.
      recommendedRules[ruleName] = ['warn', ...ruleConfig.slice(1)];
    } else {
      recommendedRules[ruleName] = 'warn';
    }
  }

  baseRules.push({
    name: 'brix/architecture-guard/jsx-a11y-baseline',
    files: [
      '**/enterprise-solutions/**/*.{tsx,jsx}',
      '**/app-*/**/*.{tsx,jsx}',
    ],
    plugins: {
      'jsx-a11y': jsxA11yPlugin,
    },
    rules: recommendedRules,
  });
}

module.exports.ultraThinHostRules = ultraThinHostRules;
// Export base red line constants for custom rule composition
module.exports.BASE_RESTRICTED_IMPORT_PATTERNS = BASE_RESTRICTED_IMPORT_PATTERNS;
module.exports.BASE_RESTRICTED_IMPORT_PATHS = BASE_RESTRICTED_IMPORT_PATHS;
module.exports.BASE_RESTRICTED_GLOBALS = BASE_RESTRICTED_GLOBALS;
// Export BrixUI governance patterns for custom rule composition (v3.3.0)
module.exports.BRIX_UI_RESTRICTED_PATTERNS = BRIX_UI_RESTRICTED_PATTERNS;
// Export Design Token Governance plugin for standalone use (v3.4.0)
module.exports.brixArchPlugin = brixArchPlugin;
// Export merge helper functions for custom layer rules
module.exports.mergeRestrictedImports = mergeRestrictedImports;
module.exports.mergeRestrictedGlobals = mergeRestrictedGlobals;
