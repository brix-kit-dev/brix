/**
 * @file generator.ts
 * @description Code Generator
 * @module @brix/create-brix
 * @version 3.0
 * 
 * v3.0 Changes:
 * - Added generateApp function to support creating v3.0 business application modules
 * - Added buildAppContext to build App template context
 * - Added App template series (api/core/ui/app/manifest)
 */

import { mkdir, writeFile, readFile, readdir, stat } from 'fs/promises';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import ejs from 'ejs';
import type { 
  PluginConfig, 
  ServiceConfig, 
  PluginTemplateContext, 
  ServiceTemplateContext,
  AppConfig,
  AppTemplateContext,
} from './types.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const TEMPLATES_DIR = join(__dirname, '..', 'templates');

/**
 * Generate Plugin Skeleton
 */
export async function generatePlugin(config: PluginConfig): Promise<void> {
  const context = buildPluginContext(config);
  const outputDir = join(config.outputDir, config.name);
  
  // Create directory
  await mkdir(outputDir, { recursive: true });
  
  // Generate backend modules
  await generateBackendPlugin(context, outputDir);
  
  // Generate frontend modules (if needed)
  if (config.withWeb) {
    await generateFrontendModule(context, outputDir, 'web');
  }
  if (config.withMobile) {
    await generateFrontendModule(context, outputDir, 'mobile');
  }
  
  // Generate common files
  await generateCommonFiles(context, outputDir, 'plugin');
}

/**
 * Generate Service Skeleton
 */
export async function generateService(config: ServiceConfig): Promise<void> {
  const context = buildServiceContext(config);
  const outputDir = join(config.outputDir, config.fullName);
  
  // Create directory structure
  await mkdir(outputDir, { recursive: true });
  
  // Generate service templates
  await generateServiceFiles(context, outputDir);
  
  // Generate common files
  await generateCommonFiles(context, outputDir, 'service');
}

/**
 * Build Plugin Template Context
 */
function buildPluginContext(config: PluginConfig): PluginTemplateContext {
  const nameWithoutPrefix = config.name.replace('plugin-', '');
  const parts = nameWithoutPrefix.split('-');
  const classPrefix = parts.map(p => p.charAt(0).toUpperCase() + p.slice(1)).join('');
  
  return {
    ...config,
    date: new Date().toISOString().split('T')[0],
    // [v3.2.0 Fix] groupId changed from shinwa.plugin to io.brix.plugin
    packageName: `io.brix.plugin.${nameWithoutPrefix.replace(/-/g, '.')}`,
    classPrefix,
  };
}

/**
 * Build Service Template Context
 */
function buildServiceContext(config: ServiceConfig): ServiceTemplateContext {
  // Use javaPackageName (if exists), otherwise use name
  const javaName = config.javaPackageName || config.name;
  const parts = javaName.split('-');
  const classPrefix = parts.map(p => p.charAt(0).toUpperCase() + p.slice(1)).join('');
  
  return {
    ...config,
    date: new Date().toISOString().split('T')[0],
    // [v3.2.0 Fix] packageName changed from shinwa.service to io.brix.service
    packageName: `io.brix.service.${javaName.replace(/-/g, '.')}`,
    classPrefix,
    springServiceName: config.fullName,
  };
}

/**
 * Generate Backend Plugin Modules
 */
async function generateBackendPlugin(
  context: PluginTemplateContext, 
  outputDir: string
): Promise<void> {
  // Generate parent POM
  const parentPomPath = join(outputDir, 'pom.xml');
  await renderTemplate('backend/parent-pom.xml.ejs', parentPomPath, context);
  
  // Generate Core module
  const coreDir = join(outputDir, `${context.name}-core`);
  await generatePluginCoreModule(context, coreDir);
  
  // Generate API module (if needed)
  if (context.withApi) {
    const apiDir = join(outputDir, `${context.name}-api`);
    await generatePluginApiModule(context, apiDir);
  }
}

/**
 * Generate Plugin Core Module
 */
