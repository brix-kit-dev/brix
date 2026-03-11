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
 * @file prompts.ts
 * @description Interactive Prompts
 * @module @brix/create-brix
 * @version 3.0
 * 
 * v3.0 Changes:
 * - Added collectAppConfig / confirmAppConfig functions
 * - Support for v3.0 business application module (shinwa-app-*) creation
 */

import inquirer from 'inquirer';
import chalk from 'chalk';
import type { 
  PluginConfig, 
  ServiceConfig, 
  PluginDependency,
  AppConfig,
  ModuleType,
} from './types.js';
import {
  DEFAULT_REQUIRED_CAPABILITIES,
  DEFAULT_OPTIONAL_CAPABILITIES,
} from './types.js';

/**
 * Available plugins list (for selection during service creation)
 * 
 * Version unified to 1.0.0-SNAPSHOT (development stage)
 */
const AVAILABLE_PLUGINS: PluginDependency[] = [
  { name: 'plugin-user', version: '1.0.0-SNAPSHOT', groupId: 'com.shinwa.plugin', artifactId: 'plugin-user-core' },
  { name: 'plugin-contract', version: '1.0.0-SNAPSHOT', groupId: 'com.shinwa.plugin', artifactId: 'plugin-contract-core' },
  { name: 'plugin-file-center', version: '1.0.0-SNAPSHOT', groupId: 'com.shinwa.plugin', artifactId: 'plugin-file-center-core' },
  { name: 'plugin-notification', version: '1.0.0-SNAPSHOT', groupId: 'com.shinwa.plugin', artifactId: 'plugin-notification-core' },
  { name: 'plugin-partner-catalog', version: '1.0.0-SNAPSHOT', groupId: 'com.shinwa.plugin', artifactId: 'plugin-partner-catalog-core' },
  { name: 'plugin-service-package', version: '1.0.0-SNAPSHOT', groupId: 'com.shinwa.plugin', artifactId: 'plugin-service-package-core' },
  { name: 'plugin-medical-intake', version: '1.0.0-SNAPSHOT', groupId: 'com.shinwa.plugin', artifactId: 'plugin-medical-intake-core' },
  { name: 'plugin-risk-compliance', version: '1.0.0-SNAPSHOT', groupId: 'com.shinwa.plugin', artifactId: 'plugin-risk-compliance-core' },
];

/**
 * Flyway prefix allocation table
 */
const FLYWAY_PREFIXES: Record<string, string> = {
  'plugin-user': '001',
  'plugin-contract': '002',
  'plugin-file-center': '003',
  'plugin-notification': '004',
  'plugin-partner-catalog': '005',
  'plugin-service-package': '006',
};

/**
 * Collect plugin configuration
 * 
 * Naming convention:
 * - Plugin names must start with plugin-
 * - If the provided name doesn't have plugin- prefix, it's automatically added
 * - Example: input reservation -> automatically converted to plugin-reservation
 */
