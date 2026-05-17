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
 * @file Shell Page Styles
 * @description Common styles for Web host package
 * @module @brix-sdk/platform-frame-web/pages/styles
 * @version 3.0.0
 */

/**
 * Default theme color
 */
export const DEFAULT_PRIMARY_COLOR = '#4f46e5';

/**
 * Generate Dashboard styles
 */
export function generateDashboardStyles(primaryColor: string = DEFAULT_PRIMARY_COLOR): string {
  return `
    .shell-dashboard {
      height: 100%;
      background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
      padding: 20px;
      box-sizing: border-box;
      overflow-y: auto;
      border-radius: 0;
    }
    
    .shell-dashboard-header {
      margin-bottom: 20px;
    }
    
    .shell-dashboard-welcome {
      font-size: clamp(20px, 3vw, 28px);
      font-weight: 700;
      color: #1e293b;
      margin: 0 0 8px 0;
    }
    
    .shell-dashboard-subtitle {
      font-size: clamp(14px, 2vw, 16px);
      color: #64748b;
      margin: 0;
    }
    
    .shell-dashboard-stats {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
      margin-bottom: 24px;
    }
    
    .shell-stat-card {
      background: white;
      border-radius: 8px;
      padding: 16px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
      transition: transform 0.2s, box-shadow 0.2s;
    }
    
    .shell-stat-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    }
    
    .shell-stat-card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
    }
    
    .shell-stat-card-icon {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
    }
    
    .shell-stat-card-change {
      font-size: 12px;
      font-weight: 600;
      padding: 4px 8px;
      border-radius: 4px;
    }
    
    .shell-stat-card-change.increase {
      background: #dcfce7;
      color: #16a34a;
    }
    
    .shell-stat-card-change.decrease {
      background: #fee2e2;
      color: #dc2626;
    }
    
    .shell-stat-card-value {
      font-size: 32px;
      font-weight: 700;
      color: #1e293b;
      margin-bottom: 4px;
    }
    
    .shell-stat-card-title {
      font-size: 14px;
      color: #64748b;
    }
    
    .shell-dashboard-section {
      margin-bottom: 32px;
    }
    
    .shell-dashboard-section-title {
      font-size: 18px;
      font-weight: 600;
      color: #1e293b;
      margin: 0 0 16px 0;
    }
    
    .shell-quick-actions {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
    }
    
    .shell-quick-action {
      background: white;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 20px;
      cursor: pointer;
      transition: all 0.2s;
      text-align: left;
    }
    
    .shell-quick-action:hover {
      border-color: ${primaryColor};
      box-shadow: 0 4px 12px rgba(79, 70, 229, 0.15);
    }
    
    .shell-quick-action-icon {
      font-size: 32px;
      margin-bottom: 12px;
    }
    
    .shell-quick-action-title {
      font-size: 16px;
      font-weight: 600;
      color: #1e293b;
      margin-bottom: 4px;
    }
    
    .shell-quick-action-desc {
      font-size: 13px;
      color: #64748b;
    }
  `;
}

/**
 * Generate error page styles
 */
export function generateErrorPageStyles(primaryColor: string = DEFAULT_PRIMARY_COLOR): string {
  return `
    .shell-error-page {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #f5f5f5 0%, #e5e5e5 100%);
      padding: 20px;
    }
    
    .shell-error-container {
      text-align: center;
      max-width: 480px;
    }
    
    .shell-error-icon {
      font-size: 64px;
      margin-bottom: 16px;
    }
    
    .shell-error-code {
      font-size: 120px;
      font-weight: 800;
      line-height: 1;
      background: linear-gradient(135deg, ${primaryColor} 0%, #7c3aed 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
      margin-bottom: 16px;
    }
    
    .shell-error-code.error-403 {
      font-size: 80px;
      background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }
    
    .shell-error-title {
      font-size: 24px;
      font-weight: 600;
      color: #1e293b;
      margin: 0 0 12px 0;
    }
    
    .shell-error-message {
      font-size: 16px;
      color: #64748b;
      margin: 0 0 32px 0;
      line-height: 1.6;
    }
    
    .shell-error-actions {
      display: flex;
      gap: 12px;
      justify-content: center;
      flex-wrap: wrap;
    }
    
    .shell-btn {
      padding: 12px 24px;
      border-radius: 8px;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
      border: none;
    }
    
    .shell-btn-primary {
      background: ${primaryColor};
      color: white;
    }
    
    .shell-btn-primary:hover {
      opacity: 0.9;
      transform: translateY(-1px);
    }
    
    .shell-btn-secondary {
      background: white;
      color: #475569;
      border: 1px solid #e2e8f0;
    }
    
    .shell-btn-secondary:hover {
      background: #f8fafc;
      border-color: #cbd5e1;
    }
  `;
}
