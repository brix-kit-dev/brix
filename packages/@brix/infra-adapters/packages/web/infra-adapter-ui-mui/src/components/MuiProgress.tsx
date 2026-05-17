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
 * @file MUI Progress Component Implementation
 * @description Material UI implementation of the Progress indicator component
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiProgress
 * @version 1.0.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Implements UIAdapter contract for Progress component
 * - Maps MUI LinearProgress/CircularProgress to unified BrixUI interface
 * - All progress components must be obtained via useUI() hook
 *
 * @example
 * ```tsx
 * import { useUI } from '@brix-sdk/runtime-sdk-api-web';
 *
 * function UploadProgress() {
 *   const { Progress, Stack } = useUI();
 *
 *   return (
 *     <Stack spacing={16}>
 *       <Progress percent={45} />
 *       <Progress type="circle" percent={75} />
 *       <Progress percent={100} status="success" />
 *     </Stack>
 *   );
 * }
 * ```
 */

import { type FC, useMemo } from 'react';
import LinearProgress from '@mui/material/LinearProgress';
import CircularProgress from '@mui/material/CircularProgress';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type {
  ProgressProps,
  ProgressStatus,
} from '@brix-sdk/runtime-sdk-api-web';
import type { ComponentSize } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Status to Color Mapping
 *
 * Maps ProgressStatus to MUI theme colors.
 */
const STATUS_COLOR_MAP: Record<ProgressStatus | 'normal', 'primary' | 'success' | 'error' | 'info'> = {
  normal: 'primary',
  success: 'success',
  error: 'error',
  active: 'primary',
};

/**
 * Size to Stroke Width Mapping (for line type)
 */
const LINE_HEIGHT_MAP: Record<ComponentSize, number> = {
  small: 4,
  medium: 8,
  large: 12,
};

/**
 * Size to Circle Width Mapping
 */
const CIRCLE_SIZE_MAP: Record<ComponentSize, number> = {
  small: 60,
  medium: 100,
  large: 140,
};

/**
 * Size to Circle Stroke Width Mapping
 */
const CIRCLE_STROKE_MAP: Record<ComponentSize, number> = {
  small: 4,
  medium: 6,
  large: 8,
};

/**
 * MUI Progress Component
 *
 * Material UI implementation of the UIAdapter Progress interface.
 * Supports linear (line), circular (circle), and dashboard progress types.
 *
 * **Features:**
 * - Three types: line, circle, dashboard
 * - Four status levels: normal, success, error, active
 * - Configurable size and stroke width
 * - Custom format function for percentage display
 * - Show/hide percentage info
 *
 * @param props - ProgressProps from UIAdapter contract
 * @returns React element
 */
