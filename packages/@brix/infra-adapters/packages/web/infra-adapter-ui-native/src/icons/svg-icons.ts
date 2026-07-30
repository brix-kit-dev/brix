/**
 * @file Native SVG Icon Definitions
 * @description SVG path definitions for inline icons used in Native UI Adapter.
 *              All icons are from MUI (Material Design Icons) or similar open-source icon sets.
 * @module @brix-sdk/infra-adapter-ui-native/icons/svg-icons
 * @version 3.1.0
 *
 * [Design Principles]
 * - All icons are inline SVG paths for zero network dependency
 * - Icons use currentColor for easy theming via CSS color property
 * - Standard 24x24 viewBox for consistent sizing
 * - Icons are organized by category for easy maintenance
 *
 * [Icon Sources]
 * - Material Design Icons (Apache 2.0 License)
 * - Heroicons (MIT License)
 */

// ============================================================================
// Icon Path Type Definition
// ============================================================================

/**
 * SVG Icon Path Definition
 *
 * <p>Contains the path data and optional viewBox for rendering SVG icons.</p>
 */
export interface SvgIconDef {
  /**
   * SVG Path Data
   *
   * <p>The 'd' attribute value for the SVG path element.
   * Can be a single path or multiple paths joined with space.</p>
   */
  path: string;

  /**
   * ViewBox Dimensions
   *
   * <p>Optional custom viewBox. Defaults to "0 0 24 24" if not specified.</p>
   */
  viewBox?: string;

  /**
   * Fill Rule
   *
   * <p>Optional fillRule for complex paths. Defaults to "evenodd".</p>
   */
  fillRule?: 'nonzero' | 'evenodd';
}

// ============================================================================
// Navigation & Menu Icons
// ============================================================================

/**
 * Navigation Icons - Used in sidebars, headers, and navigation menus
 */
export const navigationIcons: Record<string, SvgIconDef> = {
  /**
   * Menu (hamburger) Icon
   * Used for sidebar toggle buttons
   */
  menu: {
    path: 'M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z',
  },

  /**
   * Close Icon
   * Used for closing menus, modals, dialogs
   */
  close: {
    path: 'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
  },

  /**
   * Chevron Right Icon
   * Used for menu expansion indicators
   */
  chevronRight: {
    path: 'M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z',
  },

  /**
   * Chevron Left Icon
   * Used for back navigation
   */
  chevronLeft: {
    path: 'M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z',
  },

  /**
   * Chevron Down Icon
   * Used for dropdown indicators
   */
  chevronDown: {
    path: 'M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z',
  },

  /**
   * Chevron Up Icon
   * Used for collapse indicators
   */
  chevronUp: {
    path: 'M7.41 15.41L12 10.83l4.59 4.58L18 14l-6-6-6 6 1.41 1.41z',
  },

  /**
   * Arrow Back Icon
   * Used for navigation history
   */
  arrowBack: {
    path: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
  },

  /**
   * Arrow Forward Icon
   */
  arrowForward: {
    path: 'M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z',
  },
};

// ============================================================================
// Application Icons
// ============================================================================

/**
 * Application Icons - Common application-level icons
 */
export const applicationIcons: Record<string, SvgIconDef> = {
  /**
   * Dashboard Icon
   * Used for dashboard/overview pages
   */
  dashboard: {
    path: 'M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z',
  },

  /**
   * Home Icon
   * Used for home/landing pages
   */
  home: {
    path: 'M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z',
  },

  /**
   * Settings Icon
   * Used for configuration pages
   */
  settings: {
    path: 'M19.14 12.94c.04-.31.06-.63.06-.94 0-.31-.02-.63-.06-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.04.31-.06.63-.06.94s.02.63.06.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
  },

  /**
   * Search Icon
   */
  search: {
    path: 'M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
  },

  /**
   * Notifications Icon
   */
  notifications: {
    path: 'M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z',
  },

  /**
   * Help Icon
   */
  help: {
    path: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17h-2v-2h2v2zm2.07-7.75l-.9.92C13.45 12.9 13 13.5 13 15h-2v-.5c0-1.1.45-2.1 1.17-2.83l1.24-1.26c.37-.36.59-.86.59-1.41 0-1.1-.9-2-2-2s-2 .9-2 2H8c0-2.21 1.79-4 4-4s4 1.79 4 4c0 .88-.36 1.68-.93 2.25z',
  },
};

