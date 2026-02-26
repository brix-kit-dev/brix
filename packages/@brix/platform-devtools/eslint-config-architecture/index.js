/**
 * @brix/eslint-config-architecture
 *
 * Shared ESLint architectural guard rules, corresponding to the 8 red lines (TypeScript side)
 * defined in the v3.0 Runtime Shell Architecture Blueprint.
 *
 * [Covered Red Lines]
 * - Red Line 1: Prohibit direct import of infrastructure adapters (@brix/infra-adapter-*)
 * - Red Line 2: Prohibit direct use of middleware clients (kafkajs/ioredis/amqplib, etc.)
 * - Red Line 3: Prohibit direct use of HTTP clients (axios/fetch/got/undici)
 * - Red Line 6: Prohibit direct access to local storage (localStorage/sessionStorage)
 * - Red Line 7: Prohibit console output (allow warn/error)
 * - Red Line 8: Prohibit direct use of EventEmitter (must use EventBusCapability)
 *
 * [Layer Guard Rules]
 * - pages/ directory prohibits direct import of repositories/*
 * - repositories/ directory prohibits direct use of fetch/axios
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
 * @version 3.2.0
 * @author Brix Architecture Team
 * @see v3.0 Runtime Shell Architecture Blueprint
 */

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
      // Logs should be output through ObservabilityCapability / LoggerCapability
      'no-console': ['error', {
        allow: ['warn', 'error']
      }]
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
module.exports.ultraThinHostRules = ultraThinHostRules;
// Export base red line constants for custom rule composition
module.exports.BASE_RESTRICTED_IMPORT_PATTERNS = BASE_RESTRICTED_IMPORT_PATTERNS;
module.exports.BASE_RESTRICTED_IMPORT_PATHS = BASE_RESTRICTED_IMPORT_PATHS;
module.exports.BASE_RESTRICTED_GLOBALS = BASE_RESTRICTED_GLOBALS;
// Export merge helper functions for custom layer rules
module.exports.mergeRestrictedImports = mergeRestrictedImports;
module.exports.mergeRestrictedGlobals = mergeRestrictedGlobals;