export async function collectPluginConfig(
  name?: string,
  options?: {
    flywayPrefix?: string;
    withWeb?: boolean;
    withMobile?: boolean;
    withApi?: boolean;
    outputDir?: string;
  }
): Promise<PluginConfig> {
  // Automatically handle plugin name prefix
  // If the provided name doesn't have plugin- prefix, automatically add it
  let normalizedName = name;
  if (name && !name.startsWith('plugin-')) {
    normalizedName = `plugin-${name}`;
  }
  
  const answers = await inquirer.prompt([
    {
      type: 'input',
      name: 'name',
      message: 'Plugin name (must start with plugin-, e.g., plugin-user):',
      default: normalizedName,
      validate: (input: string) => {
        if (!input.startsWith('plugin-')) {
          return 'Plugin name must start with plugin-';
        }
        if (!/^plugin-[a-z][a-z0-9-]*$/.test(input)) {
          return 'Plugin name can only contain lowercase letters, numbers, and hyphens';
        }
        return true;
      },
      when: !normalizedName,
    },
    {
      type: 'input',
      name: 'displayName',
      message: 'Display name (Chinese or English):',
      default: (answers: { name?: string }) => {
        const pluginName = name || answers.name || '';
        return pluginName.replace('plugin-', '').replace(/-/g, ' ');
      },
    },
    {
      type: 'input',
      name: 'description',
      message: 'Plugin description:',
      default: 'Brix Platform Plugin',
    },
    {
      type: 'input',
      name: 'flywayPrefix',
      message: 'Flyway version prefix (3 digits, e.g., 001):',
      default: (answers: { name?: string }) => {
        const pluginName = name || answers.name || '';
        return FLYWAY_PREFIXES[pluginName] || '100';
      },
      validate: (input: string) => {
        if (!/^\d{3}$/.test(input)) {
          return 'Prefix must be 3 digits';
        }
        return true;
      },
      when: !options?.flywayPrefix,
    },
    {
      type: 'input',
      name: 'author',
      message: 'Author:',
      default: 'Shinwa Team',
    },
    {
      type: 'confirm',
      name: 'withApi',
      message: 'Include API module (for other plugins to depend on)?',
      default: options?.withApi ?? true,
      when: options?.withApi === undefined,
    },
    {
      type: 'confirm',
      name: 'withWeb',
      message: 'Include Web frontend module?',
      default: options?.withWeb ?? true,
      when: options?.withWeb === undefined,
    },
    {
      type: 'confirm',
      name: 'withMobile',
      message: 'Include Mobile frontend module?',
      default: options?.withMobile ?? false,
      when: options?.withMobile === undefined,
    },
    {
      type: 'input',
      name: 'webPort',
      message: 'Web frontend dev server port:',
      default: 3000,
      validate: (input: string) => {
        const port = parseInt(input, 10);
        if (isNaN(port) || port < 1024 || port > 65535) {
          return 'Port must be a number between 1024-65535';
        }
        return true;
      },
      filter: (input: string) => parseInt(input, 10),
      when: (answers: { withWeb?: boolean }) => options?.withWeb ?? answers.withWeb,
    },
    {
      type: 'confirm',
      name: 'includeFlyway',
      message: 'Include Flyway database migrations?',
      default: true,
    },
    {
      type: 'confirm',
      name: 'includeKafka',
      message: 'Include Kafka event support?',
      default: true,
    },
    {
      type: 'confirm',
      name: 'includeOutbox',
      message: 'Include Outbox table template (event consistency)?',
      default: true,
    },
    {
      type: 'confirm',
      name: 'includeTenantSupport',
      message: 'Include multi-tenant support?',
      default: true,
    },
    {
      type: 'input',
      name: 'outputDir',
      message: 'Output directory:',
      default: options?.outputDir || '.',
      when: !options?.outputDir,
    },
  ]);

  // Ensure returned name has plugin- prefix
  const finalName = normalizedName || answers.name;
  
  return {
    name: finalName,
    displayName: answers.displayName,
    description: answers.description,
    type: answers.withWeb && answers.withMobile ? 'full' : (answers.withWeb ? 'web' : 'mobile'),
    backend: 'spring-boot',
    frontend: 'react',
    author: answers.author,
    version: '1.0.0-SNAPSHOT',
    typescript: true,
    includeExamples: true,
    outputDir: options?.outputDir || answers.outputDir || '.',
    flywayPrefix: options?.flywayPrefix || answers.flywayPrefix,
    schemaVersion: '1.0',
    includeOutbox: answers.includeOutbox ?? true,
    includeTenantSupport: answers.includeTenantSupport ?? true,
    includeKafka: answers.includeKafka ?? true,
    includeFlyway: answers.includeFlyway ?? true,
    withApi: options?.withApi ?? answers.withApi ?? true,
    withWeb: options?.withWeb ?? answers.withWeb ?? true,
    withMobile: options?.withMobile ?? answers.withMobile ?? false,
    webPort: answers.webPort ?? 3000,
  };
}

/**
 * Confirm plugin configuration
 */
