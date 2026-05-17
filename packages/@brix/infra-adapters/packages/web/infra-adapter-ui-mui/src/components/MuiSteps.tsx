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
 * @file MUI Steps Component Implementation
 * @description Material UI implementation of the Steps/Stepper navigation component
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiSteps
 * @version 1.0.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Implements UIAdapter contract for Steps component
 * - Maps MUI Stepper API to unified BrixUI interface
 * - All Steps components must be obtained via useUI() hook
 *
 * @example
 * ```tsx
 * import { useUI } from '@brix-sdk/runtime-sdk-api-web';
 *
 * function TenantWizard() {
 *   const { Steps } = useUI();
 *
 *   return (
 *     <Steps
 *       current={currentStep}
 *       items={[
 *         { title: 'Basic Info' },
 *         { title: 'Configuration' },
 *         { title: 'Confirmation' },
 *       ]}
 *       onChange={(step) => setCurrentStep(step)}
 *     />
 *   );
 * }
 * ```
 */

import React, { type FC, useCallback } from 'react';
import Stepper from '@mui/material/Stepper';
import Step from '@mui/material/Step';
import StepLabel from '@mui/material/StepLabel';
import StepButton from '@mui/material/StepButton';
import type { StepsProps, StepStatus } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Map BrixUI StepStatus to MUI error/completed state
 */
function getStepState(
  index: number,
  current: number,
  itemStatus?: StepStatus,
  globalStatus?: StepStatus,
): { error: boolean; completed: boolean } {
  const effectiveStatus = itemStatus ?? (index === current ? globalStatus : undefined);

  if (effectiveStatus === 'error') {
    return { error: true, completed: false };
  }

  if (effectiveStatus === 'finish' || (index < current && effectiveStatus !== 'wait')) {
    return { error: false, completed: true };
  }

  return { error: false, completed: false };
}

/**
 * MUI Steps Component
 *
 * Material UI implementation of the UIAdapter Steps interface.
 * Wraps MUI Stepper/Step/StepLabel to provide wizard-style navigation.
 *
 * **Features:**
 * - Horizontal and vertical orientation
 * - Clickable steps (when onChange is provided)
 * - Per-step status overrides (error, finish, wait, process)
 * - Small size variant
 * - Step descriptions and custom icons
 *
 * @param props - StepsProps from UIAdapter contract
 * @returns React element
 */
export const MuiSteps: FC<StepsProps> = ({
  current = 0,
  items = [],
  direction = 'horizontal',
  size,
  status,
  onChange,
  style,
  className,
}) => {
  const isClickable = !!onChange;

  const handleStepClick = useCallback(
    (stepIndex: number) => () => {
      onChange?.(stepIndex);
    },
    [onChange],
  );

  return (
    <Stepper
      activeStep={current}
      orientation={direction}
      alternativeLabel={direction === 'horizontal'}
      className={className}
      sx={{
        ...style,
        ...(size === 'small' && {
          '& .MuiStepLabel-label': { fontSize: '0.75rem' },
          '& .MuiStepLabel-iconContainer .MuiSvgIcon-root': { fontSize: '1.25rem' },
        }),
      }}
    >
      {items.map((item, index) => {
        const { error, completed } = getStepState(index, current, item.status, status);

        const labelProps: { optional?: React.ReactNode; error?: boolean; icon?: React.ReactNode } = {};
        if (item.description) {
          labelProps.optional = (
            <span style={{ fontSize: '0.75rem', color: '#999' }}>{item.description}</span>
          );
        }
        if (error) {
          labelProps.error = true;
        }
        if (item.icon) {
          labelProps.icon = item.icon;
        }

        return (
          <Step key={index} completed={completed} disabled={item.disabled}>
            {isClickable ? (
              <StepButton onClick={handleStepClick(index)}>
                <StepLabel {...labelProps}>{item.title}</StepLabel>
              </StepButton>
            ) : (
              <StepLabel {...labelProps}>{item.title}</StepLabel>
            )}
          </Step>
        );
      })}
    </Stepper>
  );
};
