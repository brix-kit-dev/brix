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
 * @file MUI Icon Component
 * @description Material UI implementation of IconProps from UIAdapter contract.
 *              Name-based icon lookup using @mui/icons-material library.
 * @module @brix-sdk/infra-adapter-ui-mui/icons/MuiIcon
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
import type { IconProps, ComponentSize } from '@brix-sdk/runtime-sdk-api-web';
import type { SvgIconProps } from '@mui/material/SvgIcon';

// ============================================================================
// Icon Imports (Static for Common Icons)
// ============================================================================

// Import commonly used icons directly for instant rendering
// Using Outlined variants for flat, modern commercial style
// Less common icons will be dynamically loaded

// ============================================================================
// Outlined Variants (Flat, Modern Style - Preferred)
// ============================================================================
import DashboardOutlined from '@mui/icons-material/DashboardOutlined';
import SettingsOutlined from '@mui/icons-material/SettingsOutlined';
import PersonOutlined from '@mui/icons-material/PersonOutlined';
import MenuOutlined from '@mui/icons-material/MenuOutlined';
import CloseOutlined from '@mui/icons-material/CloseOutlined';
import SearchOutlined from '@mui/icons-material/SearchOutlined';
import AddOutlined from '@mui/icons-material/AddOutlined';
import EditOutlined from '@mui/icons-material/EditOutlined';
import DeleteOutlined from '@mui/icons-material/DeleteOutlined';
import SaveOutlined from '@mui/icons-material/SaveOutlined';
import CancelOutlined from '@mui/icons-material/CancelOutlined';
import CheckOutlined from '@mui/icons-material/CheckOutlined';
import ErrorOutlined from '@mui/icons-material/ErrorOutlined';
import WarningAmberOutlined from '@mui/icons-material/WarningAmberOutlined';
import InfoOutlined from '@mui/icons-material/InfoOutlined';
import HelpOutlineOutlined from '@mui/icons-material/HelpOutlineOutlined';
import HomeOutlined from '@mui/icons-material/HomeOutlined';
import LogoutOutlined from '@mui/icons-material/LogoutOutlined';
import LoginOutlined from '@mui/icons-material/LoginOutlined';
import NotificationsOutlined from '@mui/icons-material/NotificationsOutlined';
import EmailOutlined from '@mui/icons-material/EmailOutlined';
import PhoneOutlined from '@mui/icons-material/PhoneOutlined';
import LocationOnOutlined from '@mui/icons-material/LocationOnOutlined';
import CalendarTodayOutlined from '@mui/icons-material/CalendarTodayOutlined';
import AccessTimeOutlined from '@mui/icons-material/AccessTimeOutlined';
import AttachFileOutlined from '@mui/icons-material/AttachFileOutlined';
import CloudUploadOutlined from '@mui/icons-material/CloudUploadOutlined';
import CloudDownloadOutlined from '@mui/icons-material/CloudDownloadOutlined';
import FolderOutlined from '@mui/icons-material/FolderOutlined';
import InsertDriveFileOutlined from '@mui/icons-material/InsertDriveFileOutlined';
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined';
import ImageOutlined from '@mui/icons-material/ImageOutlined';
import VideocamOutlined from '@mui/icons-material/VideocamOutlined';
import MicOutlined from '@mui/icons-material/MicOutlined';
import PrintOutlined from '@mui/icons-material/PrintOutlined';
import ShareOutlined from '@mui/icons-material/ShareOutlined';
import LinkOutlined from '@mui/icons-material/LinkOutlined';
import ContentCopyOutlined from '@mui/icons-material/ContentCopyOutlined';
import ContentCutOutlined from '@mui/icons-material/ContentCutOutlined';
import ContentPasteOutlined from '@mui/icons-material/ContentPasteOutlined';
import UndoOutlined from '@mui/icons-material/UndoOutlined';
import RedoOutlined from '@mui/icons-material/RedoOutlined';
import RefreshOutlined from '@mui/icons-material/RefreshOutlined';
import SyncOutlined from '@mui/icons-material/SyncOutlined';
import FilterListOutlined from '@mui/icons-material/FilterListOutlined';
import SortOutlined from '@mui/icons-material/SortOutlined';
import VisibilityOutlined from '@mui/icons-material/VisibilityOutlined';
import VisibilityOffOutlined from '@mui/icons-material/VisibilityOffOutlined';
import LockOutlined from '@mui/icons-material/LockOutlined';
import LockOpenOutlined from '@mui/icons-material/LockOpenOutlined';
import SecurityOutlined from '@mui/icons-material/SecurityOutlined';
import VpnKeyOutlined from '@mui/icons-material/VpnKeyOutlined';
import GroupOutlined from '@mui/icons-material/GroupOutlined';
import PersonAddOutlined from '@mui/icons-material/PersonAddOutlined';
import PersonRemoveOutlined from '@mui/icons-material/PersonRemoveOutlined';
import BusinessOutlined from '@mui/icons-material/BusinessOutlined';
import WorkOutlined from '@mui/icons-material/WorkOutlined';
import AccountBalanceOutlined from '@mui/icons-material/AccountBalanceOutlined';
import PaymentOutlined from '@mui/icons-material/PaymentOutlined';
import ShoppingCartOutlined from '@mui/icons-material/ShoppingCartOutlined';
import LocalShippingOutlined from '@mui/icons-material/LocalShippingOutlined';
import Inventory2Outlined from '@mui/icons-material/Inventory2Outlined';
import ReceiptOutlined from '@mui/icons-material/ReceiptOutlined';
import AssessmentOutlined from '@mui/icons-material/AssessmentOutlined';
import BarChartOutlined from '@mui/icons-material/BarChartOutlined';
import PieChartOutlined from '@mui/icons-material/PieChartOutlined';
import TimelineOutlined from '@mui/icons-material/TimelineOutlined';
import TrendingUpOutlined from '@mui/icons-material/TrendingUpOutlined';
import TrendingDownOutlined from '@mui/icons-material/TrendingDownOutlined';
import StarOutlined from '@mui/icons-material/StarOutlined';
import StarBorderOutlined from '@mui/icons-material/StarBorderOutlined';
import FavoriteBorderOutlined from '@mui/icons-material/FavoriteBorderOutlined';
import ThumbUpOutlined from '@mui/icons-material/ThumbUpOutlined';
import ThumbDownOutlined from '@mui/icons-material/ThumbDownOutlined';
import ChatOutlined from '@mui/icons-material/ChatOutlined';
import ForumOutlined from '@mui/icons-material/ForumOutlined';
import SupportAgentOutlined from '@mui/icons-material/SupportAgentOutlined';
import BuildOutlined from '@mui/icons-material/BuildOutlined';
import CodeOutlined from '@mui/icons-material/CodeOutlined';
import BugReportOutlined from '@mui/icons-material/BugReportOutlined';
import MemoryOutlined from '@mui/icons-material/MemoryOutlined';
import StorageOutlined from '@mui/icons-material/StorageOutlined';
import CloudOutlined from '@mui/icons-material/CloudOutlined';
import WifiOutlined from '@mui/icons-material/WifiOutlined';
import BluetoothOutlined from '@mui/icons-material/BluetoothOutlined';
import BatteryFullOutlined from '@mui/icons-material/BatteryFullOutlined';
import FlashOnOutlined from '@mui/icons-material/FlashOnOutlined';
import PowerSettingsNewOutlined from '@mui/icons-material/PowerSettingsNewOutlined';
import RestartAltOutlined from '@mui/icons-material/RestartAltOutlined';
import ArrowBackOutlined from '@mui/icons-material/ArrowBackOutlined';
import ArrowForwardOutlined from '@mui/icons-material/ArrowForwardOutlined';
import ArrowUpwardOutlined from '@mui/icons-material/ArrowUpwardOutlined';
import ArrowDownwardOutlined from '@mui/icons-material/ArrowDownwardOutlined';
import ExpandMoreOutlined from '@mui/icons-material/ExpandMoreOutlined';
import ExpandLessOutlined from '@mui/icons-material/ExpandLessOutlined';
import ChevronLeftOutlined from '@mui/icons-material/ChevronLeftOutlined';
import ChevronRightOutlined from '@mui/icons-material/ChevronRightOutlined';
import MoreVertOutlined from '@mui/icons-material/MoreVertOutlined';
import MoreHorizOutlined from '@mui/icons-material/MoreHorizOutlined';
import AppsOutlined from '@mui/icons-material/AppsOutlined';
import ViewListOutlined from '@mui/icons-material/ViewListOutlined';
import ViewModuleOutlined from '@mui/icons-material/ViewModuleOutlined';
import GridOnOutlined from '@mui/icons-material/GridOnOutlined';
import TableChartOutlined from '@mui/icons-material/TableChartOutlined';
import AccountCircleOutlined from '@mui/icons-material/AccountCircleOutlined';
import VerifiedOutlined from '@mui/icons-material/VerifiedOutlined';
import NewReleasesOutlined from '@mui/icons-material/NewReleasesOutlined';
import AnnouncementOutlined from '@mui/icons-material/AnnouncementOutlined';
import CampaignOutlined from '@mui/icons-material/CampaignOutlined';
import EventOutlined from '@mui/icons-material/EventOutlined';
import TaskAltOutlined from '@mui/icons-material/TaskAltOutlined';
import CheckCircleOutlined from '@mui/icons-material/CheckCircleOutlined';
import RadioButtonUncheckedOutlined from '@mui/icons-material/RadioButtonUncheckedOutlined';
import RadioButtonCheckedOutlined from '@mui/icons-material/RadioButtonCheckedOutlined';
import CheckBoxOutlined from '@mui/icons-material/CheckBoxOutlined';
import CheckBoxOutlineBlankOutlined from '@mui/icons-material/CheckBoxOutlineBlankOutlined';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import QuestionMarkOutlined from '@mui/icons-material/QuestionMarkOutlined';

