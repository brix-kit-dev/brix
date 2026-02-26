/**
 * @file Manifest Loader
 * @description Responsible for loading application manifests from various sources
 * @module @brix/runtime-manifest-web/ManifestLoader
 * @version 3.0.0
 * 
 * [Design Notes]
 * ManifestLoader supports multiple manifest loading methods:
 * - URL loading: Fetches from remote server
 * - File system loading: Used during local development
 * - Inline loading: Passes manifest object directly
 * - Dynamic loading: Loads plugin manifests on demand
 */

import type { AppManifest, PluginManifest } from './types/Manifest';

/**
 * Loader Configuration
 */
export interface ManifestLoaderConfig {
  /** Base path for manifest files */
  basePath?: string;
  
  /** Request timeout (milliseconds) */
  timeout?: number;
  
  /** Whether to enable caching */
  cache?: boolean;
  
  /** Cache TTL (milliseconds) */
  cacheTTL?: number;
  
  /** Custom request headers */
  headers?: Record<string, string>;
  
  /** Request credentials mode */
  credentials?: RequestCredentials;
}

/**
 * Default Configuration
 */
const DEFAULT_CONFIG: Required<ManifestLoaderConfig> = {
  basePath: '',
  timeout: 30000,
  cache: true,
  cacheTTL: 5 * 60 * 1000, // 5 minutes
  headers: {},
  credentials: 'same-origin',
};

/**
 * Cache Entry
 */
interface CacheEntry {
  data: AppManifest | PluginManifest;
  timestamp: number;
}

/**
 * Manifest Loader
 * 
 * Provides manifest file loading functionality.
 */
export class ManifestLoader {
  /** Configuration */
  private readonly config: Required<ManifestLoaderConfig>;
  
  /** Manifest cache */
  private readonly cache = new Map<string, CacheEntry>();
  
  /** Loading promises (for deduplication) */
  private readonly loading = new Map<string, Promise<AppManifest | PluginManifest>>();
  
  /**
   * Constructor
   * 
   * @param config - Loader configuration
   */
  constructor(config: ManifestLoaderConfig = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
  }
  
  /**
   * Load application manifest from URL
   * 
   * @param url - Manifest file URL
   * @returns Application manifest object
   */
  async loadAppManifest(url: string): Promise<AppManifest> {
    const fullUrl = this.resolveUrl(url);
    
    // Check cache
    if (this.config.cache) {
      const cached = this.getFromCache<AppManifest>(fullUrl);
      if (cached) {
        return cached;
      }
    }
    
    // Check if loading is in progress
    const existing = this.loading.get(fullUrl);
    if (existing) {
      return existing as Promise<AppManifest>;
    }
    
    // Execute loading
    const promise = this.fetchManifest<AppManifest>(fullUrl);
    this.loading.set(fullUrl, promise);
    
    try {
      const manifest = await promise;
      
      // Add to cache
      if (this.config.cache) {
        this.addToCache(fullUrl, manifest);
      }
      
      return manifest;
    } finally {
      this.loading.delete(fullUrl);
    }
  }
  
  /**
   * Load plugin manifest from URL
   * 
   * @param url - Manifest file URL
   * @returns Plugin manifest object
   */
  async loadPluginManifest(url: string): Promise<PluginManifest> {
    const fullUrl = this.resolveUrl(url);
    
    // Check cache
    if (this.config.cache) {
      const cached = this.getFromCache<PluginManifest>(fullUrl);
      if (cached) {
        return cached;
      }
    }
    
    // Check if loading is in progress
    const existing = this.loading.get(fullUrl);
    if (existing) {
      return existing as Promise<PluginManifest>;
    }
    
    // Execute loading
    const promise = this.fetchManifest<PluginManifest>(fullUrl);
    this.loading.set(fullUrl, promise);
    
    try {
      const manifest = await promise;
      
      // Add to cache
      if (this.config.cache) {
        this.addToCache(fullUrl, manifest);
      }
      
      return manifest;
    } finally {
      this.loading.delete(fullUrl);
    }
  }
  
  /**
   * Load application manifest from inline object
   * 
   * @param manifest - Manifest object
   * @returns Application manifest object
   */
  loadFromObject(manifest: AppManifest): AppManifest {
    return manifest;
  }
  
  /**
   * Batch load plugin manifests
   * 
   * @param urls - Manifest file URL array
   * @returns Plugin manifest array
   */
  async loadPluginManifests(urls: string[]): Promise<PluginManifest[]> {
    const promises = urls.map(url => this.loadPluginManifest(url));
    return Promise.all(promises);
  }
  
  /**
   * Clear cache
   * 
   * @param url - Optional, cache for specific URL; clears all if not provided
   */
  clearCache(url?: string): void {
    if (url) {
      const fullUrl = this.resolveUrl(url);
      this.cache.delete(fullUrl);
    } else {
      this.cache.clear();
    }
  }
  
  /**
   * Get cache statistics
   */
  getCacheStats(): { size: number; keys: string[] } {
    return {
      size: this.cache.size,
      keys: Array.from(this.cache.keys()),
    };
  }
  
  /**
   * Preload manifests
   * 
   * @param urls - URL array
   */
  async preload(urls: string[]): Promise<void> {
    await Promise.allSettled(
      urls.map(url => this.loadAppManifest(url).catch(() => {}))
    );
  }
  
  /**
   * Resolve full URL
   */
  private resolveUrl(url: string): string {
    if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('/')) {
      return url;
    }
    
    return `${this.config.basePath}/${url}`.replace(/\/+/g, '/');
  }
  
  /**
   * Get from cache
   */
  private getFromCache<T extends AppManifest | PluginManifest>(url: string): T | null {
    const entry = this.cache.get(url);
    
    if (!entry) {
      return null;
    }
    
    // Check if expired
    if (Date.now() - entry.timestamp > this.config.cacheTTL) {
      this.cache.delete(url);
      return null;
    }
    
    return entry.data as T;
  }
  
  /**
   * Add to cache
   */
  private addToCache(url: string, data: AppManifest | PluginManifest): void {
    this.cache.set(url, {
      data,
      timestamp: Date.now(),
    });
  }
  
  /**
   * Execute HTTP request
   */
  private async fetchManifest<T extends AppManifest | PluginManifest>(url: string): Promise<T> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.config.timeout);
    
    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: {
          'Accept': 'application/json',
          ...this.config.headers,
        },
        credentials: this.config.credentials,
        signal: controller.signal,
      });
      
      if (!response.ok) {
        throw new Error(
          `Failed to load manifest: ${response.status} ${response.statusText}`
        );
      }
      
      const data = await response.json();
      return data as T;
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') {
        throw new Error(`Manifest loading timeout: ${url}`);
      }
      throw error;
    } finally {
      clearTimeout(timeoutId);
    }
  }
}

/**
 * Create manifest loader instance
 * 
 * @param config - Loader configuration
 * @returns Manifest loader instance
 */
export function createManifestLoader(config?: ManifestLoaderConfig): ManifestLoader {
  return new ManifestLoader(config);
}