async function generatePluginCoreModule(
  context: PluginTemplateContext,
  outputDir: string
): Promise<void> {
  await mkdir(outputDir, { recursive: true });
  
  const srcDir = join(outputDir, 'src', 'main', 'java', ...context.packageName.split('.'));
  const resourcesDir = join(outputDir, 'src', 'main', 'resources');
  
  await mkdir(srcDir, { recursive: true });
  await mkdir(resourcesDir, { recursive: true });
  
  // Generate pom.xml
  await renderTemplate('backend/core/pom.xml.ejs', join(outputDir, 'pom.xml'), context);
  
  // Generate configuration class
  const configDir = join(srcDir, 'config');
  await mkdir(configDir, { recursive: true });
  await renderTemplate('backend/core/PluginConfiguration.java.ejs', 
    join(configDir, `${context.classPrefix}PluginConfiguration.java`), context);

  // Generate architecture guard test (ArchUnit red line constraint)
  const testDir = join(outputDir, 'src', 'test', 'java', ...context.packageName.split('.'));
  await mkdir(testDir, { recursive: true });
  await renderTemplate('backend/core/ArchitectureTest.java.ejs',
    join(testDir, 'ArchitectureTest.java'), context);
  
  // Generate Flyway script directory (if needed)
  if (context.includeFlyway) {
    const migrationDir = join(resourcesDir, 'db', 'migration', context.name);
    await mkdir(migrationDir, { recursive: true });
    await renderTemplate('backend/core/V001__init.sql.ejs',
      join(migrationDir, `V${context.flywayPrefix}_001__${context.name.replace('plugin-', '')}_init.sql`), 
      context);
  }
}

/**
 * Generate Plugin API Module
 */
async function generatePluginApiModule(
  context: PluginTemplateContext,
  outputDir: string
): Promise<void> {
  await mkdir(outputDir, { recursive: true });
  
  const srcDir = join(outputDir, 'src', 'main', 'java', ...context.packageName.split('.'), 'api');
  await mkdir(srcDir, { recursive: true });
  
  // Generate pom.xml
  await renderTemplate('backend/api/pom.xml.ejs', join(outputDir, 'pom.xml'), context);
  
  // Generate API interface
  await renderTemplate('backend/api/PluginApi.java.ejs',
    join(srcDir, `${context.classPrefix}Api.java`), context);
}

/**
 * Generate Frontend Module
 * 
 * Web module uses Module Federation with rspack configuration.
 * Mobile module is published as npm package with tsup build.
 */
async function generateFrontendModule(
  context: PluginTemplateContext,
  outputDir: string,
  type: 'web' | 'mobile'
): Promise<void> {
  const moduleDir = join(outputDir, `${context.name}-${type}`);
  await mkdir(moduleDir, { recursive: true });
  
  // Generate package.json
  await renderTemplate(`frontend/${type}/package.json.ejs`, 
    join(moduleDir, 'package.json'), context);
  
  // Generate tsconfig.json
  await renderTemplate(`frontend/${type}/tsconfig.json.ejs`,
    join(moduleDir, 'tsconfig.json'), context);
  
  // Web module generates rspack.config.ts (Module Federation config)
  // Mobile module as npm package does not need rspack config
  if (type === 'web') {
    await renderTemplate(`frontend/${type}/rspack.config.ts.ejs`,
      join(moduleDir, 'rspack.config.ts'), context);
  }
  
  // Generate Dockerfile.dev (for Docker dev environment)
  await renderTemplate(`frontend/${type}/Dockerfile.dev.ejs`,
    join(moduleDir, 'Dockerfile.dev'), context);
  
  // Generate src directory structure
  const srcDir = join(moduleDir, 'src');
  await mkdir(srcDir, { recursive: true });
  
  // Generate entry file
  await renderTemplate(`frontend/${type}/index.tsx.ejs`,
    join(srcDir, 'index.tsx'), context);
  
  // Generate exposes directory (Web: Module Federation exposed component wrappers / Mobile: component exports)
  const exposesDir = join(srcDir, 'exposes');
  await mkdir(exposesDir, { recursive: true });
  await renderTemplate(`frontend/${type}/exposes/init.ts.ejs`,
    join(exposesDir, 'init.ts'), context);
  await renderTemplate(`frontend/${type}/exposes/List.tsx.ejs`,
    join(exposesDir, `${context.classPrefix}List.tsx`), context);
  await renderTemplate(`frontend/${type}/exposes/Detail.tsx.ejs`,
    join(exposesDir, `${context.classPrefix}Detail.tsx`), context);
  
  // Generate pages directory (actual page components)
  const pagesDir = join(srcDir, 'pages');
  await mkdir(pagesDir, { recursive: true });
  await renderTemplate(`frontend/${type}/pages/List.tsx.ejs`,
    join(pagesDir, `${context.classPrefix}List.tsx`), context);
  await renderTemplate(`frontend/${type}/pages/Detail.tsx.ejs`,
    join(pagesDir, `${context.classPrefix}Detail.tsx`), context);
  
  // Generate i18n directory (internationalization config)
  const i18nDir = join(srcDir, 'i18n');
  await mkdir(i18nDir, { recursive: true });
  await renderTemplate(`frontend/${type}/i18n/index.ts.ejs`,
    join(i18nDir, 'index.ts'), context);
  
  // Generate public directory
  const publicDir = join(moduleDir, 'public');
  await mkdir(publicDir, { recursive: true });
}

