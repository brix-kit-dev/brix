/**
 * @file Icons Module Exports
 * @description Exports for the icon system including NativeIcon component and SVG registry.
 * @module @brix/infra-adapter-ui-native/icons
 * @version 3.1.0
 */

// Component export
export { NativeIcon, default } from './NativeIcon';

// SVG registry exports
export {
  type SvgIconDef,
  SVG_ICON_REGISTRY,
  getIconDef,
  hasIconDef,
  getAvailableIconNames,
  navigationIcons,
  applicationIcons,
  userIcons,
  businessIcons,
  fileIcons,
  actionIcons,
  statusIcons,
} from './svg-icons';