// Additional Outlined icons for business menus
import HandshakeOutlined from '@mui/icons-material/HandshakeOutlined';
import CategoryOutlined from '@mui/icons-material/CategoryOutlined';
import ViewCarouselOutlined from '@mui/icons-material/ViewCarouselOutlined';
import BadgeOutlined from '@mui/icons-material/BadgeOutlined';
import ManageAccountsOutlined from '@mui/icons-material/ManageAccountsOutlined';
import AdminPanelSettingsOutlined from '@mui/icons-material/AdminPanelSettingsOutlined';
import MailOutlined from '@mui/icons-material/MailOutlined';
import InboxOutlined from '@mui/icons-material/InboxOutlined';
import SendOutlined from '@mui/icons-material/SendOutlined';
import ArticleOutlined from '@mui/icons-material/ArticleOutlined';
import AssignmentOutlined from '@mui/icons-material/AssignmentOutlined';
import ListAltOutlined from '@mui/icons-material/ListAltOutlined';
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined';
import EventNoteOutlined from '@mui/icons-material/EventNoteOutlined';
import AddCircleOutlineOutlined from '@mui/icons-material/AddCircleOutlineOutlined';
import PeopleOutlined from '@mui/icons-material/PeopleOutlined';
import StorefrontOutlined from '@mui/icons-material/StorefrontOutlined';