/**
 * Generate Service Files
 */
async function generateServiceFiles(
  context: ServiceTemplateContext,
  outputDir: string
): Promise<void> {
  const srcDir = join(outputDir, 'src', 'main', 'java', ...context.packageName.split('.'));
  const resourcesDir = join(outputDir, 'src', 'main', 'resources');
  
  await mkdir(srcDir, { recursive: true });
  await mkdir(resourcesDir, { recursive: true });
  
  // Generate pom.xml
  await renderTemplate('service/pom.xml.ejs', join(outputDir, 'pom.xml'), context);
  
  // Generate Application.java
  await renderTemplate('service/Application.java.ejs',
    join(srcDir, `${context.classPrefix}Application.java`), context);
  
  // Generate application.yml
  await renderTemplate('service/application.yml.ejs',
    join(resourcesDir, 'application.yml'), context);
  
  // Generate Docker config (if needed)
  if (context.withDocker) {
    await renderTemplate('service/Dockerfile.ejs',
      join(outputDir, 'Dockerfile'), context);
    await renderTemplate('service/docker-compose.yml.ejs',
      join(outputDir, 'docker-compose.yml'), context);
  }
  
  // Generate Kubernetes config (if needed)
  if (context.withK8s) {
    const k8sDir = join(outputDir, 'k8s');
    await mkdir(k8sDir, { recursive: true });
    await renderTemplate('service/k8s/deployment.yml.ejs',
      join(k8sDir, 'deployment.yml'), context);
    await renderTemplate('service/k8s/service.yml.ejs',
      join(k8sDir, 'service.yml'), context);
  }
}

/**
 * Generate Common Files
 */
async function generateCommonFiles(
  context: PluginTemplateContext | ServiceTemplateContext | AppTemplateContext,
  outputDir: string,
  type: 'plugin' | 'service' | 'app'
): Promise<void> {
  // Generate README.md
  await renderTemplate(`common/README-${type}.md.ejs`,
    join(outputDir, 'README.md'), context);
  
  // Generate .gitignore
  await renderTemplate('common/.gitignore.ejs',
    join(outputDir, '.gitignore'), context);
}

/**
 * Render Template File
 */
async function renderTemplate(
  templatePath: string,
  outputPath: string,
  context: Record<string, unknown>
): Promise<void> {
  const fullTemplatePath = join(TEMPLATES_DIR, templatePath);
  
  try {
    // Check if template exists
    await stat(fullTemplatePath);
    
    const template = await readFile(fullTemplatePath, 'utf-8');
    const content = ejs.render(template, context);
    
    await mkdir(dirname(outputPath), { recursive: true });
    await writeFile(outputPath, content, 'utf-8');
  } catch (error) {
    // Create placeholder file when template does not exist
    await mkdir(dirname(outputPath), { recursive: true });
    await writeFile(outputPath, `# TODO: Create template ${templatePath}\n`, 'utf-8');
  }
}

// =====================================================
// v3.0 Business Application Generator (App)
// =====================================================

/**
 * Generate Business Application Skeleton (v3.0 Architecture)
 * 
 * Follows v3.0 Runtime Shell Architecture Blueprint to generate business application module structure
 * conforming to Runtime Shell capability contracts:
 * 
 * ```
 * shinwa-app-{name}/
 * ├── pom.xml                    # Parent POM
 * ├── module-manifest.yaml       # Module declaration file (v3.0 core)
 * ├── {name}-api/                # API module (DTO, Event, Request)
 * ├── {name}-core/               # Core module (business logic)
 * ├── {name}-server/             # Server module (v3.0.4 new: REST Controller + AutoConfiguration)
 * ├── {name}-shared/             # Shared module (v3.0.4 new: frontend-backend shared types + orval code generation)
 * ├── {name}-ui-web/             # UI Web module (frontend interface, renamed from ui module)
 * ├── {name}-ui-mobile/          # UI Mobile module (v3.0.4 new: React Native mobile)
 * └── {name}-app/                # App module (standalone runnable)
 * ```
 * 
 * @param config Application configuration
 */