// ============================================================================
// User & Account Icons
// ============================================================================

/**
 * User Icons - User and account related icons
 */
export const userIcons: Record<string, SvgIconDef> = {
  /**
   * Person (Single User) Icon
   */
  user: {
    path: 'M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z',
  },

  /**
   * People (Multiple Users) Icon
   */
  users: {
    path: 'M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z',
  },

  /**
   * Account Circle Icon
   */
  account: {
    path: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z',
  },

  /**
   * Identity/Key Icon
   */
  identity: {
    path: 'M12.65 10C11.83 7.67 9.61 6 7 6c-3.31 0-6 2.69-6 6s2.69 6 6 6c2.61 0 4.83-1.67 5.65-4H17v4h4v-4h2v-4H12.65zM7 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z',
  },

  /**
   * Profile Icon
   */
  profile: {
    path: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zM7.07 18.28c.43-.9 3.05-1.78 4.93-1.78s4.51.88 4.93 1.78C15.57 19.36 13.86 20 12 20s-3.57-.64-4.93-1.72zm11.29-1.45c-1.43-1.74-4.9-2.33-6.36-2.33s-4.93.59-6.36 2.33C4.62 15.49 4 13.82 4 12c0-4.41 3.59-8 8-8s8 3.59 8 8c0 1.82-.62 3.49-1.64 4.83zM12 6c-1.94 0-3.5 1.56-3.5 3.5S10.06 13 12 13s3.5-1.56 3.5-3.5S13.94 6 12 6zm0 5c-.83 0-1.5-.67-1.5-1.5S11.17 8 12 8s1.5.67 1.5 1.5S12.83 11 12 11z',
  },

  /**
   * Logout Icon
   */
  logout: {
    path: 'M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z',
  },

  /**
   * Login Icon
   */
  login: {
    path: 'M11 7L9.6 8.4l2.6 2.6H2v2h10.2l-2.6 2.6L11 17l5-5-5-5zm9 12h-8v2h8c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2h-8v2h8v14z',
  },
};

// ============================================================================
// Business Icons
// ============================================================================

/**
 * Business Icons - Business domain related icons
 */
