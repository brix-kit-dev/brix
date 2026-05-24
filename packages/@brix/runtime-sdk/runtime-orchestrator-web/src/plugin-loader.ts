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
 * @file Plugin Loader
 * @description Plugin loading utilities for different module formats
 * @module @brix-sdk/runtime-orchestrator-web/plugin-loader
 * @version 3.0.0
 * 
 * Extracted from PluginManager.ts as part of v3.2 architecture refactoring
 * to keep each file under 500 lines per code quality guidelines.
 * 
 * Supports multiple loader types:
 * - esm: ES Module dynamic import (recommended)
 * - script: Script tag loading with global variable exposure
 * - cjs: CommonJS loading (placeholder)
 * - iife: IIFE loading (placeholder)
 * 
 * �����ļ���Ҫ�㡿
 * ���������֧�ֶ���ģ���ʽ���Ƽ�ʹ�� ESM ��̬���롣
 * script ��ʽͨ��ȫ�ֱ��� __BRIX_PLUGIN__ ��¶���ʵ����
 */

import type { PluginEntry, PluginLifecycle } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Execute plugin loading
 * 
 * Loads plugin based on the specified loader type.
 * 
 * @param entry - Plugin entry configuration
 * @returns Plugin instance
 * @throws Error if loading fails or loader type is unsupported
 */
export async function executeLoad(entry: PluginEntry): Promise<PluginLifecycle> {
  return entry.loader();
}

/**
 * Load plugin via script tag
 * 
 * Loads plugin by injecting a script tag into the document head.
 * Plugin must expose its instance via window.__BRIX_PLUGIN__ global variable.
 * 
 * �����ļ���Ҫ�㡿
 * ͨ�� script ��ǩ���ز���������Ҫ��ʵ����¶�� window.__BRIX_PLUGIN__��
 * ������ɺ��Զ�����ȫ�ֱ�����������Ⱦȫ�������ռ䡣
 * 
 * @param url - Script URL
 * @returns Plugin instance
 */
export function loadScript(url: string): Promise<PluginLifecycle> {
  return new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = url;
    script.async = true;
    
    script.onload = () => {
      // Assume plugin exposes via global variable
      const globalPlugin = (window as unknown as Record<string, unknown>).__BRIX_PLUGIN__;
      
      if (globalPlugin) {
        resolve(globalPlugin as PluginLifecycle);
        // Clean up global variable
        delete (window as unknown as Record<string, unknown>).__BRIX_PLUGIN__;
      } else {
        reject(new Error(`Plugin instance not found after loading script "${url}"`));
      }
    };
    
    script.onerror = () => {
      reject(new Error(`Failed to load script "${url}"`));
    };
    
    document.head.appendChild(script);
  });
}