export async function generateApp(config: AppConfig): Promise<void> {
  const context = buildAppContext(config);
  const outputDir = join(config.outputDir, config.fullName);
  
  // Create root directory
  await mkdir(outputDir, { recursive: true });
  
  // Generate parent POM
  await renderTemplate('app/pom.xml.ejs', join(outputDir, 'pom.xml'), context);
  
  // Generate module-manifest.yaml (v3.0 core configuration file)
  await renderTemplate('app/module-manifest.yaml.ejs', 
    join(outputDir, 'module-manifest.yaml'), context);
  
  // Generate API module (if needed)
  if (config.withApi) {
    await generateAppApiModule(context, outputDir);
  }
  
  // Generate Core module (if needed)
  if (config.withCore) {
    await generateAppCoreModule(context, outputDir);
  }
  
  // Generate Server module (v3.0.4 new: REST Controller + AutoConfiguration)
  if (config.withServer) {
    await generateAppServerModule(context, outputDir);
  }
  
  // Generate Shared module (v3.0.4 new: frontend-backend shared types + orval code generation)
  if (config.withShared) {
    await generateAppSharedModule(context, outputDir);
  }
  
  // Generate UI Web module (if needed - renamed from UI module)
  if (config.withUiWeb || config.withUi) {
    await generateAppUiWebModule(context, outputDir);
  }
  
  // Generate UI Mobile module (v3.0.4 new: React Native)
  if (config.withUiMobile) {
    await generateAppUiMobileModule(context, outputDir);
  }
  
  // Generate App module (if needed, standalone runnable startup module)
  if (config.withApp) {
    await generateAppAppModule(context, outputDir);
  }
  
  // Generate common files
  await generateCommonFiles(context, outputDir, 'app');
  
  // Generate CI configuration files (GitHub Actions)
  await generateCIFiles(context, outputDir);
}

/**
 * Build Business Application Template Context
 * 
 * v3.0.4 Enhancement: Supports NPM package name generation for server, shared, ui-web, ui-mobile module types
 * 
 * @param config Application configuration
 * @returns AppTemplateContext Template context
 */
function buildAppContext(config: AppConfig): AppTemplateContext {
  // Convert from kebab-case to PascalCase
  const parts = config.name.split('-');
  const classPrefix = parts.map(p => p.charAt(0).toUpperCase() + p.slice(1)).join('');
  
  // Build Java package name: com.shinwa.app.{name} (hyphens replaced with dots)
  const packageName = `com.shinwa.app.${config.name.replace(/-/g, '.')}`;
  
  // Build package path (for directory structure)
  const packagePath = packageName.replace(/\./g, '/');
  
  // NPM package names (three frontend modules)
  const npmPackageName = `@shinwa/${config.name}-ui`;
  const npmPackageNameMobile = `@shinwa/${config.name}-ui-mobile`;
  const npmPackageNameShared = `@shinwa/${config.name}-shared`;
  
  return {
    ...config,
    date: new Date().toISOString().split('T')[0],
    packageName,
    packagePath,
    classPrefix,
    npmPackageName,
    npmPackageNameMobile,
    npmPackageNameShared,
  };
}

/**
 * Generate API Module
 * 
 * API module contains:
 * - DTO (Data Transfer Objects)
 * - Event (Domain event definitions)
 * - Request (Request objects)
 * 
 * @param context Template context
 * @param outputDir Output directory
 */