export const businessIcons: Record<string, SvgIconDef> = {
  /**
   * Product/Package Icon
   */
  product: {
    path: 'M20 3H4c-1.11 0-2 .89-2 2v14c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V5c0-1.11-.89-2-2-2zm-8 14H8c-.55 0-1-.45-1-1s.45-1 1-1h4c.55 0 1 .45 1 1s-.45 1-1 1zm4-4H8c-.55 0-1-.45-1-1s.45-1 1-1h8c.55 0 1 .45 1 1s-.45 1-1 1zm0-4H8c-.55 0-1-.45-1-1s.45-1 1-1h8c.55 0 1 .45 1 1s-.45 1-1 1z',
  },

  /**
   * Products/Inventory Icon
   */
  products: {
    path: 'M20 2H4c-1 0-2 .9-2 2v3.01c0 .72.43 1.34 1 1.69V20c0 1.1 1.1 2 2 2h14c.9 0 2-.9 2-2V8.7c.57-.35 1-.97 1-1.69V4c0-1.1-1-2-2-2zm-5 12H9v-2h6v2zm5-7H4V4l16-.02V7z',
  },

  /**
   * Partner/Handshake Icon
   */
  partner: {
    path: 'M12.22 19.85c-.18.18-.5.21-.71 0-.18-.18-.21-.5 0-.71l3.54-3.54c.18-.18.5-.21.71 0 .18.18.21.5 0 .71l-3.54 3.54zm9.65-6.03l-3.54 3.54c-.78.78-2.05.78-2.83 0l-4.24-4.24c-.78-.78-.78-2.05 0-2.83l3.54-3.54c.78-.78 2.05-.78 2.83 0l4.24 4.24c.79.78.79 2.05 0 2.83zm-4.24-4.24l-3.54 3.54 2.83 2.83 3.54-3.54-2.83-2.83zM6.36 18.64c-.78-.78-.78-2.05 0-2.83l3.54-3.54c.78-.78 2.05-.78 2.83 0l.28.28 1.41-1.41-.28-.28c-1.56-1.56-4.09-1.56-5.66 0l-3.54 3.54c-1.56 1.56-1.56 4.09 0 5.66 1.56 1.56 4.09 1.56 5.66 0l.28-.28-1.41-1.41-.28.28c-.78.77-2.05.77-2.83-.01zm-.71-12.73l4.24 4.24c.78.78.78 2.05 0 2.83l-.71.71-1.41-1.41.71-.71-2.83-2.83-.71.71-1.41-1.41.71-.71c.78-.78 2.05-.78 2.83 0l.58.58z',
  },

  /**
   * Partners Icon
   */
  partners: {
    path: 'M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z',
  },

  /**
   * Message/Chat Icon
   */
  message: {
    path: 'M20 2H4c-1.1 0-1.99.9-1.99 2L2 22l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM9 11H7V9h2v2zm4 0h-2V9h2v2zm4 0h-2V9h2v2z',
  },

  /**
   * Messenger Icon
   */
  messenger: {
    path: 'M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H5.17L4 17.17V4h16v12z',
  },

  /**
   * Booking/Calendar Icon
   */
  booking: {
    path: 'M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11zM9 10H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2zm-8 4H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2z',
  },

  /**
   * Calendar Icon
   */
  calendar: {
    path: 'M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11zM9 10H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2z',
  },

  /**
   * Contract/Document Icon
   */
  contract: {
    path: 'M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z',
  },

  /**
   * Contracts Icon
   */
  contracts: {
    path: 'M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-1 9H9V9h10v2zm-4 4H9v-2h6v2zm4-8H9V5h10v2z',
  },
};

// ============================================================================
// File & Content Icons
// ============================================================================

/**
 * File Icons - File and document related icons
 */
export const fileIcons: Record<string, SvgIconDef> = {
  /**
   * Single File Icon
   */
  file: {
    path: 'M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11z',
  },

  /**
   * Folder Icon
   */
  files: {
    path: 'M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z',
  },

  /**
   * Document Icon
   */
  document: {
    path: 'M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z',
  },

  /**
   * Carousel/Image Collection Icon
   */
  carousel: {
    path: 'M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z',
  },
};

// ============================================================================
// Action Icons
// ============================================================================

/**
 * Action Icons - Common action buttons
 */
export const actionIcons: Record<string, SvgIconDef> = {
  /**
   * Add/Plus Icon
   */
  add: {
    path: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  },

  /**
   * Edit/Pencil Icon
   */
  edit: {
    path: 'M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z',
  },

  /**
   * Delete/Trash Icon
   */
  delete: {
    path: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z',
  },

  /**
   * Save Icon
   */
  save: {
    path: 'M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z',
  },

  /**
   * Cancel Icon
   */
  cancel: {
    path: 'M12 2C6.47 2 2 6.47 2 12s4.47 10 10 10 10-4.47 10-10S17.53 2 12 2zm5 13.59L15.59 17 12 13.41 8.41 17 7 15.59 10.59 12 7 8.41 8.41 7 12 10.59 15.59 7 17 8.41 13.41 12 17 15.59z',
  },

  /**
   * Check/Confirm Icon
   */
  check: {
    path: 'M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
  },

  /**
   * Refresh Icon
   */
  refresh: {
    path: 'M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z',
  },

  /**
   * Download Icon
   */
  download: {
    path: 'M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z',
  },

  /**
   * Upload Icon
   */
  upload: {
    path: 'M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z',
  },

  /**
   * Copy Icon
   */
  copy: {
    path: 'M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z',
  },

  /**
   * More Vertical (kebab) Icon
   */
  moreVert: {
    path: 'M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z',
  },

  /**
   * More Horizontal (meatballs) Icon
   */
  moreHoriz: {
    path: 'M6 10c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm12 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm-6 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z',
  },

  /**
   * Filter Icon
   */
  filter: {
    path: 'M10 18h4v-2h-4v2zM3 6v2h18V6H3zm3 7h12v-2H6v2z',
  },

  /**
   * Sort Icon
   */
  sort: {
    path: 'M3 18h6v-2H3v2zM3 6v2h18V6H3zm0 7h12v-2H3v2z',
  },

  /**
   * Visibility/Eye Icon
   */
  visibility: {
    path: 'M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zm0 12.5c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z',
  },

  /**
   * Visibility Off/Eye Slash Icon
   */
  visibility_off: {
    path: 'M12 6.5c3.79 0 7.17 2.13 8.82 5.5-.7 1.43-1.79 2.61-3.11 3.45L19.15 16.9C20.75 15.8 22.07 14.12 23 12c-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l1.68 1.68c.73-.25 1.5-.38 2.3-.38zM2.71 3.16 1.39 4.48l2.54 2.54C2.7 8.24 1.69 9.92 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.32 4.38-.9l3.14 3.14 1.32-1.32L2.71 3.16zm6.53 6.53 1.4 1.4c-.08.29-.14.59-.14.91 0 .83.67 1.5 1.5 1.5.32 0 .62-.06.91-.14l1.4 1.4c-.68.46-1.47.74-2.31.74-1.93 0-3.5-1.57-3.5-3.5 0-.84.28-1.63.74-2.31z',
  },
};