export async function confirmConfig(config: PluginConfig): Promise<boolean> {
  console.log(chalk.cyan('\n📋 Configuration Confirmation:'));
  console.log(chalk.gray('─'.repeat(50)));
  console.log(`  Name: ${chalk.yellow(config.name)}`);
  console.log(`  Display Name: ${config.displayName}`);
  console.log(`  Description: ${config.description}`);
  console.log(`  Flyway Prefix: ${chalk.yellow(config.flywayPrefix)}`);
  console.log(`  Modules:`);
  console.log(`    - Core: ${chalk.green('✓')}`);
  console.log(`    - API: ${config.withApi ? chalk.green('✓') : chalk.gray('✗')}`);
  console.log(`    - Web: ${config.withWeb ? chalk.green('✓') + chalk.gray(` (port: ${config.webPort})`) : chalk.gray('✗')}`);
  console.log(`    - Mobile: ${config.withMobile ? chalk.green('✓') : chalk.gray('✗')}`);
  console.log(`  Features:`);
  console.log(`    - Flyway: ${config.includeFlyway ? chalk.green('✓') : chalk.gray('✗')}`);
  console.log(`    - Kafka: ${config.includeKafka ? chalk.green('✓') : chalk.gray('✗')}`);
  console.log(`    - Outbox: ${config.includeOutbox ? chalk.green('✓') : chalk.gray('✗')}`);
  console.log(`    - Multi-tenant: ${config.includeTenantSupport ? chalk.green('✓') : chalk.gray('✗')}`);
  console.log(`  Output Directory: ${config.outputDir}`);
  console.log(chalk.gray('─'.repeat(50)));

  const { confirmed } = await inquirer.prompt([
    {
      type: 'confirm',
      name: 'confirmed',
      message: 'Confirm creation?',
      default: true,
    },
  ]);

  return confirmed;
}

/**
 * Java reserved words list
 */
const JAVA_RESERVED_WORDS = [
  'abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch', 'char', 
  'class', 'const', 'continue', 'default', 'do', 'double', 'else', 'enum', 
  'extends', 'final', 'finally', 'float', 'for', 'goto', 'if', 'implements', 
  'import', 'instanceof', 'int', 'interface', 'long', 'native', 'new', 'package', 
  'private', 'protected', 'public', 'return', 'short', 'static', 'strictfp', 
  'super', 'switch', 'synchronized', 'this', 'throw', 'throws', 'transient', 
  'try', 'void', 'volatile', 'while', 'true', 'false', 'null'
];

/**
 * Collect service configuration
 */