async function generateAppApiModule(
  context: AppTemplateContext,
  outputDir: string
): Promise<void> {
  const moduleDir = join(outputDir, `${context.name}-api`);
  const srcDir = join(moduleDir, 'src', 'main', 'java', ...context.packageName.split('.'), 'api');
  
  await mkdir(srcDir, { recursive: true });
  
  // Generate pom.xml
  await renderTemplate('app/api/pom.xml.ejs', join(moduleDir, 'pom.xml'), context);
  
  // Create subdirectory structure
  const dtoDir = join(srcDir, 'dto');
  const eventDir = join(srcDir, 'event');
  const requestDir = join(srcDir, 'request');
  
  await mkdir(dtoDir, { recursive: true });
  await mkdir(eventDir, { recursive: true });
  await mkdir(requestDir, { recursive: true });
  
  // Generate example DTO
  await renderTemplate('app/api/ExampleDTO.java.ejs', 
    join(dtoDir, `${context.classPrefix}DTO.java`), context);
  
  // Generate example Event (domain event)
  await renderTemplate('app/api/ExampleEvent.java.ejs', 
    join(eventDir, `${context.classPrefix}CreatedEvent.java`), context);
  
  // Generate example Request
  await renderTemplate('app/api/ExampleRequest.java.ejs', 
    join(requestDir, `Create${context.classPrefix}Request.java`), context);
}

/**
 * Generate Core Module
 * 
 * Core module contains:
 * - Entity (Entities)
 * - Repository (Repositories)
 * - Service (Services)
 * - Handler (Event handlers)
 * - Controller (REST controllers)
 * 
 * [IMPORTANT] Core module only depends on RuntimeContext, not directly on infrastructure like Kafka/Redis
 * 
 * @param context Template context
 * @param outputDir Output directory
 */
async function generateAppCoreModule(
  context: AppTemplateContext,
  outputDir: string
): Promise<void> {
  const moduleDir = join(outputDir, `${context.name}-core`);
  const srcDir = join(moduleDir, 'src', 'main', 'java', ...context.packageName.split('.'), 'core');
  const resourcesDir = join(moduleDir, 'src', 'main', 'resources');
  
  await mkdir(srcDir, { recursive: true });
  await mkdir(resourcesDir, { recursive: true });
  
  // Generate pom.xml
  await renderTemplate('app/core/pom.xml.ejs', join(moduleDir, 'pom.xml'), context);
  
  // Create subdirectory structure
  const entityDir = join(srcDir, 'entity');
  const repositoryDir = join(srcDir, 'repository');
  const serviceDir = join(srcDir, 'service');
  const handlerDir = join(srcDir, 'handler');
  const controllerDir = join(srcDir, 'controller');
  const configDir = join(srcDir, 'config');
  
  await mkdir(entityDir, { recursive: true });
  await mkdir(repositoryDir, { recursive: true });
  await mkdir(serviceDir, { recursive: true });
  await mkdir(handlerDir, { recursive: true });
  await mkdir(controllerDir, { recursive: true });
  await mkdir(configDir, { recursive: true });
  
  // Generate configuration class
  await renderTemplate('app/core/ModuleConfiguration.java.ejs',
    join(configDir, `${context.classPrefix}ModuleConfiguration.java`), context);
  
  // Generate example Entity
  await renderTemplate('app/core/ExampleEntity.java.ejs',
    join(entityDir, `${context.classPrefix}Entity.java`), context);
  
  // Generate example Repository
  await renderTemplate('app/core/ExampleRepository.java.ejs',
    join(repositoryDir, `${context.classPrefix}Repository.java`), context);
  
  // Generate example Service (core: uses RuntimeContext)
  await renderTemplate('app/core/ExampleService.java.ejs',
    join(serviceDir, `${context.classPrefix}Service.java`), context);
  
  // Generate example EventHandler
  await renderTemplate('app/core/ExampleEventHandler.java.ejs',
    join(handlerDir, `${context.classPrefix}EventHandler.java`), context);
  
  // Generate example Controller
  await renderTemplate('app/core/ExampleController.java.ejs',
    join(controllerDir, `${context.classPrefix}Controller.java`), context);
  
  // Generate Flyway migration script directory
  const migrationDir = join(resourcesDir, 'db', 'migration', context.fullName);
  await mkdir(migrationDir, { recursive: true });
  await renderTemplate('app/core/V001__init.sql.ejs',
    join(migrationDir, `V001__${context.name}_init.sql`), context);

  // Generate architecture guard test (ArchUnit red line constraint)
  const testDir = join(moduleDir, 'src', 'test', 'java', ...context.packageName.split('.'));
  await mkdir(testDir, { recursive: true });
  await renderTemplate('app/core/ArchitectureTest.java.ejs',
    join(testDir, 'ArchitectureTest.java'), context);
}

