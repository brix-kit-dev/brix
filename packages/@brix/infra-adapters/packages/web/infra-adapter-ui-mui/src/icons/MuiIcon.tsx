/**
 * @file MUI Icon Component
 * @description Material UI implementation of IconProps from UIAdapter contract.
 *              Name-based icon lookup using @mui/icons-material library.
 * @module @brix/infra-adapter-ui-mui/icons/MuiIcon
 * @version 3.1.0
 *
 * [Design Principles]
 * - Name-based icon resolution from @mui/icons-material
 * - Dynamic import for tree-shaking optimization
 * - Fallback icon for unknown names
 * - Configurable size and color
 *
 * [Architectural Position - v3.0.4 Blueprint]
 * This is an atomic component in the infra-adapters layer.
 * All other components (Button, Menu) use this for icon display.
 *
 * [Icon Naming Convention]
 * Icon names use lowercase with underscores (e.g., 'dashboard', 'settings', 'arrow_back').
 * The component converts these to PascalCase for MUI icon lookup (e.g., Dashboard, Settings, ArrowBack).
 */

import React, { useMemo } from 'react';
import type { FC, CSSProperties, ComponentType } from 'react';
import type { IconProps, ComponentSize } from '@brix/runtime-sdk-api-web';
import type { SvgIconProps } from '@mui/material/SvgIcon';

// ============================================================================
// Icon Imports (Static for Common Icons)
// ============================================================================

// Import commonly used icons directly for instant rendering
// Less common icons will be dynamically loaded

import DashboardIcon from '@mui/icons-material/Dashboard';
import SettingsIcon from '@mui/icons-material/Settings';
import PersonIcon from '@mui/icons-material/Person';
import MenuIcon from '@mui/icons-material/Menu';
import CloseIcon from '@mui/icons-material/Close';
import SearchIcon from '@mui/icons-material/Search';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import SaveIcon from '@mui/icons-material/Save';
import CancelIcon from '@mui/icons-material/Cancel';
import CheckIcon from '@mui/icons-material/Check';
import ErrorIcon from '@mui/icons-material/Error';
import WarningIcon from '@mui/icons-material/Warning';
import InfoIcon from '@mui/icons-material/Info';
import HelpIcon from '@mui/icons-material/Help';
import HomeIcon from '@mui/icons-material/Home';
import LogoutIcon from '@mui/icons-material/Logout';
import LoginIcon from '@mui/icons-material/Login';
import NotificationsIcon from '@mui/icons-material/Notifications';
import EmailIcon from '@mui/icons-material/Email';
import PhoneIcon from '@mui/icons-material/Phone';
import LocationOnIcon from '@mui/icons-material/LocationOn';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import AttachFileIcon from '@mui/icons-material/AttachFile';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import CloudDownloadIcon from '@mui/icons-material/CloudDownload';
import FolderIcon from '@mui/icons-material/Folder';
import FileOpenIcon from '@mui/icons-material/FileOpen';
import DescriptionIcon from '@mui/icons-material/Description';
import ImageIcon from '@mui/icons-material/Image';
import VideocamIcon from '@mui/icons-material/Videocam';
import MicIcon from '@mui/icons-material/Mic';
import PrintIcon from '@mui/icons-material/Print';
import ShareIcon from '@mui/icons-material/Share';
import LinkIcon from '@mui/icons-material/Link';
import CopyAllIcon from '@mui/icons-material/CopyAll';
import ContentCutIcon from '@mui/icons-material/ContentCut';
import ContentPasteIcon from '@mui/icons-material/ContentPaste';
import UndoIcon from '@mui/icons-material/Undo';
import RedoIcon from '@mui/icons-material/Redo';
import RefreshIcon from '@mui/icons-material/Refresh';
import SyncIcon from '@mui/icons-material/Sync';
import FilterListIcon from '@mui/icons-material/FilterList';
import SortIcon from '@mui/icons-material/Sort';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import LockIcon from '@mui/icons-material/Lock';
import LockOpenIcon from '@mui/icons-material/LockOpen';
import SecurityIcon from '@mui/icons-material/Security';
import VpnKeyIcon from '@mui/icons-material/VpnKey';
import GroupIcon from '@mui/icons-material/Group';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import PersonRemoveIcon from '@mui/icons-material/PersonRemove';
import BusinessIcon from '@mui/icons-material/Business';
import WorkIcon from '@mui/icons-material/Work';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import PaymentIcon from '@mui/icons-material/Payment';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import LocalShippingIcon from '@mui/icons-material/LocalShipping';
import InventoryIcon from '@mui/icons-material/Inventory';
import ReceiptIcon from '@mui/icons-material/Receipt';
import AssessmentIcon from '@mui/icons-material/Assessment';
import BarChartIcon from '@mui/icons-material/BarChart';
import PieChartIcon from '@mui/icons-material/PieChart';
import TimelineIcon from '@mui/icons-material/Timeline';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import FavoriteIcon from '@mui/icons-material/Favorite';
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';
import ThumbUpIcon from '@mui/icons-material/ThumbUp';
import ThumbDownIcon from '@mui/icons-material/ThumbDown';
import ChatIcon from '@mui/icons-material/Chat';
import ForumIcon from '@mui/icons-material/Forum';
import SupportAgentIcon from '@mui/icons-material/SupportAgent';
import BuildIcon from '@mui/icons-material/Build';
import CodeIcon from '@mui/icons-material/Code';
import BugReportIcon from '@mui/icons-material/BugReport';
import MemoryIcon from '@mui/icons-material/Memory';
import StorageIcon from '@mui/icons-material/Storage';
import CloudIcon from '@mui/icons-material/Cloud';
import WifiIcon from '@mui/icons-material/Wifi';
import BluetoothIcon from '@mui/icons-material/Bluetooth';
import BatteryFullIcon from '@mui/icons-material/BatteryFull';
import FlashOnIcon from '@mui/icons-material/FlashOn';
import PowerSettingsNewIcon from '@mui/icons-material/PowerSettingsNew';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import MoreHorizIcon from '@mui/icons-material/MoreHoriz';
import AppsIcon from '@mui/icons-material/Apps';
import ViewListIcon from '@mui/icons-material/ViewList';
import ViewModuleIcon from '@mui/icons-material/ViewModule';
import GridOnIcon from '@mui/icons-material/GridOn';
import TableChartIcon from '@mui/icons-material/TableChart';
import AccountCircleIcon from '@mui/icons-material/AccountCircle';
import VerifiedIcon from '@mui/icons-material/Verified';
import NewReleasesIcon from '@mui/icons-material/NewReleases';
import AnnouncementIcon from '@mui/icons-material/Announcement';
import CampaignIcon from '@mui/icons-material/Campaign';
import EventIcon from '@mui/icons-material/Event';
import TaskAltIcon from '@mui/icons-material/TaskAlt';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked';
import RadioButtonCheckedIcon from '@mui/icons-material/RadioButtonChecked';
import CheckBoxIcon from '@mui/icons-material/CheckBox';
import CheckBoxOutlineBlankIcon from '@mui/icons-material/CheckBoxOutlineBlank';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import QuestionMarkIcon from '@mui/icons-material/QuestionMark';

