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
 * @file MUI Alert Component Implementation
 * @description Material UI implementation of the Alert feedback component
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiAlert
 * @version 1.0.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Implements UIAdapter contract for Alert component
 * - Maps MUI Alert API to unified BrixUI interface
 * - All alert components must be obtained via useUI() hook
 *
 * @example
 * ```tsx
 * import { useUI } from '@brix-sdk/runtime-sdk-api-web';
 *
 * function ValidationFeedback() {
 *   const { Alert, Stack } = useUI();
 *
 *   return (
 *     <Stack spacing={16}>
 *       <Alert severity="success">Record saved successfully!</Alert>
 *       <Alert severity="error" title="Error" closable>
 *         Failed to connect to server.
 *       </Alert>
 *     </Stack>
 *   );
 * }
 * ```
 */

import React, { type FC, useState, useCallback } from 'react';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import IconButton from '@mui/material/IconButton';
import Collapse from '@mui/material/Collapse';
import CloseIcon from '@mui/icons-material/Close';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import type {
  AlertProps,
  AlertSeverity,
  AlertVariant,
} from '@brix-sdk/runtime-sdk-api-web';

/**
 * MUI Variant Mapping
 *
 * Maps UIAdapter alert variants to MUI Alert variants.
 */
const VARIANT_MAP: Record<AlertVariant, 'filled' | 'outlined' | 'standard'> = {
  filled: 'filled',
  outlined: 'outlined',
  standard: 'standard',
};

/**
 * Icon Mapping for Custom Icons
 *
 * Maps icon string names to MUI icon components.
 */
const ICON_MAP: Record<string, React.ReactElement> = {
  success: <CheckCircleOutlineIcon fontSize="inherit" />,
  info: <InfoOutlinedIcon fontSize="inherit" />,
  warning: <WarningAmberIcon fontSize="inherit" />,
  error: <ErrorOutlineIcon fontSize="inherit" />,
};

/**
 * MUI Alert Component
 *
 * Material UI implementation of the UIAdapter Alert interface.
 * Provides static notification banners for contextual feedback with
 * support for multiple severity levels, variants, and optional actions.
 *
 * **Features:**
 * - Four severity levels: success, info, warning, error
 * - Three visual variants: filled, outlined, standard
 * - Optional title for structured content
 * - Closable mode with callback
 * - Custom action slot for buttons or links
 *
 * @param props - AlertProps from UIAdapter contract
 * @returns React element
 */
export const MuiAlert: FC<AlertProps> = ({
  severity = 'info',
  variant = 'standard',
  title,
  icon,
  closable = false,
  onClose,
  action,
  banner = false,
  showIcon = true,
  className,
  style,
  children,
}) => {
  const [open, setOpen] = useState(true);

  /**
   * Handle close button click
   */
  const handleClose = useCallback(
    (event: React.SyntheticEvent) => {
      setOpen(false);
      onClose?.(event);
    },
    [onClose]
  );

  /**
   * Build icon prop
   *
   * - If icon is false, hide icon
   * - If icon is a string, use mapped icon
   * - Otherwise, use default severity icon
   */
  const getIconProp = (): React.ReactNode | undefined => {
    if (icon === false || !showIcon) {
      return false;
    }
    if (typeof icon === 'string' && ICON_MAP[icon]) {
      return ICON_MAP[icon];
    }
    return undefined; // Use default MUI severity icon
  };

  /**
   * Build action prop
   *
   * Combines custom action with closable button if needed.
   */
  const getActionProp = (): React.ReactNode | undefined => {
    const actions: React.ReactNode[] = [];

    if (action) {
      actions.push(<React.Fragment key="action">{action}</React.Fragment>);
    }

    if (closable) {
      actions.push(
        <IconButton
          key="close"
          aria-label="close"
          color="inherit"
          size="small"
          onClick={handleClose}
        >
          <CloseIcon fontSize="inherit" />
        </IconButton>
      );
    }

    if (actions.length === 0) {
      return undefined;
    }

    return <>{actions}</>;
  };

  /**
   * Alert content with optional title
   */
  const alertContent = (
    <Alert
      severity={severity as AlertSeverity}
      variant={VARIANT_MAP[variant]}
      icon={getIconProp()}
      action={getActionProp()}
      className={className}
      style={{
        ...style,
        ...(banner
          ? {
              borderRadius: 0,
              width: '100%',
            }
          : {}),
      }}
      sx={{
        // Banner mode styling
        ...(banner && {
          justifyContent: 'center',
        }),
      }}
    >
      {title && <AlertTitle>{title}</AlertTitle>}
      {children}
    </Alert>
  );

  // If closable, wrap in Collapse for smooth animation
  if (closable) {
    return <Collapse in={open}>{alertContent}</Collapse>;
  }

  return alertContent;
};

export default MuiAlert;