/**
 * Generate UI Web Module (v3.0.4 Renamed: Originally UI Module)
 * 
 * UI Web module uses Module Federation architecture:
 * - Served as remote module for dynamic loading by host application
 * - Uses @runtime/ui-sdk to obtain UI capabilities
 * - Uses rspack for building
 * - Added repositories directory (MVVM data access layer)
 * 
 * @param context Template context
 * @param outputDir Output directory
 */
async function generateAppUiWebModule(
  context: AppTemplateContext,
  outputDir: string
): Promise<void> {
  const moduleDir = join(outputDir, `${context.name}-ui-web`);
  const srcDir = join(moduleDir, 'src');
  const publicDir = join(moduleDir, 'public');
  
  await mkdir(srcDir, { recursive: true });
  await mkdir(publicDir, { recursive: true });
  
  // Generate package.json
  await renderTemplate('app/ui/package.json.ejs', join(moduleDir, 'package.json'), context);
  
  // Generate tsconfig.json
  await renderTemplate('app/ui/tsconfig.json.ejs', join(moduleDir, 'tsconfig.json'), context);
  
  // Generate rspack.config.cjs (Module Federation config)
  await renderTemplate('app/ui/rspack.config.cjs.ejs', join(moduleDir, 'rspack.config.cjs'), context);
  
  // Generate ui-manifest.yaml (UI module declaration)
  await renderTemplate('app/ui/ui-manifest.yaml.ejs', join(moduleDir, 'ui-manifest.yaml'), context);

  // Generate eslint.config.js (architecture guard rules)
  await renderTemplate('app/ui/eslint.config.js.ejs', join(moduleDir, 'eslint.config.js'), context);
  
  // Generate public/index.html
  await renderTemplate('app/ui/index.html.ejs', join(publicDir, 'index.html'), context);
  
  // Generate entry files
  await renderTemplate('app/ui/bootstrap.tsx.ejs', join(srcDir, 'bootstrap.tsx'), context);
  await renderTemplate('app/ui/App.tsx.ejs', join(srcDir, 'App.tsx'), context);
  
  // Create component directories
  const componentsDir = join(srcDir, 'components');
  const pagesDir = join(srcDir, 'pages');
  const hooksDir = join(srcDir, 'hooks');
  const repositoriesDir = join(srcDir, 'repositories');
  
  await mkdir(componentsDir, { recursive: true });
  await mkdir(pagesDir, { recursive: true });
  await mkdir(hooksDir, { recursive: true });
  await mkdir(repositoriesDir, { recursive: true });
  
  // Generate example pages
  await renderTemplate('app/ui/pages/List.tsx.ejs',
    join(pagesDir, `${context.classPrefix}List.tsx`), context);
  await renderTemplate('app/ui/pages/Detail.tsx.ejs',
    join(pagesDir, `${context.classPrefix}Detail.tsx`), context);
  
  // Generate example Hook (uses UIRuntimeContext)
  await renderTemplate('app/ui/hooks/useModule.ts.ejs',
    join(hooksDir, `use${context.classPrefix}.ts`), context);
    
  // Generate Repository (v3.0.4 new: MVVM data access layer)
  await renderTemplate('app/ui/repositories/Repository.ts.ejs',
    join(repositoriesDir, `${context.classPrefix}Repository.ts`), context);
  await renderTemplate('app/ui/repositories/index.ts.ejs',
    join(repositoriesDir, 'index.ts'), context);
}

/**
 * Generate Server Module (v3.0.4 New)
 * 
 * Server module follows the "Ultra-thin Host Layer" principle:
 * - REST Controller (only exposes endpoints, no business logic)
 * - AutoConfiguration (Spring Boot auto-configuration)
 * - Properties (configuration property classes)
 * - OpenAPI annotations (@Tag, @Operation, @ApiResponse)
 * 
 * @param context Template context
 * @param outputDir Output directory
 */
