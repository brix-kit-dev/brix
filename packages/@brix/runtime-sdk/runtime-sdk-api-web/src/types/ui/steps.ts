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
 * @file Steps Component Type Definitions
 * @description Defines types for the Steps/Stepper component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/steps
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Steps provides wizard/stepper navigation for multi-step workflows
 * - Plugins must obtain Steps through useUI() hook
 * - This contract defines the minimal common interface across MUI Stepper / Ant Design Steps
 */

import type { ReactNode, CSSProperties } from 'react';

/**
 * Step Status
 *
 * Status of an individual step in the stepper.
 */
export type StepStatus = 'wait' | 'process' | 'finish' | 'error';

/**
 * Steps Direction
 *
 * Layout direction of the steps component.
 */
export type StepsDirection = 'horizontal' | 'vertical';

/**
 * Step Item Definition
 *
 * Configuration object for a single step.
 */
export interface StepItem {
  /**
   * Step Title
   *
   * Primary label displayed for the step.
   */
  title: ReactNode;

  /**
   * Step Description
   *
   * Optional secondary text displayed below the title.
   */
  description?: ReactNode;

  /**
   * Step Icon
   *
   * Custom icon name or ReactNode to display instead of the step number.
   */
  icon?: ReactNode;

  /**
   * Step Status Override
   *
   * Override the automatically determined status for this step.
   */
  status?: StepStatus;

  /**
   * Disabled State
   *
   * When true, the step indicator is visually dimmed.
   * @default false
   */
  disabled?: boolean;
}

/**
 * Steps Component Props
 *
 * Stepper/wizard navigation component for guiding users through
 * multi-step workflows. Supports horizontal and vertical layouts.
 *
 * **Design Principle: Workflow Navigation**
 * Steps provide visual progress indication for multi-step processes.
 * The component is controlled via the `current` prop; step transitions
 * are managed by the parent component.
 *
 * @example
 * ```tsx
 * const { Steps } = useUI();
 *
 * <Steps
 *   current={currentStep}
 *   items={[
 *     { title: 'Basic Info' },
 *     { title: 'Configuration' },
 *     { title: 'Confirmation' },
 *   ]}
 * />
 * ```
 */
export interface StepsProps {
  /**
   * Current Step Index
   *
   * Zero-based index of the currently active step.
   * Steps before this index are marked as 'finish',
   * this step as 'process', and after as 'wait'.
   *
   * @default 0
   */
  current?: number;

  /**
   * Step Items
   *
   * Array of step configurations to display.
   */
  items: StepItem[];

  /**
   * Direction
   *
   * Layout direction of the steps.
   * @default 'horizontal'
   */
  direction?: StepsDirection;

  /**
   * Size
   *
   * Size variant of the steps.
   * @default 'default'
   */
  size?: 'default' | 'small';

  /**
   * Status
   *
   * Status of the current step, overrides automatic status.
   */
  status?: StepStatus;

  /**
   * Step Change Callback
   *
   * Fired when a step indicator is clicked (if clickable).
   * Only works for steps that have already been visited.
   *
   * @param current - Index of the clicked step
   */
  onChange?: (current: number) => void;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;
}