// ============================================================================
// Icon Type Definition
// ============================================================================

/**
 * MUI Icon Component Type
 *
 * <p>Represents the type signature of MUI icon components.</p>
 */
type MuiIconComponent = ComponentType<SvgIconProps>;

// ============================================================================
// Icon Registry
// ============================================================================

/**
 * Static Icon Registry
 *
 * <p>Maps icon names to their MUI icon components.
 * Names use lowercase with underscores for consistent API.</p>
 */
const ICON_REGISTRY: Record<string, MuiIconComponent> = {
  // Navigation & Menu
  dashboard: DashboardIcon,
  home: HomeIcon,
  menu: MenuIcon,
  apps: AppsIcon,
  settings: SettingsIcon,
  
  // User & Auth
  person: PersonIcon,
  account_circle: AccountCircleIcon,
  group: GroupIcon,
  person_add: PersonAddIcon,
  person_remove: PersonRemoveIcon,
  login: LoginIcon,
  logout: LogoutIcon,
  lock: LockIcon,
  lock_open: LockOpenIcon,
  security: SecurityIcon,
  vpn_key: VpnKeyIcon,
  verified: VerifiedIcon,
  
  // Actions
  add: AddIcon,
  edit: EditIcon,
  delete: DeleteIcon,
  save: SaveIcon,
  cancel: CancelIcon,
  close: CloseIcon,
  check: CheckIcon,
  search: SearchIcon,
  refresh: RefreshIcon,
  sync: SyncIcon,
  undo: UndoIcon,
  redo: RedoIcon,
  filter_list: FilterListIcon,
  sort: SortIcon,
  share: ShareIcon,
  link: LinkIcon,
  copy: CopyAllIcon,
  cut: ContentCutIcon,
  paste: ContentPasteIcon,
  print: PrintIcon,
  
  // Status & Feedback
  error: ErrorIcon,
  warning: WarningIcon,
  info: InfoIcon,
  help: HelpIcon,
  help_outline: HelpOutlineIcon,
  question_mark: QuestionMarkIcon,
  check_circle: CheckCircleIcon,
  task_alt: TaskAltIcon,
  new_releases: NewReleasesIcon,
  announcement: AnnouncementIcon,
  campaign: CampaignIcon,
  
  // Communication
  notifications: NotificationsIcon,
  email: EmailIcon,
  phone: PhoneIcon,
  chat: ChatIcon,
  forum: ForumIcon,
  support_agent: SupportAgentIcon,
  
  // Content & Files
  folder: FolderIcon,
  file_open: FileOpenIcon,
  description: DescriptionIcon,
  attach_file: AttachFileIcon,
  image: ImageIcon,
  videocam: VideocamIcon,
  mic: MicIcon,
  
  // Cloud & Storage
  cloud: CloudIcon,
  cloud_upload: CloudUploadIcon,
  cloud_download: CloudDownloadIcon,
  storage: StorageIcon,
  memory: MemoryIcon,
  
  // Location & Time
  location_on: LocationOnIcon,
  calendar_today: CalendarTodayIcon,
  access_time: AccessTimeIcon,
  event: EventIcon,
  
  // Business
  business: BusinessIcon,
  work: WorkIcon,
  account_balance: AccountBalanceIcon,
  payment: PaymentIcon,
  shopping_cart: ShoppingCartIcon,
  local_shipping: LocalShippingIcon,
  inventory: InventoryIcon,
  receipt: ReceiptIcon,
  
  // Charts & Data
  assessment: AssessmentIcon,
  bar_chart: BarChartIcon,
  pie_chart: PieChartIcon,
  timeline: TimelineIcon,
  trending_up: TrendingUpIcon,
  trending_down: TrendingDownIcon,
  table_chart: TableChartIcon,
  
  // Ratings & Social
  star: StarIcon,
  star_border: StarBorderIcon,
  favorite: FavoriteIcon,
  favorite_border: FavoriteBorderIcon,
  thumb_up: ThumbUpIcon,
  thumb_down: ThumbDownIcon,
  
  // Development
  build: BuildIcon,
  code: CodeIcon,
  bug_report: BugReportIcon,
  
  // Hardware & System
  wifi: WifiIcon,
  bluetooth: BluetoothIcon,
  battery_full: BatteryFullIcon,
  flash_on: FlashOnIcon,
  power_settings_new: PowerSettingsNewIcon,
  restart_alt: RestartAltIcon,
  
  // Arrows & Navigation
  arrow_back: ArrowBackIcon,
  arrow_forward: ArrowForwardIcon,
  arrow_upward: ArrowUpwardIcon,
  arrow_downward: ArrowDownwardIcon,
  expand_more: ExpandMoreIcon,
  expand_less: ExpandLessIcon,
  chevron_left: ChevronLeftIcon,
  chevron_right: ChevronRightIcon,
  
  // Menus & Views
  more_vert: MoreVertIcon,
  more_horiz: MoreHorizIcon,
  view_list: ViewListIcon,
  view_module: ViewModuleIcon,
  grid_on: GridOnIcon,
  
  // Visibility
  visibility: VisibilityIcon,
  visibility_off: VisibilityOffIcon,
  
  // Form Controls
  checkbox: CheckBoxIcon,
  checkbox_blank: CheckBoxOutlineBlankIcon,
  radio_checked: RadioButtonCheckedIcon,
  radio_unchecked: RadioButtonUncheckedIcon,
};

