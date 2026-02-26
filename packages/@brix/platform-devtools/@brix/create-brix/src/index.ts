/**
 * @file index.ts
 * @description Package Entry Point
 * @module @brix/create-brix
 * @version 3.0.4
 * 
 * v3.0.4 Changes:
 * - Added generateApp function export for creating v3.0 business application modules
 */

export * from './types.js';
export { generatePlugin, generateService, generateApp } from './generator.js';