export async function collectServiceConfig(
  name?: string,
  options?: {
    port?: number;
    plugins?: string;
    withDocker?: boolean;
    withK8s?: boolean;
    outputDir?: string;
    yes?: boolean;
  }
): Promise<ServiceConfig> {
  // Non-interactive mode: use default values directly
  if (options?.yes && name) {
    const serviceName = name;
    // Java reserved word handling: safe name for Java package
    const javaPackageName = JAVA_RESERVED_WORDS.includes(name) ? `svc${name}` : name;
    let plugins: PluginDependency[] = [];
    if (options?.plugins) {
      plugins = options.plugins.split(',')
        .map(n => n.trim())
        .map(n => AVAILABLE_PLUGINS.find(p => p.name === n))
        .filter((p): p is PluginDependency => p !== undefined);
    }
    
    return {
      name: serviceName,
      javaPackageName,
      fullName: `shinwa-service-${serviceName}`,
      displayName: `${serviceName} Service`,
      description: 'Brix Platform Service',
      port: options?.port || 8080,
      author: 'Shinwa Team',
      version: '1.0.0-SNAPSHOT',
      outputDir: options?.outputDir || '.',
      plugins,
      withDocker: options?.withDocker ?? true,
      withK8s: options?.withK8s ?? false,
      baseUrl: 'http://localhost:8900',
      heartbeatInterval: '30s',
      apiKey: '${SHINWA_SERVICE_API_KEY:platform-service-key}',
      apiSecret: '${SHINWA_SERVICE_API_SECRET:platform-service-secret}',
    };
  }

  const answers = await inquirer.prompt([
    {
      type: 'input',
      name: 'name',
      message: 'Service name (without shinwa-service- prefix):',
      default: name,
      validate: (input: string) => {
        if (!/^[a-z][a-z0-9-]*$/.test(input)) {
          return 'Service name can only contain lowercase letters, numbers, and hyphens';
        }
        return true;
      },
      when: !name,
    },
    {
      type: 'input',
      name: 'displayName',
      message: 'Display name (Chinese or English):',
      default: (answers: { name?: string }) => {
        const serviceName = name || answers.name || '';
        return serviceName.replace(/-/g, ' ') + ' Service';
      },
    },
    {
      type: 'input',
      name: 'description',
      message: 'Service description:',
      default: 'Brix Platform Service',
    },
    {
      type: 'number',
      name: 'port',
      message: 'Service port:',
      default: options?.port || 8080,
      validate: (input: number) => {
        if (input < 1024 || input > 65535) {
          return 'Port must be between 1024-65535';
        }
        return true;
      },
      when: !options?.port,
    },
    {
      type: 'checkbox',
      name: 'plugins',
      message: 'Select plugins to assemble:',
      choices: AVAILABLE_PLUGINS.map(p => ({
        name: p.name,
        value: p,
        checked: options?.plugins?.split(',').includes(p.name),
      })),
      when: !options?.plugins,
    },
    {
      type: 'input',
      name: 'author',
      message: 'Author:',
      default: 'Shinwa Team',
    },
    {
      type: 'confirm',
      name: 'withDocker',
      message: 'Generate Docker configuration?',
      default: options?.withDocker ?? true,
      when: options?.withDocker === undefined,
    },
    {
      type: 'confirm',
      name: 'withK8s',
      message: 'Generate Kubernetes configuration?',
      default: options?.withK8s ?? false,
      when: options?.withK8s === undefined,
    },
    {
      type: 'input',
      name: 'baseUrl',
      message: 'Base platform URL:',
      default: 'http://localhost:8900',
    },
    {
      type: 'input',
      name: 'outputDir',
      message: 'Output directory:',
      default: options?.outputDir || '.',
      when: !options?.outputDir,
    },
  ]);

  // Parse plugins parameter
  let plugins: PluginDependency[] = answers.plugins || [];
  if (options?.plugins) {
    plugins = options.plugins.split(',')
      .map(name => name.trim())
      .map(name => AVAILABLE_PLUGINS.find(p => p.name === name))
      .filter((p): p is PluginDependency => p !== undefined);
  }

  const serviceName = name || answers.name;
  // Java reserved word handling: safe name for Java package
  const javaPackageName = JAVA_RESERVED_WORDS.includes(serviceName) ? `svc${serviceName}` : serviceName;

  return {
    name: serviceName,
    javaPackageName,
    fullName: `shinwa-service-${serviceName}`,
    displayName: answers.displayName,
    description: answers.description,
    port: options?.port || answers.port || 8080,
    author: answers.author,
    version: '1.0.0-SNAPSHOT',
    outputDir: options?.outputDir || answers.outputDir || '.',
    plugins,
    withDocker: options?.withDocker ?? answers.withDocker ?? true,
    withK8s: options?.withK8s ?? answers.withK8s ?? false,
    baseUrl: answers.baseUrl || 'http://localhost:8900',
    heartbeatInterval: '30s',
    apiKey: '${SHINWA_SERVICE_API_KEY:platform-service-key}',
    apiSecret: '${SHINWA_SERVICE_API_SECRET:platform-service-secret}',
  };
}

/**
 * Confirm service configuration
 */