// ============================================================================
// Size Mappings
// ============================================================================

/**
 * Maps ComponentSize to MUI icon fontSize
 */
const SIZE_MAP: Record<ComponentSize, 'small' | 'medium' | 'large'> = {
  small: 'small',
  medium: 'medium',
  large: 'large',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Icon Component
 *
 * <p>Material UI implementation of IconProps from UIAdapter contract.
 * Provides name-based icon lookup from @mui/icons-material library.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>100+ pre-registered common icons</li>
 *   <li>Consistent naming with lowercase and underscores</li>
 *   <li>Configurable size (preset or custom pixel)</li>
 *   <li>Custom color support</li>
 *   <li>Fallback to HelpOutline for unknown names</li>
 * </ul>
 *
 * <h3>Naming Convention:</h3>
 * <p>Use lowercase with underscores (e.g., 'arrow_back', 'account_circle').
 * This matches MUI's icon naming but with a consistent case format.</p>
 *
 * @example
 * ```tsx
 * // Basic usage
 * const { Icon } = useUI();
 *
 * <Icon name="dashboard" />
 * <Icon name="settings" size="large" />
 * <Icon name="error" color="#f44336" />
 *
 * // With custom size
 * <Icon name="notification" size={32} />
 *
 * // In a button
 * <Button startIcon="save">Save</Button>
 * ```
 *
 * @param props - IconProps from UIAdapter contract
 * @returns MUI Icon component
 */
export const MuiIcon: FC<IconProps> = ({
  name,
  size = 'medium',
  color,
  style,
  className,
  onClick,
}) => {
  // Look up icon component from registry
  const IconComponent = useMemo(() => {
    return ICON_REGISTRY[name.toLowerCase()] ?? HelpOutlineIcon;
  }, [name]);

  // Calculate font size from preset or custom value
  const fontSize = typeof size === 'number' ? undefined : SIZE_MAP[size];
  const customSize = typeof size === 'number' ? size : undefined;

  // Build style with custom size if provided
  const iconStyle: CSSProperties = {
    ...style,
    ...(customSize !== undefined && {
      fontSize: customSize,
      width: customSize,
      height: customSize,
    }),
    ...(color && { color }),
  };

  return (
    <IconComponent
      fontSize={fontSize}
      style={iconStyle}
      className={className}
      onClick={onClick as unknown as React.MouseEventHandler<SVGSVGElement>}
    />
  );
};

export default MuiIcon;