export const MuiProgress: FC<ProgressProps> = ({
  type = 'line',
  percent = 0,
  status = 'normal',
  showInfo = true,
  size = 'medium',
  width,
  strokeWidth,
  strokeColor,
  trailColor,
  format,
  className,
  style,
}) => {
  /**
   * Clamp percent to 0-100 range
   */
  const clampedPercent = useMemo(() => {
    return Math.max(0, Math.min(100, percent));
  }, [percent]);

  /**
   * Determine effective status
   * Auto-detect success when percent reaches 100
   */
  const effectiveStatus = useMemo(() => {
    if (clampedPercent === 100 && status === 'normal') {
      return 'success';
    }
    return status;
  }, [clampedPercent, status]);

  /**
   * Format percentage text
   */
  const formatPercent = (): string => {
    if (format) {
      return format(clampedPercent);
    }
    return `${clampedPercent}%`;
  };

  /**
   * Render linear progress bar
   */
  const renderLinear = () => {
    const height = strokeWidth || LINE_HEIGHT_MAP[size];

    return (
      <Box
        className={className}
        style={style}
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          width: '100%',
        }}
      >
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <LinearProgress
            variant={status === 'active' ? 'indeterminate' : 'determinate'}
            value={status === 'active' ? undefined : clampedPercent}
            color={STATUS_COLOR_MAP[effectiveStatus]}
            sx={{
              height,
              borderRadius: height / 2,
              bgcolor: trailColor || 'grey.200',
              '& .MuiLinearProgress-bar': {
                borderRadius: height / 2,
                ...(strokeColor && { bgcolor: strokeColor }),
              },
            }}
          />
        </Box>
        {showInfo && (
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{ minWidth: 48, textAlign: 'right' }}
          >
            {formatPercent()}
          </Typography>
        )}
      </Box>
    );
  };

  /**
   * Render circular progress
   */
  const renderCircular = () => {
    const circleSize = width || CIRCLE_SIZE_MAP[size];
    const thickness = strokeWidth || CIRCLE_STROKE_MAP[size];
    // MUI thickness is relative (default 3.6), convert from pixels
    const muiThickness = (thickness / circleSize) * 100;

    return (
      <Box
        className={className}
        style={style}
        sx={{
          position: 'relative',
          display: 'inline-flex',
        }}
      >
        {/* Background track */}
        <CircularProgress
          variant="determinate"
          value={100}
          size={circleSize}
          thickness={muiThickness}
          sx={{
            color: trailColor || 'grey.200',
          }}
        />
        {/* Progress value */}
        <CircularProgress
          variant="determinate"
          value={clampedPercent}
          size={circleSize}
          thickness={muiThickness}
          color={STATUS_COLOR_MAP[effectiveStatus]}
          sx={{
            position: 'absolute',
            left: 0,
            ...(strokeColor && { color: strokeColor }),
          }}
        />
        {/* Center text */}
        {showInfo && (
          <Box
            sx={{
              position: 'absolute',
              top: 0,
              left: 0,
              bottom: 0,
              right: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Typography
              variant={size === 'small' ? 'caption' : 'body2'}
              color="text.secondary"
            >
              {formatPercent()}
            </Typography>
          </Box>
        )}
      </Box>
    );
  };

  /**
   * Render dashboard (semi-circular) progress
   *
   * MUI doesn't have native dashboard progress.
   * Implement using CSS transform on circular progress.
   */
  const renderDashboard = () => {
    const circleSize = width || CIRCLE_SIZE_MAP[size];
    const thickness = strokeWidth || CIRCLE_STROKE_MAP[size];
    const muiThickness = (thickness / circleSize) * 100;
    // Dashboard shows 75% of circle (270 degrees)
    const dashboardPercent = clampedPercent * 0.75;

    return (
      <Box
        className={className}
        style={style}
        sx={{
          position: 'relative',
          display: 'inline-flex',
          transform: 'rotate(-135deg)',
        }}
      >
        {/* Background track - 75% of circle */}
        <CircularProgress
          variant="determinate"
          value={75}
          size={circleSize}
          thickness={muiThickness}
          sx={{
            color: trailColor || 'grey.200',
          }}
        />
        {/* Progress value */}
        <CircularProgress
          variant="determinate"
          value={dashboardPercent}
          size={circleSize}
          thickness={muiThickness}
          color={STATUS_COLOR_MAP[effectiveStatus]}
          sx={{
            position: 'absolute',
            left: 0,
            ...(strokeColor && { color: strokeColor }),
          }}
        />
        {/* Center text - counter-rotate to be readable */}
        {showInfo && (
          <Box
            sx={{
              position: 'absolute',
              top: 0,
              left: 0,
              bottom: 0,
              right: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              transform: 'rotate(135deg)', // Counter-rotate
            }}
          >
            <Typography
              variant={size === 'small' ? 'caption' : 'body2'}
              color="text.secondary"
            >
              {formatPercent()}
            </Typography>
          </Box>
        )}
      </Box>
    );
  };

  // Render based on type
  switch (type) {
    case 'circle':
      return renderCircular();
    case 'dashboard':
      return renderDashboard();
    case 'line':
    default:
      return renderLinear();
  }
};

export default MuiProgress;