export async function confirmServiceConfig(config: ServiceConfig): Promise<boolean> {
  console.log(chalk.cyan('\n📋 Configuration Confirmation:'));
  console.log(chalk.gray('─'.repeat(50)));
  console.log(`  Service Name: ${chalk.yellow(config.fullName)}`);
  console.log(`  Display Name: ${config.displayName}`);
  console.log(`  Description: ${config.description}`);
  console.log(`  Port: ${chalk.yellow(config.port)}`);
  console.log(`  Base Platform URL: ${config.baseUrl}`);
  console.log(`  Assembled Plugins:`);
  if (config.plugins.length === 0) {
    console.log(`    ${chalk.gray('(none)')}`);
  } else {
    config.plugins.forEach(p => {
      console.log(`    - ${chalk.green(p.name)}`);
    });
  }
  console.log(`  Docker: ${config.withDocker ? chalk.green('✓') : chalk.gray('✗')}`);
  console.log(`  Kubernetes: ${config.withK8s ? chalk.green('✓') : chalk.gray('✗')}`);
  console.log(`  Output Directory: ${config.outputDir}`);
  console.log(chalk.gray('─'.repeat(50)));

  const { confirmed } = await inquirer.prompt([
    {
      type: 'confirm',
      name: 'confirmed',
      message: 'Confirm creation?',
      default: true,
    },
  ]);

  return confirmed;
}

// =====================================================
// v3.0 Business Application Configuration (App)
// =====================================================

/**
 * Allocated Web frontend ports (per port planning document)
 * 
 * Used to automatically assign dev ports for new modules
 */
const ALLOCATED_WEB_PORTS: Record<string, number> = {
  'booking': 3031,      // shinwa-app-booking
  'identity': 3032,     // shinwa-app-identity  
  'messenger': 3033,    // shinwa-app-messenger
  'carousel': 3034,     // shinwa-app-carousel
  'partners': 3035,     // shinwa-app-partners
  'products': 3036,     // shinwa-app-products
  'storage': 3037,      // shinwa-app-storage
  'contracts': 3038,    // shinwa-app-contracts
  'workflow': 3039,     // shinwa-app-workflow
  'intake': 3040,       // shinwa-app-intake
  'compliance': 3041,   // shinwa-app-compliance
};

/**
 * Get next available Web port
 * 
 * Assigns ports for unknown modules starting from 3050
 */
function getNextAvailableWebPort(): number {
  const maxAllocated = Math.max(...Object.values(ALLOCATED_WEB_PORTS), 3049);
  return maxAllocated + 1;
}

/**
 * Collect business application configuration (v3.0 architecture)
 * 
 * Following the v3.0 Runtime Shell Architecture Blueprint, creates business application modules
 * that conform to Runtime Shell capability contracts.
 * 
 * @param name Application name (optional, skips name input if provided)
 * @param options Command line options
 * @returns Promise<AppConfig> Application configuration
 * 
 * @example
 * ```typescript
 * // Interactive creation
 * const config = await collectAppConfig();
 * 
 * // Create with specified name
 * const config = await collectAppConfig('booking', { withUi: true });
 * ```
 */
