/**
 * Generate CSS Variables File
 *
 * [v3.3.0 Plan A â€?Reverse Dependency]
 * Brand & layout colors are now imported from the freshly-built dist (which
 * transitively resolves `BRIX_*_THEME_TOKENS` from `@brix-sdk/runtime-sdk-api-web`).
 * This eliminates the prior hard-coded TailwindCSS-blue brand values that
 * had silently drifted from the contract layer (Constraint #9 violation).
 */

import { promises as fs } from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const distDir = path.join(__dirname, '..', 'dist');
const distEntry = path.join(distDir, 'index.mjs');

// Dynamic import of the just-built ESM bundle. `--external` for runtime-sdk-api-web
// in tsup means the import call below resolves through Node module resolution to
// the workspace-linked contract package, which holds the canonical BRIX presets.
const { brandColors, layoutColors, neutralColors, semanticColors, spacing, fontSize } =
  await import(pathToFileURL(distEntry).href);

const colorVars = {
  // Brand (sourced from BRIX_LIGHT_THEME_TOKENS via re-export)
  'brand-primary': brandColors.primary,
  'brand-primary-light': brandColors.primaryLight,
  'brand-primary-dark': brandColors.primaryDark,
  'brand-secondary': brandColors.secondary,
  'brand-secondary-light': brandColors.secondaryLight,
  'brand-secondary-dark': brandColors.secondaryDark,
  'brand-accent': brandColors.accent,

  // Semantic
  'semantic-success': semanticColors.success,
  'semantic-warning': semanticColors.warning,
  'semantic-error': semanticColors.error,
  'semantic-info': semanticColors.info,

  // Neutral
  'neutral-white': neutralColors.white,
  'neutral-black': neutralColors.black,
  'neutral-gray-50': neutralColors.gray50,
  'neutral-gray-100': neutralColors.gray100,
  'neutral-gray-200': neutralColors.gray200,
  'neutral-gray-300': neutralColors.gray300,
  'neutral-gray-400': neutralColors.gray400,
  'neutral-gray-500': neutralColors.gray500,
  'neutral-gray-600': neutralColors.gray600,
  'neutral-gray-700': neutralColors.gray700,
  'neutral-gray-800': neutralColors.gray800,
  'neutral-gray-900': neutralColors.gray900,

  // Layout (sourced from BRIX_LIGHT_THEME_TOKENS via re-export)
  'layout-sidebar-bg': layoutColors.sidebarBackground,
  'layout-sidebar-active-bg': layoutColors.sidebarActiveBackground,
  'layout-header-bg': layoutColors.headerBackground,
};

let css = `/**
 * @brix-sdk/platform-design-tokens CSS Variables
 * Auto-generated from BRIX_*_THEME_TOKENS (do not modify manually).
 */

:root {
`;

for (const [key, value] of Object.entries(colorVars)) {
  css += `  --color-${key}: ${value};\n`;
}

css += '\n';

const SPACING_ALIASES = ['xs', 'sm', 'md', 'lg', 'xl', '2xl', '3xl'];
for (const key of SPACING_ALIASES) {
  if (spacing[key] !== undefined) {
    css += `  --spacing-${key}: ${spacing[key]};\n`;
  }
}

css += '\n';

const FONT_SIZE_ALIASES = ['xs', 'sm', 'base', 'lg', 'xl', '2xl', '3xl', '4xl'];
for (const key of FONT_SIZE_ALIASES) {
  if (fontSize[key] !== undefined) {
    css += `  --font-size-${key}: ${fontSize[key]};\n`;
  }
}

css += '}\n';

await fs.mkdir(distDir, { recursive: true });
await fs.writeFile(path.join(distDir, 'tokens.css'), css);
console.log('âœ?Generated dist/tokens.css (sourced from BRIX_*_THEME_TOKENS)');
