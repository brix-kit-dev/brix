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
 * @file Native Progress Component
 * @description Pure CSS implementation of ProgressProps from UIAdapter contract.
 *              Visual indicator for operation completion percentage.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeProgress
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Two types: linear bar and circular
 * - Three status variants: normal, success, exception
 * - Optional percentage display
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic feedback component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for progress indication.
 * Replaces direct MUI LinearProgress/CircularProgress in enterprise-solutions.
 */

import type { FC, CSSProperties } from 'react';
import type { ProgressProps, ProgressStatus } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Status Colors
// ============================================================================

/**
 * Progress Status Colors
 *
 * <p>Colors for different progress states.</p>
 */
const STATUS_COLORS: Record<ProgressStatus, string> = {
  normal: '#1976d2',
  success: '#2e7d32',
  exception: '#d32f2f',
  active: '#1976d2',
};

// ============================================================================
// Progress Component
// ============================================================================

/**
 * Native Progress Component
 *
 * <p>Pure CSS implementation of ProgressProps from UIAdapter contract.
 * Shows progress of an operation as a bar or circle.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS with SVG for circle</li>
 *   <li>Two types: line (bar) and circle</li>
 *   <li>Three status colors: normal, success, exception</li>
 *   <li>Customizable stroke width and colors</li>
 *   <li>Optional percentage and custom format</li>
 * </ul>
 *
 * <h3>Architectural Constraints:</h3>
 * <ul>
 *   <li>This component is an atomic building block</li>
 *   <li>Shell layer uses this via UIAdapter interface</li>
 *   <li>No direct import allowed in Plugin layer</li>
 * </ul>
 *
 * @example
 * ```tsx
 * const { Progress } = useUI();
 *
 * // Linear progress bar
 * <Progress percent={75} />
 *
 * // Circular progress
 * <Progress type="circle" percent={60} status="success" />
 *
 * // Custom format
 * <Progress
 *   percent={100}
 *   status="success"
 *   format={(percent) => `${percent}% Complete`}
 * />
 * ```
 *
 * @param props - ProgressProps from UIAdapter contract
 * @returns Native Progress component
 */
