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
 * @file Plugin Prompts
 * @description Interactive prompts for plugin scaffolding
 * @module @brix-sdk/create-brix
 * @version 3.0
 */

import inquirer from 'inquirer';
import chalk from 'chalk';
import type { PluginConfig, PluginDependency } from './types.js';

/**
 * Available plugins list (for selection during service creation)
 *
 * Version unified to 1.0.0-SNAPSHOT (development stage)
 */
export const AVAILABLE_PLUGINS: PluginDependency[] = [
  { name: 'plugin-user', version: '1.0.0-SNAPSHOT', groupId: 'io.brix.plugin', artifactId: 'plugin-user-core' },
  { name: 'plugin-contract', version: '1.0.0-SNAPSHOT', groupId: 'io.brix.plugin', artifactId: 'plugin-contract-core' },
  { name: 'plugin-file-center', version: '1.0.0-SNAPSHOT', groupId: 'io.brix.plugin', artifactId: 'plugin-file-center-core' },
  { name: 'plugin-notification', version: '1.0.0-SNAPSHOT', groupId: 'io.brix.plugin', artifactId: 'plugin-notification-core' },
  { name: 'plugin-partner-catalog', version: '1.0.0-SNAPSHOT', groupId: 'io.brix.plugin', artifactId: 'plugin-partner-catalog-core' },
  { name: 'plugin-service-package', version: '1.0.0-SNAPSHOT', groupId: 'io.brix.plugin', artifactId: 'plugin-service-package-core' },
  { name: 'plugin-medical-intake', version: '1.0.0-SNAPSHOT', groupId: 'io.brix.plugin', artifactId: 'plugin-medical-intake-core' },
  { name: 'plugin-risk-compliance', version: '1.0.0-SNAPSHOT', groupId: 'io.brix.plugin', artifactId: 'plugin-risk-compliance-core' },
];

/**
 * Flyway prefix allocation table
 */
export const FLYWAY_PREFIXES: Record<string, string> = {
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
      default: 'Brix Team',
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
  console.log(chalk.cyan('\n?? Configuration Confirmation:'));
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
