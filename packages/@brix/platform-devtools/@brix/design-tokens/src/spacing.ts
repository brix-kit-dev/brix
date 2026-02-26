/**
 * @file spacing.ts
 * @description Spacing Tokens
 */

/**
 * Base Spacing
 */
export const spacing = {
  px: '1px',
  0: '0',
  0.5: '0.125rem',  // 2px
  1: '0.25rem',     // 4px
  1.5: '0.375rem',  // 6px
  2: '0.5rem',      // 8px
  2.5: '0.625rem',  // 10px
  3: '0.75rem',     // 12px
  3.5: '0.875rem',  // 14px
  4: '1rem',        // 16px
  5: '1.25rem',     // 20px
  6: '1.5rem',      // 24px
  7: '1.75rem',     // 28px
  8: '2rem',        // 32px
  9: '2.25rem',     // 36px
  10: '2.5rem',     // 40px
  11: '2.75rem',    // 44px
  12: '3rem',       // 48px
  14: '3.5rem',     // 56px
  16: '4rem',       // 64px
  20: '5rem',       // 80px
  24: '6rem',       // 96px
  28: '7rem',       // 112px
  32: '8rem',       // 128px
  
  // Semantic Aliases
  xs: '0.25rem',    // 4px
  sm: '0.5rem',     // 8px
  md: '1rem',       // 16px
  lg: '1.5rem',     // 24px
  xl: '2rem',       // 32px
  '2xl': '3rem',    // 48px
  '3xl': '4rem',    // 64px
};

/**
 * Component Spacing
 */
export const componentSpacing = {
  button: {
    paddingX: spacing[4],
    paddingY: spacing[2],
  },
  input: {
    paddingX: spacing[3],
    paddingY: spacing[2],
  },
  card: {
    padding: spacing[4],
  },
  modal: {
    padding: spacing[6],
  },
};

/**
 * Layout Spacing
 */
export const layoutSpacing = {
  page: {
    paddingX: spacing[4],
    paddingY: spacing[6],
  },
  section: {
    marginY: spacing[8],
  },
  container: {
    maxWidth: '1280px',
    paddingX: spacing[4],
  },
};

/**
 * Icon Sizes
 */
export const iconSizes = {
  xs: '0.75rem',    // 12px
  sm: '1rem',       // 16px
  md: '1.25rem',    // 20px
  lg: '1.5rem',     // 24px
  xl: '2rem',       // 32px
  '2xl': '2.5rem',  // 40px
};

/**
 * Avatar Sizes
 */
export const avatarSizes = {
  xs: '1.5rem',     // 24px
  sm: '2rem',       // 32px
  md: '2.5rem',     // 40px
  lg: '3rem',       // 48px
  xl: '4rem',       // 64px
  '2xl': '6rem',    // 96px
};

/**
 * Border Radius
 */
export const borderRadius = {
  none: '0',
  sm: '0.125rem',   // 2px
  DEFAULT: '0.25rem', // 4px
  md: '0.375rem',   // 6px
  lg: '0.5rem',     // 8px
  xl: '0.75rem',    // 12px
  '2xl': '1rem',    // 16px
  '3xl': '1.5rem',  // 24px
  full: '9999px',
};

/**
 * Border Width
 */
export const borderWidth = {
  0: '0',
  DEFAULT: '1px',
  2: '2px',
  4: '4px',
  8: '8px',
};

/**
 * Shadows
 */
export const shadows = {
  none: 'none',
  sm: '0 1px 2px 0 rgb(0 0 0 / 0.05)',
  DEFAULT: '0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1)',
  md: '0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)',
  lg: '0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1)',
  xl: '0 20px 25px -5px rgb(0 0 0 / 0.1), 0 8px 10px -6px rgb(0 0 0 / 0.1)',
  '2xl': '0 25px 50px -12px rgb(0 0 0 / 0.25)',
  inner: 'inset 0 2px 4px 0 rgb(0 0 0 / 0.05)',
};

/**
 * Get Spacing Value
 */
export function getSpacing(size: keyof typeof spacing): string {
  return spacing[size];
}

/**
 * Convert to px Value
 */
export function px(rem: string): string {
  const num = parseFloat(rem);
  return `${num * 16}px`;
}

/**
 * Convert to rem Value
 */
export function rem(px: number): string {
  return `${px / 16}rem`;
}