// ============================================================================
// Domain-Specific Icons (Partners, Booking, Medical, Compliance, etc.)
// ============================================================================
import AllInclusiveOutlined from '@mui/icons-material/AllInclusiveOutlined';
import LocalHospitalOutlined from '@mui/icons-material/LocalHospitalOutlined';
import HotelOutlined from '@mui/icons-material/HotelOutlined';
import DirectionsCarOutlined from '@mui/icons-material/DirectionsCarOutlined';
import TranslateOutlined from '@mui/icons-material/TranslateOutlined';
import ArchiveOutlined from '@mui/icons-material/ArchiveOutlined';
import BlockOutlined from '@mui/icons-material/BlockOutlined';
import BookmarkOutlined from '@mui/icons-material/BookmarkOutlined';
import DevicesOutlined from '@mui/icons-material/DevicesOutlined';
import DoneOutlined from '@mui/icons-material/DoneOutlined';
import DoneAllOutlined from '@mui/icons-material/DoneAllOutlined';
import DraftsOutlined from '@mui/icons-material/DraftsOutlined';
import DrawOutlined from '@mui/icons-material/DrawOutlined';
import EditNoteOutlined from '@mui/icons-material/EditNoteOutlined';
import EventAvailableOutlined from '@mui/icons-material/EventAvailableOutlined';
import ExtensionOutlined from '@mui/icons-material/ExtensionOutlined';
import FlightLandOutlined from '@mui/icons-material/FlightLandOutlined';
import FolderOpenOutlined from '@mui/icons-material/FolderOpenOutlined';
import HealingOutlined from '@mui/icons-material/HealingOutlined';
import HistoryOutlined from '@mui/icons-material/HistoryOutlined';
import HourglassEmptyOutlined from '@mui/icons-material/HourglassEmptyOutlined';
import InsightsOutlined from '@mui/icons-material/InsightsOutlined';
import LinkOffOutlined from '@mui/icons-material/LinkOffOutlined';
import LocalOfferOutlined from '@mui/icons-material/LocalOfferOutlined';
import MedicalServicesOutlined from '@mui/icons-material/MedicalServicesOutlined';
import MeetingRoomOutlined from '@mui/icons-material/MeetingRoomOutlined';
import NavigateBeforeOutlined from '@mui/icons-material/NavigateBeforeOutlined';
import NavigateNextOutlined from '@mui/icons-material/NavigateNextOutlined';
import NoteAddOutlined from '@mui/icons-material/NoteAddOutlined';
import PauseOutlined from '@mui/icons-material/PauseOutlined';
import PauseCircleOutlined from '@mui/icons-material/PauseCircleOutlined';
import PersonOffOutlined from '@mui/icons-material/PersonOffOutlined';
import PlayArrowOutlined from '@mui/icons-material/PlayArrowOutlined';
import PlayCircleOutlined from '@mui/icons-material/PlayCircleOutlined';
import PriorityHighOutlined from '@mui/icons-material/PriorityHighOutlined';
import RemoveOutlined from '@mui/icons-material/RemoveOutlined';
import RemoveCircleOutlined from '@mui/icons-material/RemoveCircleOutlined';
import ReplyOutlined from '@mui/icons-material/ReplyOutlined';
import RestaurantOutlined from '@mui/icons-material/RestaurantOutlined';
import RingVolumeOutlined from '@mui/icons-material/RingVolumeOutlined';
import ScheduleOutlined from '@mui/icons-material/ScheduleOutlined';
import SkipNextOutlined from '@mui/icons-material/SkipNextOutlined';
import StopOutlined from '@mui/icons-material/StopOutlined';
import SwapHorizOutlined from '@mui/icons-material/SwapHorizOutlined';
import CameraAltOutlined from '@mui/icons-material/CameraAltOutlined';
import TodayOutlined from '@mui/icons-material/TodayOutlined';
import UpdateOutlined from '@mui/icons-material/UpdateOutlined';
import UploadFileOutlined from '@mui/icons-material/UploadFileOutlined';
import AddBusinessOutlined from '@mui/icons-material/AddBusinessOutlined';
import IndeterminateCheckBoxOutlined from '@mui/icons-material/IndeterminateCheckBoxOutlined';

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
 * Uses Outlined variants for flat, modern commercial style.
 * Names use lowercase with underscores for consistent API.</p>
 */