export const NativeProgress: FC<ProgressProps> = ({
  type = 'line',
  percent = 0,
  status = 'normal',
  showInfo = true,
  strokeWidth,
  strokeColor,
  trailColor = '#e0e0e0',
  width = 120,
  format,
  size = 'medium',
  steps,
  style,
  className,
  'data-testid': dataTestId,
}) => {
  // Clamp percent to 0-100
  const clampedPercent = Math.min(100, Math.max(0, percent));

  // Determine color based on status or custom
  const progressColor = strokeColor || STATUS_COLORS[status];

  // Determine stroke width based on size
  const defaultStrokeWidth = size === 'small' ? 4 : size === 'large' ? 10 : 8;
  const actualStrokeWidth = strokeWidth || defaultStrokeWidth;

  // Format percentage text
  const formatInfo = () => {
    if (format) {
      return format(clampedPercent);
    }
    if (status === 'success') {
      return '✓';
    }
    if (status === 'exception') {
      return '✕';
    }
    return `${clampedPercent}%`;
  };

  // Render steps progress
  if (steps && steps > 0) {
    return renderSteps();
  }

  // Render based on type
  if (type === 'circle') {
    return renderCircle();
  }

  return renderLine();

  // ============================================================================
  // Line Progress
  // ============================================================================

  function renderLine() {
    const containerStyle: CSSProperties = {
      display: 'flex',
      alignItems: 'center',
      width: '100%',
      fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
      ...style,
    };

    const trackStyle: CSSProperties = {
      flex: 1,
      height: actualStrokeWidth,
      backgroundColor: trailColor,
      borderRadius: actualStrokeWidth / 2,
      overflow: 'hidden',
    };

    const barStyle: CSSProperties = {
      width: `${clampedPercent}%`,
      height: '100%',
      backgroundColor: progressColor,
      borderRadius: actualStrokeWidth / 2,
      transition: 'width 0.3s ease',
      position: 'relative',
    };

    // Active animation stripe
    const activeAnimationStyle: CSSProperties =
      status === 'active'
        ? {
            backgroundImage:
              'linear-gradient(135deg, rgba(255,255,255,0.25) 25%, transparent 25%, transparent 50%, rgba(255,255,255,0.25) 50%, rgba(255,255,255,0.25) 75%, transparent 75%, transparent)',
            backgroundSize: `${actualStrokeWidth * 2}px ${actualStrokeWidth * 2}px`,
            animation: 'progress-stripe 1s linear infinite',
          }
        : {};

    const infoStyle: CSSProperties = {
      marginLeft: 8,
      fontSize: size === 'small' ? 12 : size === 'large' ? 16 : 14,
      color: status === 'exception' ? '#d32f2f' : status === 'success' ? '#2e7d32' : 'rgba(0, 0, 0, 0.87)',
      minWidth: 36,
      textAlign: 'right',
    };

    return (
      <div
        style={containerStyle}
        className={className}
        data-testid={dataTestId}
        role="progressbar"
        aria-valuenow={clampedPercent}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        {status === 'active' && (
          <style>{`
            @keyframes progress-stripe {
              0% { background-position: 0 0; }
              100% { background-position: ${actualStrokeWidth * 2}px 0; }
            }
          `}</style>
        )}

        <div style={trackStyle}>
          <div style={{ ...barStyle, ...activeAnimationStyle }} />
        </div>

        {showInfo && <span style={infoStyle}>{formatInfo()}</span>}
      </div>
    );
  }

  // ============================================================================
  // Circle Progress
  // ============================================================================

  function renderCircle() {
    const circleSize = typeof width === 'number' ? width : 120;
    const radius = (circleSize - actualStrokeWidth) / 2;
    const circumference = 2 * Math.PI * radius;
    const dashOffset = circumference * ((100 - clampedPercent) / 100);

    const containerStyle: CSSProperties = {
      position: 'relative',
      width: circleSize,
      height: circleSize,
      fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
      ...style,
    };

    const svgStyle: CSSProperties = {
      transform: 'rotate(-90deg)',
    };

    const infoStyle: CSSProperties = {
      position: 'absolute',
      top: '50%',
      left: '50%',
      transform: 'translate(-50%, -50%)',
      fontSize: circleSize / 5,
      fontWeight: 500,
      color: status === 'exception' ? '#d32f2f' : status === 'success' ? '#2e7d32' : 'rgba(0, 0, 0, 0.87)',
    };

    return (
      <div
        style={containerStyle}
        className={className}
        data-testid={dataTestId}
        role="progressbar"
        aria-valuenow={clampedPercent}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <svg width={circleSize} height={circleSize} style={svgStyle}>
          {/* Background circle (trail) */}
          <circle
            cx={circleSize / 2}
            cy={circleSize / 2}
            r={radius}
            fill="none"
            stroke={trailColor}
            strokeWidth={actualStrokeWidth}
          />

          {/* Progress circle */}
          <circle
            cx={circleSize / 2}
            cy={circleSize / 2}
            r={radius}
            fill="none"
            stroke={progressColor}
            strokeWidth={actualStrokeWidth}
            strokeLinecap="round"
            strokeDasharray={circumference}
            strokeDashoffset={dashOffset}
            style={{ transition: 'stroke-dashoffset 0.3s ease' }}
          />
        </svg>

        {showInfo && <span style={infoStyle}>{formatInfo()}</span>}
      </div>
    );
  }

  // ============================================================================
  // Steps Progress
  // ============================================================================

  function renderSteps() {
    const stepsCount = steps!;
    const completedSteps = Math.round((clampedPercent / 100) * stepsCount);

    const containerStyle: CSSProperties = {
      display: 'flex',
      alignItems: 'center',
      gap: 2,
      fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
      ...style,
    };

    const stepStyle = (index: number): CSSProperties => ({
      width: 8,
      height: actualStrokeWidth,
      backgroundColor: index < completedSteps ? progressColor : trailColor,
      borderRadius: 2,
      transition: 'background-color 0.3s ease',
    });

    const infoStyle: CSSProperties = {
      marginLeft: 8,
      fontSize: size === 'small' ? 12 : size === 'large' ? 16 : 14,
      color: status === 'exception' ? '#d32f2f' : status === 'success' ? '#2e7d32' : 'rgba(0, 0, 0, 0.87)',
    };

    return (
      <div
        style={containerStyle}
        className={className}
        data-testid={dataTestId}
        role="progressbar"
        aria-valuenow={clampedPercent}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        {Array.from({ length: stepsCount }).map((_, index) => (
          <div key={index} style={stepStyle(index)} />
        ))}

        {showInfo && <span style={infoStyle}>{formatInfo()}</span>}
      </div>
    );
  }
};

NativeProgress.displayName = 'NativeProgress';

export default NativeProgress;
