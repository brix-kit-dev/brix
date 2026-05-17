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
 * @file cli.ts
 * @description Brix Platform Scaffolding CLI Entry
 * @module @brix-sdk/create-brix
 * @version 3.0.4
 * 
 * v3.0.4 Changes:
 * - Added add-module subcommand to add sub-modules to existing applications
 * - app command added --with-server, --with-shared, --with-ui-mobile, --with-pact options
 * 
 * v3.0 supports three generation types:
 * - plugin: Plugin skeleton (v2.x legacy architecture, pure JAR, depends only on platform-common)
 * - service: Service skeleton (v2.x legacy architecture, runnable, depends on platform-common-starter)
 * - app: Business application module (v3.0 new architecture, follows Runtime Shell capability contracts)
 */

import { Command } from 'commander';
import chalk from 'chalk';
import ora from 'ora';
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { 
  collectPluginConfig, 
  confirmConfig, 
  collectServiceConfig,
  confirmServiceConfig,
  collectAppConfig,
  confirmAppConfig,
  printAppConfigSummary,
} from './prompts.js';
import { generatePlugin, generateService, generateApp } from './generator.js';
import type { PluginConfig, ServiceConfig, AppConfig } from './types.js';

// Version number
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const packageJson = JSON.parse(
  readFileSync(join(__dirname, '..', 'package.json'), 'utf-8')
);
const VERSION = packageJson.version;

const program = new Command();

/**
 * Print Banner
 * 
 * [v3.2.0 Fix] Version number changed from hardcoded to dynamically read from package.json
 */
function printBanner(): void {
  // Dynamically calculate version display width for banner alignment
  const versionText = `Brix Platform Generator v${VERSION}`;
  // Banner inner width fixed at 66 characters, calculate required padding
  const paddingLength = Math.max(0, 66 - 4 - versionText.length); // 4 = "�U  " + " �U"
  const padding = ' '.repeat(paddingLength);
  
  console.log(chalk.cyan(`
  �X�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�[
  �U                                                                  �U
  �U  ${chalk.bold(versionText)}${padding}�U
  �U                                                                  �U
  �U  v3.0 Runtime Shell Architecture + v2.x Compatibility Mode       �U
  �U  �� app     - Business App Module (v3.0 Recommended, Runtime Shell)�U
  �U  �� plugin  - Plugin Skeleton (v2.x Compatible, Pure JAR)         �U
  �U  �� service - Service Skeleton (v2.x Compatible, Runnable)        �U
  �U                                                                  �U
  �^�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�T�a
  `));
}

/**
 * Create plugin command
 */
async function createPlugin(name?: string, options?: {
  flywayPrefix?: string;
  withWeb?: boolean;
  withMobile?: boolean;
  withApi?: boolean;
  outputDir?: string;
  skipInstall?: boolean;
  skipGit?: boolean;
  dryRun?: boolean;
}): Promise<void> {
  printBanner();
  console.log(chalk.blue('?? Creating Plugin Skeleton\n'));

  try {
    // Collect configuration
    const config = await collectPluginConfig(name, options);
    
    // Confirm configuration
    const confirmed = await confirmConfig(config);
    if (!confirmed) {
      console.log(chalk.yellow('\nCreation cancelled'));
      return;
    }

    // Generate plugin
    const spinner = ora('Generating plugin skeleton...').start();
    
    if (options?.dryRun) {
      spinner.info('Preview mode - files will not be created');
      console.log(chalk.gray(JSON.stringify(config, null, 2)));
      return;
    }

    await generatePlugin(config);
    spinner.succeed(chalk.green('Plugin skeleton created successfully!'));

    // Print next steps
    printPluginNextSteps(config);
  } catch (err) {
    console.error(chalk.red(`\nError: ${err}`));
    process.exit(1);
  }
}

/**
 * Create service command
 */