const ICON_REGISTRY: Record<string, MuiIconComponent> = {
  // Navigation & Menu
  dashboard: DashboardOutlined,
  home: HomeOutlined,
  menu: MenuOutlined,
  apps: AppsOutlined,
  settings: SettingsOutlined,
  
  // User & Auth
  person: PersonOutlined,
  account_circle: AccountCircleOutlined,
  group: GroupOutlined,
  people: PeopleOutlined,
  person_add: PersonAddOutlined,
  person_remove: PersonRemoveOutlined,
  login: LoginOutlined,
  logout: LogoutOutlined,
  lock: LockOutlined,
  lock_open: LockOpenOutlined,
  security: SecurityOutlined,
  vpn_key: VpnKeyOutlined,
  verified: VerifiedOutlined,
  badge: BadgeOutlined,
  manage_accounts: ManageAccountsOutlined,
  admin_panel_settings: AdminPanelSettingsOutlined,
  
  // Actions
  add: AddOutlined,
  add_circle: AddCircleOutlineOutlined,
  edit: EditOutlined,
  delete: DeleteOutlined,
  save: SaveOutlined,
  cancel: CancelOutlined,
  close: CloseOutlined,
  check: CheckOutlined,
  search: SearchOutlined,
  refresh: RefreshOutlined,
  sync: SyncOutlined,
  undo: UndoOutlined,
  redo: RedoOutlined,
  filter_list: FilterListOutlined,
  sort: SortOutlined,
  share: ShareOutlined,
  link: LinkOutlined,
  copy: ContentCopyOutlined,
  cut: ContentCutOutlined,
  paste: ContentPasteOutlined,
  print: PrintOutlined,
  
  // Status & Feedback
  error: ErrorOutlined,
  warning: WarningAmberOutlined,
  info: InfoOutlined,
  help: HelpOutlineOutlined,
  help_outline: HelpOutlineIcon,
  question_mark: QuestionMarkOutlined,
  check_circle: CheckCircleOutlined,
  task_alt: TaskAltOutlined,
  new_releases: NewReleasesOutlined,
  announcement: AnnouncementOutlined,
  campaign: CampaignOutlined,
  
  // Communication
  notifications: NotificationsOutlined,
  email: EmailOutlined,
  mail: MailOutlined,
  inbox: InboxOutlined,
  send: SendOutlined,
  phone: PhoneOutlined,
  chat: ChatOutlined,
  forum: ForumOutlined,
  support_agent: SupportAgentOutlined,
  
  // Content & Files
  folder: FolderOutlined,
  file: InsertDriveFileOutlined,
  description: DescriptionOutlined,
  article: ArticleOutlined,
  assignment: AssignmentOutlined,
  attach_file: AttachFileOutlined,
  image: ImageOutlined,
  videocam: VideocamOutlined,
  mic: MicOutlined,
  
  // Cloud & Storage
  cloud: CloudOutlined,
  cloud_upload: CloudUploadOutlined,
  cloud_download: CloudDownloadOutlined,
  storage: StorageOutlined,
  memory: MemoryOutlined,
  
  // Location & Time
  location_on: LocationOnOutlined,
  calendar_today: CalendarTodayOutlined,
  calendar_month: CalendarMonthOutlined,
  access_time: AccessTimeOutlined,
  event: EventOutlined,
  event_note: EventNoteOutlined,
  
  // Business
  business: BusinessOutlined,
  handshake: HandshakeOutlined,
  storefront: StorefrontOutlined,
  work: WorkOutlined,
  account_balance: AccountBalanceOutlined,
  payment: PaymentOutlined,
  shopping_cart: ShoppingCartOutlined,
  local_shipping: LocalShippingOutlined,
  inventory: Inventory2Outlined,
  category: CategoryOutlined,
  receipt: ReceiptOutlined,
  
  // Charts & Data
  assessment: AssessmentOutlined,
  bar_chart: BarChartOutlined,
  pie_chart: PieChartOutlined,
  timeline: TimelineOutlined,
  trending_up: TrendingUpOutlined,
  trending_down: TrendingDownOutlined,
  table_chart: TableChartOutlined,
  
  // Lists & Views
  list: ListAltOutlined,
  view_list: ViewListOutlined,
  view_carousel: ViewCarouselOutlined,
  view_module: ViewModuleOutlined,
  grid_on: GridOnOutlined,
  
  // Ratings & Social
  star: StarOutlined,
  star_border: StarBorderOutlined,
  favorite: FavoriteBorderOutlined,
  thumb_up: ThumbUpOutlined,
  thumb_down: ThumbDownOutlined,
  
  // Development
  build: BuildOutlined,
  code: CodeOutlined,
  bug_report: BugReportOutlined,
  
  // Hardware & System
  wifi: WifiOutlined,
  bluetooth: BluetoothOutlined,
  battery_full: BatteryFullOutlined,
  flash_on: FlashOnOutlined,
  power_settings_new: PowerSettingsNewOutlined,
  restart_alt: RestartAltOutlined,
  
  // Arrows & Navigation
  arrow_back: ArrowBackOutlined,
  arrow_forward: ArrowForwardOutlined,
  arrow_upward: ArrowUpwardOutlined,
  arrow_downward: ArrowDownwardOutlined,
  expand_more: ExpandMoreOutlined,
  expand_less: ExpandLessOutlined,
  chevron_left: ChevronLeftOutlined,
  chevron_right: ChevronRightOutlined,
  
  // Menus & Views
  more_vert: MoreVertOutlined,
  more_horiz: MoreHorizOutlined,
  
  // Visibility
  visibility: VisibilityOutlined,
  visibility_off: VisibilityOffOutlined,
  
  // Form Controls
  checkbox: CheckBoxOutlined,
  check_box: CheckBoxOutlined,
  checkbox_blank: CheckBoxOutlineBlankOutlined,
  radio_checked: RadioButtonCheckedOutlined,
  radio_unchecked: RadioButtonUncheckedOutlined,
  radio_button_unchecked: RadioButtonUncheckedOutlined,
  indeterminate_check_box: IndeterminateCheckBoxOutlined,

  // Domain: Partners / Medical / Hospitality / Transport
  all_inclusive: AllInclusiveOutlined,
  local_hospital: LocalHospitalOutlined,
  hotel: HotelOutlined,
  directions_car: DirectionsCarOutlined,
  translate: TranslateOutlined,
  medical_services: MedicalServicesOutlined,
  healing: HealingOutlined,
  restaurant: RestaurantOutlined,
  flight_land: FlightLandOutlined,
  meeting_room: MeetingRoomOutlined,
  add_business: AddBusinessOutlined,

  // Domain: Scheduling / Time
  schedule: ScheduleOutlined,
  today: TodayOutlined,
  event_available: EventAvailableOutlined,
  hourglass_empty: HourglassEmptyOutlined,
  update: UpdateOutlined,
  history: HistoryOutlined,

  // Domain: Workflow / Status
  done: DoneOutlined,
  done_all: DoneAllOutlined,
  block: BlockOutlined,
  pause: PauseOutlined,
  pause_circle: PauseCircleOutlined,
  play_arrow: PlayArrowOutlined,
  play_circle: PlayCircleOutlined,
  stop: StopOutlined,
  skip_next: SkipNextOutlined,
  priority_high: PriorityHighOutlined,
  swap_horiz: SwapHorizOutlined,

  // Domain: Content / Documents
  archive: ArchiveOutlined,
  bookmark: BookmarkOutlined,
  drafts: DraftsOutlined,
  draw: DrawOutlined,
  edit_note: EditNoteOutlined,
  note_add: NoteAddOutlined,
  folder_open: FolderOpenOutlined,
  upload_file: UploadFileOutlined,
  reply: ReplyOutlined,
  insights: InsightsOutlined,

  // Domain: Devices / System
  devices: DevicesOutlined,
  extension: ExtensionOutlined,
  ring_volume: RingVolumeOutlined,
  camera: CameraAltOutlined,
  take_photo: CameraAltOutlined,

  // Domain: People
  person_off: PersonOffOutlined,

  // Domain: Actions (additional)
  remove: RemoveOutlined,
  remove_circle: RemoveCircleOutlined,
  link_off: LinkOffOutlined,
  local_offer: LocalOfferOutlined,
  navigate_before: NavigateBeforeOutlined,
  navigate_next: NavigateNextOutlined,
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