// ============================================================================
// Status Icons
// ============================================================================

/**
 * Status Icons - Status and feedback indicators
 */
export const statusIcons: Record<string, SvgIconDef> = {
  /**
   * Success/Check Circle Icon
   */
  success: {
    path: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z',
  },

  /**
   * Warning Icon
   */
  warning: {
    path: 'M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z',
  },

  /**
   * Error Icon
   */
  error: {
    path: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z',
  },

  /**
   * Info Icon
   */
  info: {
    path: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z',
  },

  /**
   * Pending/Hourglass Icon
   */
  pending: {
    path: 'M6 2v6h.01L6 8.01 10 12l-4 4 .01.01H6V22h12v-5.99h-.01L18 16l-4-4 4-3.99-.01-.01H18V2H6zm10 14.5V20H8v-3.5l4-4 4 4zm-4-5l-4-4V4h8v3.5l-4 4z',
  },
};

// ============================================================================
// Full Icon Registry
// ============================================================================

/**
 * Complete Icon Registry
 *
 * <p>Merged registry of all icon categories for unified lookup.</p>
 */
export const SVG_ICON_REGISTRY: Record<string, SvgIconDef> = {
  ...navigationIcons,
  ...applicationIcons,
  ...userIcons,
  ...businessIcons,
  ...fileIcons,
  ...actionIcons,
  ...statusIcons,
  
  // Aliases for compatibility with existing menuIcons
  config: applicationIcons.settings as SvgIconDef,
  default: fileIcons.document as SvgIconDef,
};

/**
 * Get Icon Definition by Name
 *
 * <p>Looks up an icon definition in the registry by name.
 * Returns the default icon if not found.</p>
 *
 * @param name - Icon name (case insensitive)
 * @returns SvgIconDef for the icon
 *
 * @example
 * ```typescript
 * const dashboardIcon = getIconDef('dashboard');
 * console.log(dashboardIcon.path); // SVG path data
 * ```
 */
export function getIconDef(name: string): SvgIconDef {
  const normalizedName = name.toLowerCase();
  return SVG_ICON_REGISTRY[normalizedName] ?? SVG_ICON_REGISTRY['default'] as SvgIconDef;
}

/**
 * Check if Icon Exists
 *
 * @param name - Icon name (case insensitive)
 * @returns true if icon exists in registry
 */
export function hasIconDef(name: string): boolean {
  const normalizedName = name.toLowerCase();
  return normalizedName in SVG_ICON_REGISTRY;
}

/**
 * Get All Available Icon Names
 *
 * @returns Array of all registered icon names
 */
export function getAvailableIconNames(): string[] {
  return Object.keys(SVG_ICON_REGISTRY).filter(key => key !== 'default');
}