async function createService(name?: string, options?: {
  port?: number;
  plugins?: string;
  withDocker?: boolean;
  withK8s?: boolean;
  outputDir?: string;
  skipInstall?: boolean;
  skipGit?: boolean;
  dryRun?: boolean;
  yes?: boolean;
}): Promise<void> {
  printBanner();
  console.log(chalk.blue('?? Creating Service Skeleton\n'));

  try {
    // Collect configuration
    const config = await collectServiceConfig(name, options);
    
    // Confirm configuration (skip in non-interactive mode)
    if (!options?.yes) {
      const confirmed = await confirmServiceConfig(config);
      if (!confirmed) {
        console.log(chalk.yellow('\nCreation cancelled'));
        return;
      }
    } else {
      // Non-interactive mode: print config without asking
      printServiceConfigSummary(config);
    }

    // Generate service
    const spinner = ora('Generating service skeleton...').start();
    
    if (options?.dryRun) {
      spinner.info('Preview mode - files will not be created');
      console.log(chalk.gray(JSON.stringify(config, null, 2)));
      return;
    }

    await generateService(config);
    spinner.succeed(chalk.green('Service skeleton created successfully!'));

    // Print next steps
    printServiceNextSteps(config);
  } catch (err) {
    console.error(chalk.red(`\nError: ${err}`));
    process.exit(1);
  }
}

/**
 * Print next steps after plugin creation
 */
function printPluginNextSteps(config: PluginConfig): void {
  console.log(chalk.cyan('\n?? Next Steps:'));
  console.log(chalk.gray('��'.repeat(50)));
  console.log(`
  1. Enter the plugin directory:
     ${chalk.yellow(`cd ${config.outputDir}/${config.name}`)}

  2. Build the plugin:
     ${chalk.yellow('mvn clean install')}

  3. Add dependency to your service:
     ${chalk.gray(`<dependency>
       <groupId>io.brix.plugin</groupId>
       <artifactId>${config.name}-core</artifactId>
       <version>0.1.0-SNAPSHOT</version>
     </dependency>`)}
  `);
}

/**
 * Print service configuration summary (non-interactive mode)
 */
function printServiceConfigSummary(config: ServiceConfig): void {
  console.log(chalk.cyan('\n?? Configuration Summary (Non-interactive Mode):'));
  console.log(chalk.gray('��'.repeat(50)));
  console.log(`  Service Name: ${chalk.yellow(config.fullName)}`);
  console.log(`  Port: ${chalk.yellow(config.port)}`);
  console.log(`  Assembled Plugins:`);
  if (config.plugins.length === 0) {
    console.log(`    ${chalk.gray('(none)')}`);
  } else {
    config.plugins.forEach(p => {
      console.log(`    - ${chalk.green(p.name)}`);
    });
  }
  console.log(chalk.gray('��'.repeat(50)));
}

/**
 * Print next steps after service creation
 */