async function generateAppServerModule(
  context: AppTemplateContext,
  outputDir: string
): Promise<void> {
  const moduleDir = join(outputDir, `${context.name}-server`);
  const srcDir = join(moduleDir, 'src', 'main', 'java', ...context.packageName.split('.'), 'server');
  const resourcesDir = join(moduleDir, 'src', 'main', 'resources');
  
  await mkdir(srcDir, { recursive: true });
  await mkdir(resourcesDir, { recursive: true });
  
  // Create subdirectory structure
  const controllerDir = join(srcDir, 'controller');
  const configDir = join(srcDir, 'config');
  
  await mkdir(controllerDir, { recursive: true });
  await mkdir(configDir, { recursive: true });
  
  // Generate pom.xml (includes springdoc-openapi dependency)
  await renderTemplate('app/server/pom.xml.ejs', join(moduleDir, 'pom.xml'), context);
  
  // Generate REST Controller (OpenAPI annotations)
  await renderTemplate('app/server/ExampleController.java.ejs',
    join(controllerDir, `${context.classPrefix}Controller.java`), context);
  
  // Generate AutoConfiguration (Spring Boot auto-configuration)
  await renderTemplate('app/server/AutoConfiguration.java.ejs',
    join(configDir, `${context.classPrefix}ServerAutoConfiguration.java`), context);
  
  // Generate Properties (configuration properties)
  await renderTemplate('app/server/Properties.java.ejs',
    join(configDir, `${context.classPrefix}ServerProperties.java`), context);
    
  // Generate META-INF/spring auto-configuration registration file
  const metaInfDir = join(resourcesDir, 'META-INF', 'spring');
  await mkdir(metaInfDir, { recursive: true });
  
  const autoConfigContent = `${context.packageName}.server.config.${context.classPrefix}ServerAutoConfiguration`;
  await writeFile(
    join(metaInfDir, 'org.springframework.boot.autoconfigure.AutoConfiguration.imports'),
    autoConfigContent,
    'utf-8'
  );
}

/**
 * Generate Shared Module (v3.0.4 New)
 * 
 * Shared module for frontend-backend type sharing:
 * - types.ts (business type definitions, corresponding to backend DTOs)
 * - events.ts (event definitions)
 * - constants.ts (constant definitions)
 * - http-client.ts (HttpCapability adapter)
 * - orval.config.ts (OpenAPI code generation config)
 * - tsup.config.ts (build config)
 * 
 * @param context Template context
 * @param outputDir Output directory
 */
async function generateAppSharedModule(
  context: AppTemplateContext,
  outputDir: string
): Promise<void> {
  const moduleDir = join(outputDir, `${context.name}-shared`);
  const srcDir = join(moduleDir, 'src');
  const generatedDir = join(srcDir, 'generated');
  
  await mkdir(srcDir, { recursive: true });
  await mkdir(generatedDir, { recursive: true });
  
  // Generate package.json
  await renderTemplate('app/shared/package.json.ejs', join(moduleDir, 'package.json'), context);
  
  // Generate tsconfig.json
  await renderTemplate('app/shared/tsconfig.json.ejs', join(moduleDir, 'tsconfig.json'), context);
  
  // Generate tsup.config.ts (build config)
  await renderTemplate('app/shared/tsup.config.ts.ejs', join(moduleDir, 'tsup.config.ts'), context);
  
  // Generate orval.config.ts (OpenAPI code generation config)
  await renderTemplate('app/shared/orval.config.ts.ejs', join(moduleDir, 'orval.config.ts'), context);
  
  // Generate http-client.ts (HttpCapability adapter)
  await renderTemplate('app/shared/http-client.ts.ejs', join(srcDir, 'http-client.ts'), context);
  
  // Generate business type definition files
  await renderTemplate('app/shared/types.ts.ejs', join(srcDir, 'types.ts'), context);
  await renderTemplate('app/shared/events.ts.ejs', join(srcDir, 'events.ts'), context);
  await renderTemplate('app/shared/constants.ts.ejs', join(srcDir, 'constants.ts'), context);
  
  // Generate entry file
  await renderTemplate('app/shared/index.ts.ejs', join(srcDir, 'index.ts'), context);
  
  // Create .gitkeep to keep generated directory
  await writeFile(join(generatedDir, '.gitkeep'), '', 'utf-8');
}

/**
 * Generate UI Mobile Module (v3.0.4 New)
 * 
 * UI Mobile module uses React Native architecture:
 * - Follows MVVM layering (pages/hooks/repositories)
 * - Uses HttpCapability for HTTP requests
 * - Shares shared module types with ui-web module
 * 
 * @param context Template context
 * @param outputDir Output directory
 */
