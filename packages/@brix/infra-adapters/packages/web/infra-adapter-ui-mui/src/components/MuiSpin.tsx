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
 * @file MUI Spin Component Implementation
 * @description Material UI implementation of the Spin loading component
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiSpin
 * @version 1.0.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Implements UIAdapter contract for Spin component
 * - Wraps MUI CircularProgress to match Ant Design Spin API
 * - All spinner components must be obtained via useUI() hook
 *
 * @example
 * ```tsx
 * import { useUI } from '@brix-sdk/runtime-sdk-api-web';
 *
 * function LoadingData() {
 *   const { Spin, Card } = useUI();
 *   const [loading, setLoading] = useState(true);
 *
 *   return (
 *     <Spin spinning={loading} tip="Loading...">
 *       <Card>Content here</Card>
 *     </Spin>
 *   );
 * }
 * ```
 */

import {
  type FC,
  type ReactNode,
  useState,
  useEffect,
  useRef,
} from 'react';
import CircularProgress from '@mui/material/CircularProgress';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Backdrop from '@mui/material/Backdrop';
import type { SpinProps } from '@brix-sdk/runtime-sdk-api-web';
import type { ComponentSize } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Size to Pixel Mapping
 *
 * Maps ComponentSize to CircularProgress size in pixels.
 */
const SIZE_MAP: Record<ComponentSize, number> = {
  small: 20,
  medium: 32,
  large: 44,
};

/**
 * Tip Alignment Flex Direction Mapping
 */
const TIP_ALIGN_MAP: Record<string, 'column' | 'column-reverse' | 'row' | 'row-reverse'> = {
  top: 'column-reverse',
  bottom: 'column',
  left: 'row-reverse',
  right: 'row',
};

/**
 * MUI Spin Component
 *
 * Material UI implementation of the UIAdapter Spin interface.
 * Wraps CircularProgress with additional features like content overlay,
 * tip text, delay, and full-screen mode.
 *
 * **Features:**
 * - Standalone spinner or content wrapper mode
 * - Configurable size (small, medium, large)
 * - Loading tip with configurable position
 * - Delay before showing spinner
 * - Full-screen blocking overlay mode
 *
 * @param props - SpinProps from UIAdapter contract
 * @returns React element
 */
export const MuiSpin: FC<SpinProps> = ({
  spinning = true,
  size = 'medium',
  tip,
  tipAlign = 'bottom',
  delay,
  indicator,
  fullScreen = false,
  style,
  wrapperStyle,
  className,
  wrapperClassName,
  children,
}) => {
  const [shouldShow, setShouldShow] = useState(!delay && spinning);
  const delayTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  /**
   * Handle delay logic
   */
  useEffect(() => {
    if (delay && spinning) {
      delayTimerRef.current = setTimeout(() => {
        setShouldShow(true);
      }, delay);
    } else if (!spinning) {
      setShouldShow(false);
    } else if (!delay && spinning) {
      setShouldShow(true);
    }

    return () => {
      if (delayTimerRef.current) {
        clearTimeout(delayTimerRef.current);
      }
    };
  }, [spinning, delay]);

  /**
   * Render the spinner indicator
   */
  const renderIndicator = (): ReactNode => {
    if (indicator) {
      return indicator;
    }
    return (
      <CircularProgress
        size={SIZE_MAP[size]}
        color="primary"
      />
    );
  };

  /**
   * Render spinner with optional tip
   */
  const renderSpinner = (): ReactNode => {
    const flexDirection = TIP_ALIGN_MAP[tipAlign] || 'column';
    const gap = tipAlign === 'left' || tipAlign === 'right' ? 12 : 8;

    return (
      <Box
        sx={{
          display: 'flex',
          flexDirection,
          alignItems: 'center',
          justifyContent: 'center',
          gap: `${gap}px`,
        }}
      >
        {renderIndicator()}
        {tip && (
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{
              textAlign: 'center',
            }}
          >
            {tip}
          </Typography>
        )}
      </Box>
    );
  };

  /**
   * Full screen mode - use Backdrop
   */
  if (fullScreen) {
    return (
      <Backdrop
        open={shouldShow}
        sx={{
          color: '#fff',
          zIndex: (theme) => theme.zIndex.drawer + 1,
          flexDirection: 'column',
          gap: 2,
        }}
        style={style}
        className={className}
      >
        {renderSpinner()}
      </Backdrop>
    );
  }

  /**
   * Standalone mode - no children
   */
  if (!children) {
    if (!shouldShow) {
      return null;
    }
    return (
      <Box
        className={className}
        style={style}
        sx={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {renderSpinner()}
      </Box>
    );
  }

  /**
   * Wrapper mode - overlay children with spinner
   */
  return (
    <Box
      className={wrapperClassName}
      style={wrapperStyle}
      sx={{
        position: 'relative',
      }}
    >
      {/* Content */}
      <Box
        sx={{
          transition: 'opacity 0.3s',
          opacity: shouldShow ? 0.5 : 1,
          pointerEvents: shouldShow ? 'none' : 'auto',
        }}
      >
        {children}
      </Box>

      {/* Overlay Spinner */}
      {shouldShow && (
        <Box
          className={className}
          style={style}
          sx={{
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1,
          }}
        >
          {renderSpinner()}
        </Box>
      )}
    </Box>
  );
};

export default MuiSpin;
