/**
 * Generate CSS Variables File
 */

const fs = require('fs');
const path = require('path');

// Simple color and spacing values
const colors = {
  'brand-primary': '#3B82F6',
  'brand-secondary': '#8B5CF6',
  'brand-accent': '#F59E0B',
  'semantic-success': '#10B981',
  'semantic-warning': '#F59E0B',
  'semantic-error': '#EF4444',
  'semantic-info': '#3B82F6',
  'neutral-white': '#FFFFFF',
  'neutral-black': '#000000',
  'neutral-gray-50': '#F9FAFB',
  'neutral-gray-100': '#F3F4F6',
  'neutral-gray-200': '#E5E7EB',
  'neutral-gray-300': '#D1D5DB',
  'neutral-gray-400': '#9CA3AF',
  'neutral-gray-500': '#6B7280',
  'neutral-gray-600': '#4B5563',
  'neutral-gray-700': '#374151',
  'neutral-gray-800': '#1F2937',
  'neutral-gray-900': '#111827',
};

const spacing = {
  'xs': '0.25rem',
  'sm': '0.5rem',
  'md': '1rem',
  'lg': '1.5rem',
  'xl': '2rem',
  '2xl': '3rem',
};

const fontSize = {
  'xs': '0.75rem',
  'sm': '0.875rem',
  'base': '1rem',
  'lg': '1.125rem',
  'xl': '1.25rem',
  '2xl': '1.5rem',
  '3xl': '1.875rem',
  '4xl': '2.25rem',
};

// Generate CSS
let css = `/**
 * @brix/design-tokens CSS Variables
 * Auto-generated, do not modify manually
 */

:root {
`;

// Color variables
for (const [key, value] of Object.entries(colors)) {
  css += `  --color-${key}: ${value};\n`;
}

css += '\n';

// Spacing variables
for (const [key, value] of Object.entries(spacing)) {
  css += `  --spacing-${key}: ${value};\n`;
}

css += '\n';

// Font size variables
for (const [key, value] of Object.entries(fontSize)) {
  css += `  --font-size-${key}: ${value};\n`;
}

css += '}\n';

// 确保 dist 目录存在
const distDir = path.join(__dirname, '..', 'dist');
if (!fs.existsSync(distDir)) {
  fs.mkdirSync(distDir, { recursive: true });
}

// 写入文件
fs.writeFileSync(path.join(distDir, 'tokens.css'), css);
console.log('✅ Generated dist/tokens.css');
