/**
 * @file platform-i18n-web Module Entry
 * @description Web internationalization capability implementation module - implements I18nCapability interface
 * @module @brix/platform-i18n-web
 * @version 3.0.0
 * 
 * Module Description:
 * platform-i18n-web is the implementation module of I18nCapability interface.
 * Provides multilingual translation, date/number formatting, and other features.
 * 
 * Architectural Position:
 * ```text
 * 
 *  Capability Contract Layer (runtime-sdk-api-web)                        
 *   I18nCapability interface definition                                
 * 
 *  Capability Implementation Layer (platform-commons)                     
 *   platform-i18n-web (this module)                                   
 *        I18nCapabilityImpl (interface implementation)                 
 *        I18nStore (state storage)                                     
 *        hooks/ (useI18n, useTranslation)                              
 * 
 * ```
 * 
 * Architectural Constraints:
 * - Translation keys use namespace format: moduleName:translationKey
 * - Language packs should be registered at module load, loaded dynamically at runtime
 * - Language switch notifies all modules via events
 * - Hardcoding text in components is prohibited
 */

// ============================================================================
// Capability Implementation
// ============================================================================

export { I18nCapabilityImpl, type I18nCapabilityConfig } from './I18nCapabilityImpl';
export { I18nStore, type I18nStoreConfig, type LocaleChangeListener } from './I18nStore';

// ============================================================================
// Hooks
// ============================================================================

export { useI18n, type UseI18nResult } from './hooks/useI18n';
export { useTranslation, type UseTranslationResult } from './hooks/useTranslation';