function printServiceNextSteps(config: ServiceConfig): void {
  console.log(chalk.cyan('\n?? Next Steps:'));
  console.log(chalk.gray('��'.repeat(50)));
  console.log(`
  1. Enter the service directory:
     ${chalk.yellow(`cd ${config.outputDir}/${config.name}`)}

  2. Build the service:
     ${chalk.yellow('mvn clean package')}

  3. Start the service:
     ${chalk.yellow('docker-compose up -d')}

  4. Access the service:
     ${chalk.yellow(`http://localhost:${config.port}`)}
  `);
}

// =====================================================
// v3.0 Business Application Command (App)
// =====================================================

/**
 * Create business application command (v3.0 architecture)
 * 
 * Generates business application modules following the v3.0 Runtime Shell architecture, including:
 * - module-manifest.yaml (module declaration file)
 * - {name}-api (API module)
 * - {name}-core (business logic module)
 * - {name}-server (REST Controller module)
 * - {name}-shared (frontend-backend shared types module)
 * - {name}-ui-web (UI module)
 * - {name}-ui-mobile (mobile UI module)
 * - {name}-app (standalone runnable module)
 */
async function createApp(name?: string, options?: {
  withApi?: boolean;
  withCore?: boolean;
  withServer?: boolean;
  withShared?: boolean;
  withUi?: boolean;
  withUiMobile?: boolean;
  withApp?: boolean;
  withPact?: boolean;
  withDocker?: boolean;
  withK8s?: boolean;
  outputDir?: string;
  skipInstall?: boolean;
  skipGit?: boolean;
  dryRun?: boolean;
  yes?: boolean;
}): Promise<void> {
  printBanner();
  console.log(chalk.blue('?? Creating Business Application Module (v3.0 Architecture)\n'));

  try {
    // Collect configuration
    const config = await collectAppConfig(name, options);
    
    // Confirm configuration (skip in non-interactive mode)
    if (!options?.yes) {
      const confirmed = await confirmAppConfig(config);
      if (!confirmed) {
        console.log(chalk.yellow('\nCreation cancelled'));
        return;
      }
    } else {
      // Non-interactive mode: print config without asking
      printAppConfigSummary(config);
    }

    // Generate application
    const spinner = ora('Generating business application module...').start();
    
    if (options?.dryRun) {
      spinner.info('Preview mode - files will not be created');
      console.log(chalk.gray(JSON.stringify(config, null, 2)));
      return;
    }

    await generateApp(config);
    spinner.succeed(chalk.green('Business application module created successfully!'));

    // Print next steps
    printAppNextSteps(config);
  } catch (err) {
    console.error(chalk.red(`\nError: ${err}`));
    process.exit(1);
  }
}

/**
 * Print next steps after business application creation
 */
function printAppNextSteps(config: AppConfig): void {
  console.log(chalk.cyan('\n?? Next Steps (v3.0 Architecture):'));
  console.log(chalk.gray('��'.repeat(60)));
  console.log(`
  1. Enter the application directory:
     ${chalk.yellow(`cd ${config.outputDir}/${config.fullName}`)}

  2. Build backend modules:
     ${chalk.yellow('mvn clean install')}

  3. Install frontend dependencies:
     ${chalk.yellow(`cd ${config.name}-ui-web && pnpm install`)}

  4. Start frontend dev server:
     ${chalk.yellow(`pnpm dev`)}
     ${chalk.gray(`Access: http://localhost:${config.webPort}`)}

  5. Key files:
     ${chalk.gray('�� module-manifest.yaml - Module declaration (capabilities/events/dependencies)')}
     ${chalk.gray('�� {name}-core/service - Use RuntimeContext to access capabilities')}
     ${chalk.gray('�� {name}-ui-web/hooks - Use UIRuntimeContext to access UI capabilities')}
     ${chalk.gray('�� {name}-shared - Frontend-backend shared type definitions')}

  6. Deploy to Host:
     ${chalk.gray('Deploy build artifacts to Full Product Host or Embedded Host')}
  `);
}

// Configure command line
program
  .name('create-brix')
  .description('Brix Platform Scaffolding - Create business applications, plugins and services')
  .version(VERSION);

// ===== v3.0 Recommended: Create Business Application Command =====
program
  .command('app [name]')
  .description('[v3.0 Recommended] Create business application module (follows Runtime Shell architecture)')
  .option('--with-api', 'Include API module', true)
  .option('--no-with-api', 'Exclude API module')
  .option('--with-core', 'Include Core module', true)
  .option('--no-with-core', 'Exclude Core module')
  .option('--with-server', 'Include Server module (REST Controller)', true)
  .option('--no-with-server', 'Exclude Server module')
  .option('--with-shared', 'Include Shared module (frontend-backend shared types)', true)
  .option('--no-with-shared', 'Exclude Shared module')
  .option('--with-ui', 'Include UI Web module', true)
  .option('--no-with-ui', 'Exclude UI Web module')
  .option('--with-ui-mobile', 'Include UI Mobile module', true)
  .option('--no-with-ui-mobile', 'Exclude UI Mobile module')
  .option('--with-app', 'Include App module (independently runnable)', true)
  .option('--no-with-app', 'Exclude App module')
  .option('--with-pact', 'Include Contract Testing (Pact) configuration', false)
  .option('--with-docker', 'Generate Docker configuration', true)
  .option('--no-with-docker', 'Skip Docker configuration')
  .option('--with-k8s', 'Generate Kubernetes configuration', false)
  .option('-o, --output-dir <dir>', 'Output directory', '.')
  .option('--skip-install', 'Skip dependency installation')
  .option('--skip-git', 'Skip git initialization')
  .option('--dry-run', 'Preview only, do not create files')
  .option('-y, --yes', 'Non-interactive mode, use default values')
  .action(createApp);

// ===== v3.0.4 New: Add Sub-module Command =====
program
  .command('add-module <name>')
  .description('Add sub-module to existing application (shared/ui-web/ui-mobile/server)')
  .requiredOption('-t, --type <type>', 'Module type: shared | ui-web | ui-mobile | server')
  .option('-o, --output-dir <dir>', 'Application root directory', '.')
  .option('--dry-run', 'Preview only, do not create files')
  .action(async (name: string, options: { type: string; outputDir: string; dryRun?: boolean }) => {
    printBanner();
    console.log(chalk.blue(`? Adding ${options.type} sub-module to application\n`));
    
    const validTypes = ['shared', 'ui-web', 'ui-mobile', 'server'];
    if (!validTypes.includes(options.type)) {
      console.error(chalk.red(`Error: Invalid module type "${options.type}"`));
      console.log(chalk.gray(`Supported types: ${validTypes.join(', ')}`));
      process.exit(1);
    }
    
    if (options.dryRun) {
      console.log(chalk.yellow('Preview mode - files will not be created'));
      console.log(chalk.gray(`Will create ${name}-${options.type} module under ${options.outputDir}`));
      return;
    }
    
    // Build minimal config, enable only specified module type
    const config: AppConfig = {
      name,
      fullName: `app-${name}`,
      displayName: `${name} Module`,
      description: 'Brix Platform v3.0 Business Application Module',
      moduleType: 'business',
      author: 'Brix Team',
      version: '3.0.0-SNAPSHOT',
      outputDir: options.outputDir,
      withApi: false,
      withCore: false,
      withServer: options.type === 'server',
      withShared: options.type === 'shared',
      withUi: false,
      withUiWeb: options.type === 'ui-web',
      withUiMobile: options.type === 'ui-mobile',
      withApp: false,
      withPact: false,
      webPort: 3031,
      mobilePort: 4031,
      requiredCapabilities: [],
      optionalCapabilities: [],
      publishesEvents: [],
      subscribesEvents: [],
      startupOrder: 100,
      dependsOn: [],
      withDocker: false,
      withK8s: false,
    };
    
    const spinner = ora(`Generating ${options.type} sub-module...`).start();
    try {
      await generateApp(config);
      spinner.succeed(chalk.green(`${options.type} sub-module created successfully!`));
      console.log(chalk.gray(`\nLocation: ${options.outputDir}/${config.fullName}/${name}-${options.type === 'ui-web' ? 'ui-web' : options.type}`));
    } catch (err) {
      spinner.fail(chalk.red(`Creation failed: ${err}`));
      process.exit(1);
    }
  });

// ===== v2.x Compatible: Create Plugin Command =====
program
  .command('plugin [name]')
  .description('[v2.x Compatible] Create plugin skeleton')
  .option('-f, --flyway-prefix <prefix>', 'Flyway version prefix (3 digits)')
  .option('--with-web', 'Include Web frontend module', true)
  .option('--no-with-web', 'Exclude Web frontend module')
  .option('--with-mobile', 'Include Mobile frontend module', false)
  .option('--with-api', 'Include API module', true)
  .option('--no-with-api', 'Exclude API module')
  .option('-o, --output-dir <dir>', 'Output directory', '.')
  .option('--skip-install', 'Skip dependency installation')
  .option('--skip-git', 'Skip git initialization')
  .option('--dry-run', 'Preview only, do not create files')
  .action(createPlugin);

// ===== v2.x Compatible: Create Service Command =====
program
  .command('service [name]')
  .description('[v2.x Compatible] Create service skeleton')
  .option('-p, --port <port>', 'Service port number', parseInt)
  .option('--plugins <plugins>', 'Dependent plugins list (comma-separated)')
  .option('--with-docker', 'Generate Docker configuration', true)
  .option('--no-with-docker', 'Skip Docker configuration')
  .option('--with-k8s', 'Generate Kubernetes configuration', false)
  .option('-o, --output-dir <dir>', 'Output directory', '.')
  .option('--skip-install', 'Skip dependency installation')
  .option('--skip-git', 'Skip git initialization')
  .option('--dry-run', 'Preview only, do not create files')
  .option('-y, --yes', 'Non-interactive mode, use default values')
  .action(createService);

// Parse command line
program.parse();
