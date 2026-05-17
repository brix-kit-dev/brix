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
 * @file Layout Container Component
 * @description Container that automatically selects layout mode
 * @module @brix-sdk/platform-frame-web/components/LayoutContainer
 * @version 3.0.0
 */

import { type ReactNode } from 'react';
import type { LayoutState } from '@brix-sdk/runtime-sdk-api-web';
import { ConsoleLayout } from '../layouts/ConsoleLayout';
import { PortalLayout } from '../layouts/PortalLayout';
import { MinimalLayout } from '../layouts/MinimalLayout';

/**
 * Layout Container Props
 */
export interface LayoutContainerProps {
  /**
   * Layout state
   */
  layoutState: LayoutState;
  
  /**
   * Child content
   */
  children: ReactNode;
  
  /**
   * Console layout header
   */
  consoleHeader?: ReactNode;
  
  /**
   * Console layout sidebar
   */
  consoleSidebar?: ReactNode;
  
  /**
   * Portal layout header
   */
  portalHeader?: ReactNode;
  
  /**
   * Portal layout footer
   */
  portalFooter?: ReactNode;
  
  /**
   * Custom class name
   */
  className?: string;
}

/**
 * Layout Container Component
 * 
 * Automatically selects the corresponding layout component based on layoutMode.
 * 
 * [Usage Example]
 * ```tsx
 * function App() {
 *   const layoutState = layoutCapability.getLayoutState();
 *   
 *   return (
 *     <LayoutContainer
 *       layoutState={layoutState}
 *       consoleHeader={<ConsoleHeader />}
 *       consoleSidebar={<ConsoleSidebar />}
 *       portalHeader={<PortalHeader />}
 *       portalFooter={<PortalFooter />}
 *     >
 *       <Outlet />
 *     </LayoutContainer>
 *   );
 * }
 * ```
 */
export function LayoutContainer({
  layoutState,
  children,
  consoleHeader,
  consoleSidebar,
  portalHeader,
  portalFooter,
  className,
}: LayoutContainerProps): ReactNode {
  const { layoutMode } = layoutState;
  
  switch (layoutMode) {
    case 'console':
      return (
        <ConsoleLayout
          layoutState={layoutState}
          header={consoleHeader}
          sidebar={consoleSidebar}
          className={className}
        >
          {children}
        </ConsoleLayout>
      );
      
    case 'portal':
      return (
        <PortalLayout
          layoutState={layoutState}
          header={portalHeader}
          footer={portalFooter}
          className={className}
        >
          {children}
        </PortalLayout>
      );
      
    case 'minimal':
      return (
        <MinimalLayout
          layoutState={layoutState}
          className={className}
        >
          {children}
        </MinimalLayout>
      );
      
    default:
      // Default to console layout
      return (
        <ConsoleLayout
          layoutState={layoutState}
          header={consoleHeader}
          sidebar={consoleSidebar}
          className={className}
        >
          {children}
        </ConsoleLayout>
      );
  }
}