export async function collectAppConfig(
  name?: string,
  options?: {
    withApi?: boolean;
    withCore?: boolean;
    withServer?: boolean;
    withShared?: boolean;
    withUi?: boolean;
    withUiWeb?: boolean;
    withUiMobile?: boolean;
    withApp?: boolean;
    withDocker?: boolean;
    withK8s?: boolean;
    withPact?: boolean;
    outputDir?: string;
    yes?: boolean;
  }
): Promise<AppConfig> {
  // ===== Non-interactive mode: use default values directly =====
  if (options?.yes && name) {
    const webPort = ALLOCATED_WEB_PORTS[name] || getNextAvailableWebPort();
    const mobilePort = webPort + 1000; // Mobile port = Web port + 1000
    
    return {
      name,
      fullName: `shinwa-app-${name}`,
      displayName: `${name.charAt(0).toUpperCase()}${name.slice(1)} Module`,
      description: 'Brix Platform v3.0 Business Application Module',
      moduleType: 'business',
      author: 'Shinwa Team',
      version: '3.0.0-SNAPSHOT',
      outputDir: options.outputDir || '.',
      withApi: options.withApi ?? true,
      withCore: options.withCore ?? true,
      withServer: options.withServer ?? true,
      withShared: options.withShared ?? true,
      withUi: options.withUi ?? true,
      withUiWeb: options.withUiWeb ?? options.withUi ?? true,
      withUiMobile: options.withUiMobile ?? true,
      withApp: options.withApp ?? true,
      withPact: options.withPact ?? false,
      webPort,
      mobilePort,
      requiredCapabilities: [...DEFAULT_REQUIRED_CAPABILITIES],
      optionalCapabilities: ['state-store'],
      publishesEvents: [],
      subscribesEvents: [],
      startupOrder: 100,
      dependsOn: [],
      withDocker: options.withDocker ?? true,
      withK8s: options.withK8s ?? false,
    };
  }

  // ===== Interactive mode =====
  const answers = await inquirer.prompt([
    {
      type: 'input',
      name: 'name',
      message: 'Application name (kebab-case, e.g., booking, user-auth):',
      default: name,
      validate: (input: string) => {
        if (!/^[a-z][a-z0-9-]*$/.test(input)) {
          return 'Name can only contain lowercase letters, numbers, and hyphens, starting with a letter';
        }
        if (input.startsWith('shinwa-')) {
          return 'Do not include shinwa- prefix, it will be added automatically';
        }
        return true;
      },
      when: !name,
    },
    {
      type: 'input',
      name: 'displayName',
      message: 'Display name (Chinese or English):',
      default: (answers: { name?: string }) => {
        const appName = name || answers.name || '';
        return appName.split('-').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ') + ' Module';
      },
    },
    {
      type: 'input',
      name: 'description',
      message: 'Application description:',
      default: 'Brix Platform v3.0 Business Application Module',
    },
    {
      type: 'list',
      name: 'moduleType',
      message: 'Module type:',
      choices: [
        { name: 'business (Business Module)', value: 'business' },
        { name: 'infrastructure (Infrastructure Module)', value: 'infrastructure' },
      ],
      default: 'business',
    },
    {
      type: 'input',
      name: 'author',
      message: 'Author:',
      default: 'Shinwa Team',
    },
    {
      type: 'confirm',
      name: 'withApi',
      message: 'Include API module (for other modules to depend on)?',
      default: options?.withApi ?? true,
      when: options?.withApi === undefined,
    },
    {
      type: 'confirm',
      name: 'withCore',
      message: 'Include Core module (business logic)?',
      default: options?.withCore ?? true,
      when: options?.withCore === undefined,
    },
    {
      type: 'confirm',
      name: 'withUi',
      message: 'Include UI Web module (frontend interface)?',
      default: options?.withUi ?? true,
      when: options?.withUi === undefined && options?.withUiWeb === undefined,
    },
    {
      type: 'confirm',
      name: 'withUiMobile',
      message: 'Include UI Mobile module (React Native mobile)?',
      default: options?.withUiMobile ?? true,
      when: options?.withUiMobile === undefined,
    },
    {
      type: 'confirm',
      name: 'withServer',
      message: 'Include Server module (REST Controller + AutoConfiguration)?',
      default: options?.withServer ?? true,
      when: options?.withServer === undefined,
    },
    {
      type: 'confirm',
      name: 'withShared',
      message: 'Include Shared module (frontend-backend shared types + orval code generation)?',
      default: options?.withShared ?? true,
      when: options?.withShared === undefined,
    },
    {
      type: 'confirm',
      name: 'withApp',
      message: 'Include App module (standalone runnable)?',
      default: options?.withApp ?? true,
      when: options?.withApp === undefined,
    },
    {
      type: 'confirm',
      name: 'withPact',
      message: 'Include Contract Testing (Pact) configuration?',
      default: options?.withPact ?? false,
      when: options?.withPact === undefined,
    },
    {
      type: 'input',
      name: 'webPort',
      message: 'Web frontend dev server port (per port planning 3031-3099):',
      default: (answers: { name?: string }) => {
        const appName = name || answers.name || '';
        return ALLOCATED_WEB_PORTS[appName] || getNextAvailableWebPort();
      },
      validate: (input: string) => {
        const port = parseInt(input, 10);
        if (isNaN(port) || port < 3000 || port > 65535) {
          return 'Port must be a number between 3000-65535';
        }
        return true;
      },
      filter: (input: string) => parseInt(input, 10),
      when: (answers: { withUi?: boolean }) => options?.withUi ?? answers.withUi,
    },
    {
      type: 'input',
      name: 'mobilePort',
      message: 'Mobile frontend Metro Bundler port (default = webPort + 1000):',
      default: (answers: { webPort?: number; name?: string }) => {
        const webPort = answers.webPort || ALLOCATED_WEB_PORTS[name || answers.name || ''] || 3031;
        return webPort + 1000;
      },
      validate: (input: string) => {
        const port = parseInt(input, 10);
        if (isNaN(port) || port < 4000 || port > 65535) {
          return 'Port must be a number between 4000-65535';
        }
        return true;
      },
      filter: (input: string) => parseInt(input, 10),
      when: (answers: { withUiMobile?: boolean }) => options?.withUiMobile ?? answers.withUiMobile,
    },
    {
      type: 'checkbox',
      name: 'requiredCapabilities',
      message: 'Required capabilities (startup fails if missing):',
      choices: [
        { name: 'event-bus (Event Bus)', value: 'event-bus', checked: true },
        { name: 'auth-context (Auth Context)', value: 'auth-context', checked: true },
        { name: 'observability (Observability)', value: 'observability', checked: true },
        { name: 'state-store (State Store)', value: 'state-store', checked: false },
        { name: 'config-store (Config Store)', value: 'config-store', checked: false },
      ],
    },
    {
      type: 'checkbox',
      name: 'optionalCapabilities',
      message: 'Optional capabilities (degrades if missing):',
      choices: [
        { name: 'state-store (State Store)', value: 'state-store', checked: true },
        { name: 'scheduling (Scheduled Tasks)', value: 'scheduling', checked: false },
        { name: 'lock (Distributed Lock)', value: 'lock', checked: false },
        { name: 'resilience (Resilience)', value: 'resilience', checked: false },
      ],
    },
    {
      type: 'input',
      name: 'startupOrder',
      message: 'Startup priority (10=infrastructure, 50=core modules, 100=normal modules):',
      default: (answers: { moduleType?: ModuleType }) => {
        return answers.moduleType === 'infrastructure' ? 10 : 100;
      },
      filter: (input: string) => parseInt(input, 10),
    },
    {
      type: 'confirm',
      name: 'withDocker',
      message: 'Generate Docker configuration?',
      default: options?.withDocker ?? true,
      when: options?.withDocker === undefined,
    },
    {
      type: 'confirm',
      name: 'withK8s',
      message: 'Generate Kubernetes configuration?',
      default: options?.withK8s ?? false,
      when: options?.withK8s === undefined,
    },
    {
      type: 'input',
      name: 'outputDir',
      message: 'Output directory:',
      default: options?.outputDir || '.',
      when: !options?.outputDir,
    },
  ]);

  const appName = name || answers.name;
  const webPort = answers.webPort || ALLOCATED_WEB_PORTS[appName] || getNextAvailableWebPort();
  const mobilePort = answers.mobilePort || webPort + 1000;

  return {
    name: appName,
    fullName: `shinwa-app-${appName}`,
    displayName: answers.displayName,
    description: answers.description,
    moduleType: answers.moduleType || 'business',
    author: answers.author,
    version: '3.0.0-SNAPSHOT',
    outputDir: options?.outputDir || answers.outputDir || '.',
    withApi: options?.withApi ?? answers.withApi ?? true,
    withCore: options?.withCore ?? answers.withCore ?? true,
    withServer: options?.withServer ?? answers.withServer ?? true,
    withShared: options?.withShared ?? answers.withShared ?? true,
    withUi: options?.withUi ?? answers.withUi ?? true,
    withUiWeb: options?.withUiWeb ?? options?.withUi ?? answers.withUi ?? true,
    withUiMobile: options?.withUiMobile ?? answers.withUiMobile ?? true,
    withApp: options?.withApp ?? answers.withApp ?? true,
    withPact: options?.withPact ?? answers.withPact ?? false,
    webPort,
    mobilePort,
    requiredCapabilities: answers.requiredCapabilities || [...DEFAULT_REQUIRED_CAPABILITIES],
    optionalCapabilities: answers.optionalCapabilities || ['state-store'],
    publishesEvents: [],
    subscribesEvents: [],
    startupOrder: answers.startupOrder || 100,
    dependsOn: [],
    withDocker: options?.withDocker ?? answers.withDocker ?? true,
    withK8s: options?.withK8s ?? answers.withK8s ?? false,
  };
}