async function generateAppUiMobileModule(
  context: AppTemplateContext,
  outputDir: string
): Promise<void> {
  const moduleDir = join(outputDir, `${context.name}-ui-mobile`);
  const srcDir = join(moduleDir, 'src');
  
  await mkdir(srcDir, { recursive: true });
  
  // Generate package.json
  await renderTemplate('app/ui-mobile/package.json.ejs', join(moduleDir, 'package.json'), context);
  
  // Generate tsconfig.json
  await renderTemplate('app/ui-mobile/tsconfig.json.ejs', join(moduleDir, 'tsconfig.json'), context);
  
  // Generate ui-manifest.yaml (Mobile UI module declaration)
  await renderTemplate('app/ui-mobile/ui-manifest.yaml.ejs', join(moduleDir, 'ui-manifest.yaml'), context);

  // Generate eslint.config.js (architecture guard rules)
  await renderTemplate('app/ui-mobile/eslint.config.js.ejs', join(moduleDir, 'eslint.config.js'), context);
  
  // Generate entry file
  await renderTemplate('app/ui-mobile/index.ts.ejs', join(srcDir, 'index.ts'), context);
  
  // Create MVVM directory structure
  const pagesDir = join(srcDir, 'pages');
  const hooksDir = join(srcDir, 'hooks');
  const repositoriesDir = join(srcDir, 'repositories');
  const componentsDir = join(srcDir, 'components');
  
  await mkdir(pagesDir, { recursive: true });
  await mkdir(hooksDir, { recursive: true });
  await mkdir(repositoriesDir, { recursive: true });
  await mkdir(componentsDir, { recursive: true });
  
  // Generate example pages (React Native)
  await renderTemplate('app/ui-mobile/src/pages/List.tsx.ejs',
    join(pagesDir, `${context.classPrefix}List.tsx`), context);
  await renderTemplate('app/ui-mobile/src/pages/Detail.tsx.ejs',
    join(pagesDir, `${context.classPrefix}Detail.tsx`), context);
  
  // Generate example Hook
  await renderTemplate('app/ui-mobile/src/hooks/useModule.ts.ejs',
    join(hooksDir, `use${context.classPrefix}.ts`), context);
  
  // Generate Repository
  await renderTemplate('app/ui-mobile/src/repositories/Repository.ts.ejs',
    join(repositoriesDir, `${context.classPrefix}Repository.ts`), context);
}

/**
 * Generate CI Configuration Files (v3.0.4 New)
 * 
 * Generate GitHub Actions workflows:
 * - architecture-guard.yml (architecture guard check)
 * 
 * @param context Template context
 * @param outputDir Output directory
 */
async function generateCIFiles(
  context: AppTemplateContext,
  outputDir: string
): Promise<void> {
  const workflowsDir = join(outputDir, '.github', 'workflows');
  
  await mkdir(workflowsDir, { recursive: true });
  
  // Generate architecture guard CI config
  await renderTemplate('common/.github/workflows/architecture-guard.yml.ejs',
    join(workflowsDir, 'architecture-guard.yml'), context);
}

/**
 * Generate App Module (Standalone Runnable)
 * 
 * App module is a standalone runnable Spring Boot application:
 * - For standalone deployment scenarios
 * - Contains Application startup class
 * - Contains application.yml configuration
 * 
 * @param context Template context
 * @param outputDir Output directory
 */
async function generateAppAppModule(
  context: AppTemplateContext,
  outputDir: string
): Promise<void> {
  const moduleDir = join(outputDir, `${context.name}-app`);
  const srcDir = join(moduleDir, 'src', 'main', 'java', ...context.packageName.split('.'), 'app');
  const resourcesDir = join(moduleDir, 'src', 'main', 'resources');
  
  await mkdir(srcDir, { recursive: true });
  await mkdir(resourcesDir, { recursive: true });
  
  // Generate pom.xml
  await renderTemplate('app/app/pom.xml.ejs', join(moduleDir, 'pom.xml'), context);
  
  // Generate Application.java
  await renderTemplate('app/app/Application.java.ejs',
    join(srcDir, `${context.classPrefix}Application.java`), context);
  
  // Generate application.yml
  await renderTemplate('app/app/application.yml.ejs',
    join(resourcesDir, 'application.yml'), context);
  
  // Generate Docker config (if needed)
  if (context.withDocker) {
    await renderTemplate('app/app/Dockerfile.ejs', join(moduleDir, 'Dockerfile'), context);
    await renderTemplate('app/app/docker-compose.yml.ejs', 
      join(moduleDir, 'docker-compose.yml'), context);
  }
}
