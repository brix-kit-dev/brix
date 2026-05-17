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
 * @file LoginForm
 * @description CSS-in-JS - MUI
 * @module @brix-sdk/platform-auth-web/components/LoginForm/styles
 * @version 3.0.1
 * 
 * -
 * - MUI
 * -
 * -
 * -
 */

/**
 * MUI - Primary Blue
 */
export const DEFAULT_PRIMARY_COLOR = '#1976d2';

/**
 * MUI -
 */
export const DEFAULT_SECONDARY_COLOR = '#f5f5f5';

/**
 * MUI -
 */
export const DEFAULT_TERTIARY_COLOR = '#ffffff';

/**
 */
export const DEFAULT_GRADIENT: [string, string] = [DEFAULT_TERTIARY_COLOR, DEFAULT_SECONDARY_COLOR];

/**
 */
export interface LoginStyleOptions {
  primaryColor?: string;
  secondaryColor?: string;
  tertiaryColor?: string;
  gradientColors?: [string, string];
}

/**
 */
export function generateLoginStyles(options: LoginStyleOptions): string {
  const primary = options.primaryColor || DEFAULT_PRIMARY_COLOR;
  const secondary = options.secondaryColor || DEFAULT_SECONDARY_COLOR;
  const tertiary = options.tertiaryColor || DEFAULT_TERTIARY_COLOR;
 // gradientColors
  
  return `
    html, body {
      margin: 0;
      padding: 0;
      overflow: hidden;
      height: 100%;
    }
    
    #root {
      height: 100%;
      overflow: hidden;
    }
    
    /* ====== MUI Clean Background ====== */
    .brix-login-page {
      height: 100vh;
      width: 100vw;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
      box-sizing: border-box;
      overflow: hidden;
      position: relative;
      
      background: ${secondary};
    }
    
    .brix-login-page::before,
    .brix-login-page::after {
      display: none;
    }
    
    /* ====== MUI Style Card ====== */
    .brix-login-container {
      width: 100%;
      max-width: 400px;
      position: relative;
      z-index: 1;
      
      /* MUI Paper Style */
      background: ${tertiary};
      
      border-radius: 16px;
      
      /* MUI Standard Shadow (elevation 8) */
      box-shadow: 
        0px 5px 5px -3px rgba(0,0,0,0.06),
        0px 8px 10px 1px rgba(0,0,0,0.042),
        0px 3px 14px 2px rgba(0,0,0,0.036);
      
      padding: 40px 36px;
    }
    
    .brix-register-container {
      max-width: 520px;
    }
    
    .brix-form-row-responsive {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }
    
    @media (max-width: 560px) {
      .brix-form-row-responsive {
        grid-template-columns: 1fr;
      }
      
      .brix-register-container {
        max-width: 400px;
      }
    }
    
    .brix-login-header {
      text-align: center;
      margin-bottom: 32px;
    }
    
    /* Logo Container - MUI Style */
    .brix-login-logo {
      width: 56px;
      height: 56px;
      margin: 0 auto 20px;
      background: ${primary};
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 2px 8px ${primary}40;
    }
    
    .brix-login-logo-icon {
      font-size: 28px;
      color: white;
    }
    
    .brix-login-logo-image {
      max-width: 36px;
      max-height: 36px;
      object-fit: contain;
    }
    
    /* MUI Typography */
    .brix-login-title {
      margin: 0 0 8px;
      font-size: 24px;
      font-weight: 500;
      color: rgba(0, 0, 0, 0.87);
      letter-spacing: 0;
    }
    
    .brix-login-subtitle {
      margin: 0;
      font-size: 14px;
      color: rgba(0, 0, 0, 0.6);
      line-height: 1.5;
      font-weight: 400;
    }
    
    .brix-login-form {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }
    
    /* MUI Alert Style */
    .brix-login-error {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px 16px;
      background: #fdeded;
      border-radius: 8px;
      color: #5f2120;
      font-size: 14px;
      font-weight: 400;
      border-left: 4px solid #ef5350;
    }
    
    .brix-form-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    
    .brix-form-label {
      font-size: 14px;
      font-weight: 500;
      color: rgba(0, 0, 0, 0.87);
      margin-left: 2px;
    }
    
    /* MUI TextField Outlined Style */
    .brix-form-input {
      padding: 14px 14px;
      background: ${tertiary};
      border: 1px solid rgba(0, 0, 0, 0.23);
      border-radius: 8px;
      font-size: 16px;
      color: rgba(0, 0, 0, 0.87);
      transition: border-color 0.2s, box-shadow 0.2s;
    }
    
    .brix-form-input:hover {
      border-color: rgba(0, 0, 0, 0.87);
    }
    
    .brix-form-input:focus {
      outline: none;
      border-color: ${primary};
      box-shadow: 0 0 0 2px ${primary}20;
    }
    
    .brix-form-input:disabled {
      background: rgba(0, 0, 0, 0.04);
      cursor: not-allowed;
      color: rgba(0, 0, 0, 0.38);
    }
    
    .brix-form-input::placeholder {
      color: rgba(0, 0, 0, 0.42);
    }
    
    .brix-form-input.brix-input-error {
      border-color: #EF4444;
    }
    
    .brix-form-input.brix-input-error:focus {
      border-color: #EF4444;
      box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.1);
    }
    
    .brix-field-error {
      display: block;
      font-size: 13px;
      color: #EF4444;
      margin-top: 4px;
    }
    
    /* Remember Me & Forgot Password Row */
    .brix-form-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    
    .brix-checkbox-label {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      font-size: 14px;
      color: rgba(0, 0, 0, 0.87);
      cursor: pointer;
    }
    
    .brix-checkbox-label input[type="checkbox"] {
      width: 18px;
      height: 18px;
      accent-color: ${primary};
      cursor: pointer;
      border-radius: 4px;
      flex-shrink: 0;
      margin-top: 2px;
    }
    
    .brix-checkbox-label a {
      color: ${primary};
      text-decoration: none;
      margin: 0 2px;
    }
    
    .brix-checkbox-label a:hover {
      text-decoration: underline;
    }
    
    .brix-forgot-link {
      font-size: 14px;
      color: ${primary};
      text-decoration: none;
      background: none;
      border: none;
      cursor: pointer;
      padding: 0;
      font-weight: 500;
    }
    
    .brix-forgot-link:hover {
      text-decoration: underline;
    }
    
    /* ====== Login/Submit Button - MUI Button Contained Style ====== */
    .brix-login-button,
    .brix-submit-button {
      padding: 12px 24px;
      background: ${primary};
      color: white;
      border: none;
      border-radius: 8px;
      font-size: 15px;
      font-weight: 500;
      cursor: pointer;
      transition: background-color 0.2s, box-shadow 0.2s;
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      box-shadow: 0 2px 4px -1px rgba(0,0,0,0.2), 
                  0 4px 5px 0 rgba(0,0,0,0.14), 
                  0 1px 10px 0 rgba(0,0,0,0.12);
    }
    
    .brix-login-button:hover:not(:disabled),
    .brix-submit-button:hover:not(:disabled) {
      background: #1565c0;
      box-shadow: 0 4px 8px -2px rgba(0,0,0,0.2), 
                  0 8px 10px 1px rgba(0,0,0,0.14), 
                  0 3px 14px 2px rgba(0,0,0,0.12);
    }
    
    .brix-login-button:active:not(:disabled),
    .brix-submit-button:active:not(:disabled) {
      background: #0d47a1;
    }
    
    .brix-login-button:disabled,
    .brix-submit-button:disabled {
      background: rgba(0, 0, 0, 0.12);
      color: rgba(0, 0, 0, 0.26);
      cursor: not-allowed;
      box-shadow: none;
    }
    
    .brix-loading-spinner {
      display: inline-block;
      width: 16px;
      height: 16px;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: white;
      border-radius: 50%;
      animation: brix-spin 1s linear infinite;
    }
    
    @keyframes brix-spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }
    
    .brix-social-divider {
      display: flex;
      align-items: center;
      gap: 16px;
      margin: 4px 0;
    }
    
    .brix-social-divider-line {
      flex: 1;
      height: 1px;
      background: rgba(0, 0, 0, 0.12);
    }
    
    .brix-social-divider-text {
      font-size: 13px;
      color: rgba(0, 0, 0, 0.6);
      white-space: nowrap;
      font-weight: 400;
    }
    
    /* ====== Social Login Buttons - MUI Button Outlined Style ====== */
    .brix-social-buttons {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    
    .brix-social-button {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12px;
      padding: 12px 20px;
      background: transparent;
      border: 1px solid rgba(0, 0, 0, 0.23);
      border-radius: 8px;
      font-size: 14px;
      font-weight: 500;
      color: rgba(0, 0, 0, 0.87);
      cursor: pointer;
      transition: background-color 0.2s, border-color 0.2s;
    }
    
    .brix-social-button:hover {
      background: rgba(0, 0, 0, 0.04);
      border-color: rgba(0, 0, 0, 0.87);
    }
    
    .brix-social-button:active {
      background: rgba(0, 0, 0, 0.08);
    }
    
    .brix-social-button:disabled {
      opacity: 0.38;
      cursor: not-allowed;
    }
    
    .brix-social-icon {
      width: 20px;
      height: 20px;
      flex-shrink: 0;
    }
    
    .brix-social-icon-emoji {
      font-size: 20px;
      line-height: 1;
      flex-shrink: 0;
    }
    
    .brix-social-icon-svg {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 20px;
      height: 20px;
      flex-shrink: 0;
    }
    
    .brix-social-icon-svg svg {
      width: 20px;
      height: 20px;
      display: block;
    }
    
    .brix-social-button span:last-child {
      flex: 1;
      text-align: center;
    }
    
    .brix-register-link {
      text-align: center;
      margin-top: 8px;
      font-size: 14px;
      color: rgba(0, 0, 0, 0.6);
    }
    
    .brix-register-link a,
    .brix-register-link button {
      color: ${primary};
      text-decoration: none;
      font-weight: 500;
      background: none;
      border: none;
      cursor: pointer;
      padding: 0;
      font-size: inherit;
    }
    
    .brix-register-link a:hover,
    .brix-register-link button:hover {
      text-decoration: underline;
    }
    
    .brix-login-footer {
      margin-top: 24px;
      text-align: center;
      padding-top: 16px;
      border-top: 1px solid rgba(0, 0, 0, 0.08);
    }
    
    .brix-login-footer p {
      margin: 0;
      font-size: 12px;
      color: rgba(0, 0, 0, 0.6);
    }
    
    @media (max-width: 480px) {
      .brix-login-page {
        padding: 16px;
      }
      
      .brix-login-container {
        padding: 32px 24px;
        border-radius: 12px;
      }
      
      .brix-login-title {
        font-size: 22px;
      }
      
      .brix-login-logo {
        width: 48px;
        height: 48px;
        border-radius: 10px;
      }
      
      .brix-login-logo-icon {
        font-size: 22px;
      }
    }
    
    /* ====== Dark Mode Support - MUI Dark Theme ====== */
    @media (prefers-color-scheme: dark) {
      .brix-login-page {
        background: #121212;
      }
      
      .brix-login-container {
        background: #1e1e1e;
        box-shadow: 
          0px 5px 5px -3px rgba(0,0,0,0.3),
          0px 8px 10px 1px rgba(0,0,0,0.21),
          0px 3px 14px 2px rgba(0,0,0,0.18);
      }
      
      .brix-login-title {
        color: rgba(255, 255, 255, 0.87);
      }
      
      .brix-login-subtitle {
        color: rgba(255, 255, 255, 0.6);
      }
      
      .brix-form-label {
        color: rgba(255, 255, 255, 0.87);
      }
      
      .brix-form-input {
        background: #1e1e1e;
        border-color: rgba(255, 255, 255, 0.23);
        color: rgba(255, 255, 255, 0.87);
      }
      
      .brix-form-input:hover {
        border-color: rgba(255, 255, 255, 0.87);
      }
      
      .brix-form-input::placeholder {
        color: rgba(255, 255, 255, 0.42);
      }
      
      .brix-checkbox-label {
        color: rgba(255, 255, 255, 0.87);
      }
      
      .brix-social-button {
        background: transparent;
        border-color: rgba(255, 255, 255, 0.23);
        color: rgba(255, 255, 255, 0.87);
      }
      
      .brix-social-button:hover {
        background: rgba(255, 255, 255, 0.08);
        border-color: rgba(255, 255, 255, 0.87);
      }
      
      .brix-social-divider-line {
        background: rgba(255, 255, 255, 0.12);
      }
      
      .brix-social-divider-text {
        color: rgba(255, 255, 255, 0.6);
      }
      
      .brix-register-link {
        color: rgba(255, 255, 255, 0.6);
      }
      
      .brix-login-footer {
        border-top-color: rgba(255, 255, 255, 0.08);
      }
      
      .brix-login-footer p {
        color: rgba(255, 255, 255, 0.6);
      }
      
      .brix-login-error {
        background: #2c1f1f;
        color: #f5c6cb;
        border-left-color: #ef5350;
      }
      
      .brix-login-button:disabled,
      .brix-submit-button:disabled {
        background: rgba(255, 255, 255, 0.12);
        color: rgba(255, 255, 255, 0.3);
      }
    }
  `;
}

/**
 * 
 * @param primaryColor -
 * @param secondaryColor -
 * @param tertiaryColor -
 */
export function injectLoginStyles(
  primaryColor?: string,
  secondaryColor?: string,
  tertiaryColor?: string
): void {
  const styleId = 'brix-login-styles';
  
  const existingStyle = document.getElementById(styleId);
  if (existingStyle) {
    existingStyle.remove();
  }
  
  const style = document.createElement('style');
  style.id = styleId;
  style.textContent = generateLoginStyles({
    primaryColor: primaryColor || DEFAULT_PRIMARY_COLOR,
    secondaryColor: secondaryColor || DEFAULT_SECONDARY_COLOR,
    tertiaryColor: tertiaryColor || DEFAULT_TERTIARY_COLOR,
  });
  
  document.head.appendChild(style);
}