/**
 * Confirm Business Application Configuration
 * 
 * Display configuration summary and request user confirmation
 * 
 * @param config Application configuration
 * @returns Promise<boolean> Whether user confirmed
 */
export async function confirmAppConfig(config: AppConfig): Promise<boolean> {
  console.log(chalk.cyan('\n📋 Business Application Configuration Confirmation (v3.0.4 Architecture):'));
  console.log(chalk.gray('─'.repeat(60)));
  console.log(`  Application Name: ${chalk.yellow(config.fullName)}`);
  console.log(`  Display Name: ${config.displayName}`);
  console.log(`  Description: ${config.description}`);
  console.log(`  Module Type: ${chalk.cyan(config.moduleType)}`);
  console.log(`  Startup Priority: ${config.startupOrder}`);
  console.log(`  Module Structure (Backend):`);
  console.log(`    - API:    ${config.withApi ? chalk.green('✓') : chalk.gray('✗')}`);
  console.log(`    - Core:   ${config.withCore ? chalk.green('✓') : chalk.gray('✗')}`);
  console.log(`    - Server: ${config.withServer ? chalk.green('✓') : chalk.gray('✗')}`);
  console.log(`    - App:    ${config.withApp ? chalk.green('✓') : chalk.gray('✗')}`);
  console.log(`  Module Structure (Frontend):`);
  console.log(`    - Shared:    ${config.withShared ? chalk.green('✓') : chalk.gray('✗')}`);
  console.log(`    - UI Web:    ${config.withUiWeb || config.withUi ? chalk.green('✓') + chalk.gray(` (Port: ${config.webPort})`) : chalk.gray('✗')}`);
  console.log(`    - UI Mobile: ${config.withUiMobile ? chalk.green('✓') + chalk.gray(` (Port: ${config.mobilePort})`) : chalk.gray('✗')}`);
  console.log(`  Contract Testing (Pact): ${config.withPact ? chalk.green('✓') : chalk.gray('✗')}`);
  console.log(`  Required Capabilities:`);
  if (config.requiredCapabilities.length === 0) {
    console.log(`    ${chalk.gray('(None)')}`);
  } else {
    config.requiredCapabilities.forEach(cap => {
      console.log(`    - ${chalk.green(cap)}`);
    });
  }
  console.log(`  Optional Capabilities:`);
  if (config.optionalCapabilities.length === 0) {
    console.log(`    ${chalk.gray('(None)')}`);
  } else {
    config.optionalCapabilities.forEach(cap => {
      console.log(`    - ${chalk.blue(cap)}`);
    });
  }
  console.log(`  Docker: ${config.withDocker ? chalk.green('✓') : chalk.gray('✗')}`);
  console.log(`  Kubernetes: ${config.withK8s ? chalk.green('✓') : chalk.gray('✗')}`);
  console.log(`  Output Directory: ${config.outputDir}`);
  console.log(chalk.gray('─'.repeat(60)));

  const { confirmed } = await inquirer.prompt([
    {
      type: 'confirm',
      name: 'confirmed',
      message: 'Confirm creation?',
      default: true,
    },
  ]);

  return confirmed;
}

/**
 * Print Business Application Configuration Summary (Non-interactive Mode)
 * 
 * @param config Application configuration
 */
export function printAppConfigSummary(config: AppConfig): void {
  console.log(chalk.cyan('\n📋 Configuration Summary (Non-interactive Mode - v3.0 Architecture):'));
  console.log(chalk.gray('─'.repeat(60)));
  console.log(`  Application Name: ${chalk.yellow(config.fullName)}`);
  console.log(`  Module Type: ${chalk.cyan(config.moduleType)}`);
  console.log(`  Module Structure: API=${config.withApi}, Core=${config.withCore}, UI=${config.withUi}, App=${config.withApp}`);
  if (config.withUi) {
    console.log(`  Web Port: ${config.webPort}`);
  }
  console.log(chalk.gray('─'.repeat(60)));
}
