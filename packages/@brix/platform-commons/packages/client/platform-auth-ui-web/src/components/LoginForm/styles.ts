/**
 * @file LoginForm 默认样式
 * @description 登录表单组件的默CSS-in-JS 样式 - MUI 标准配色风格
 * @module @brix/platform-auth-web/components/LoginForm/styles
 * @version 3.0.1
 * 
 * 设计特点
 * - 纯净简洁的白色背景（无渐变
 * - MUI 标准配色主题
 * - 柔和的毛玻璃卡片
 * - 圆润的边
 * - 微妙的层次感
 */

/**
 * MUI 标准主题- Primary Blue
 */
export const DEFAULT_PRIMARY_COLOR = '#1976d2';

/**
 * MUI 标准背景色- 浅灰
 */
export const DEFAULT_SECONDARY_COLOR = '#f5f5f5';

/**
 * MUI 标准背景色- 白色
 */
export const DEFAULT_TERTIARY_COLOR = '#ffffff';

/**
 * 默认渐变- 保持向后兼容（但实际不使用渐变）
 */
export const DEFAULT_GRADIENT: [string, string] = [DEFAULT_TERTIARY_COLOR, DEFAULT_SECONDARY_COLOR];

/**
 * 样式选项接口
 */
export interface LoginStyleOptions {
  primaryColor?: string;
  secondaryColor?: string;
  tertiaryColor?: string;
  gradientColors?: [string, string];
}

/**
 * 生成登录页面样式（MUI 标准配色风格
 */
export function generateLoginStyles(options: LoginStyleOptions): string {
  const primary = options.primaryColor || DEFAULT_PRIMARY_COLOR;
  const secondary = options.secondaryColor || DEFAULT_SECONDARY_COLOR;
  const tertiary = options.tertiaryColor || DEFAULT_TERTIARY_COLOR;
  // gradientColors 保留向后兼容，但不再使用渐变
  
  return `
    /* ====== 全局重置 - 禁止滚动条====== */
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
    
    /* ====== MUI 风格纯净背景 ====== */
    .shinwa-login-page {
      height: 100vh;
      width: 100vw;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
      box-sizing: border-box;
      overflow: hidden;
      position: relative;
      
      /* 纯净背景色- 无渐变*/
      background: ${secondary};
    }
    
    /* 移除背景装饰元素- 保持简洁*/
    .shinwa-login-page::before,
    .shinwa-login-page::after {
      display: none;
    }
    
    /* ====== MUI 风格卡片 ====== */
    .shinwa-login-container {
      width: 100%;
      max-width: 400px;
      position: relative;
      z-index: 1;
      
      /* MUI Paper 风格 */
      background: ${tertiary};
      
      /* 圆润边角 */
      border-radius: 16px;
      
      /* MUI 标准阴影 (elevation 8) */
      box-shadow: 
        0px 5px 5px -3px rgba(0,0,0,0.06),
        0px 8px 10px 1px rgba(0,0,0,0.042),
        0px 3px 14px 2px rgba(0,0,0,0.036);
      
      padding: 40px 36px;
    }
    
    /* ====== 注册页面扩展样式 ====== */
    .shinwa-register-container {
      max-width: 520px;
    }
    
    /* 响应式两列布局 */
    .shinwa-form-row-responsive {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }
    
    @media (max-width: 560px) {
      .shinwa-form-row-responsive {
        grid-template-columns: 1fr;
      }
      
      .shinwa-register-container {
        max-width: 400px;
      }
    }
    
    /* ====== 头部区域 ====== */
    .shinwa-login-header {
      text-align: center;
      margin-bottom: 32px;
    }
    
    /* Logo 容器 - MUI 风格 */
    .shinwa-login-logo {
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
    
    .shinwa-login-logo-icon {
      font-size: 28px;
      color: white;
    }
    
    .shinwa-login-logo-image {
      max-width: 36px;
      max-height: 36px;
      object-fit: contain;
    }
    
    /* 标题 - MUI Typography */
    .shinwa-login-title {
      margin: 0 0 8px;
      font-size: 24px;
      font-weight: 500;
      color: rgba(0, 0, 0, 0.87);
      letter-spacing: 0;
    }
    
    .shinwa-login-subtitle {
      margin: 0;
      font-size: 14px;
      color: rgba(0, 0, 0, 0.6);
      line-height: 1.5;
      font-weight: 400;
    }
    
    /* ====== 表单区域 ====== */
    .shinwa-login-form {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }
    
    /* 错误提示 - MUI Alert 风格 */
    .shinwa-login-error {
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
    
    /* 表单区*/
    .shinwa-form-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    
    .shinwa-form-label {
      font-size: 14px;
      font-weight: 500;
      color: rgba(0, 0, 0, 0.87);
      margin-left: 2px;
    }
    
    /* 输入框- MUI TextField Outlined 风格 */
    .shinwa-form-input {
      padding: 14px 14px;
      background: ${tertiary};
      border: 1px solid rgba(0, 0, 0, 0.23);
      border-radius: 8px;
      font-size: 16px;
      color: rgba(0, 0, 0, 0.87);
      transition: border-color 0.2s, box-shadow 0.2s;
    }
    
    .shinwa-form-input:hover {
      border-color: rgba(0, 0, 0, 0.87);
    }
    
    .shinwa-form-input:focus {
      outline: none;
      border-color: ${primary};
      box-shadow: 0 0 0 2px ${primary}20;
    }
    
    .shinwa-form-input:disabled {
      background: rgba(0, 0, 0, 0.04);
      cursor: not-allowed;
      color: rgba(0, 0, 0, 0.38);
    }
    
    .shinwa-form-input::placeholder {
      color: rgba(0, 0, 0, 0.42);
    }
    
    /* 输入框错误状态*/
    .shinwa-form-input.shinwa-input-error {
      border-color: #EF4444;
    }
    
    .shinwa-form-input.shinwa-input-error:focus {
      border-color: #EF4444;
      box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.1);
    }
    
    /* 字段错误提示 */
    .shinwa-field-error {
      display: block;
      font-size: 13px;
      color: #EF4444;
      margin-top: 4px;
    }
    
    /* 记住我& 忘记密码行*/
    .shinwa-form-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    
    .shinwa-checkbox-label {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      font-size: 14px;
      color: rgba(0, 0, 0, 0.87);
      cursor: pointer;
    }
    
    .shinwa-checkbox-label input[type="checkbox"] {
      width: 18px;
      height: 18px;
      accent-color: ${primary};
      cursor: pointer;
      border-radius: 4px;
      flex-shrink: 0;
      margin-top: 2px;
    }
    
    .shinwa-checkbox-label a {
      color: ${primary};
      text-decoration: none;
      margin: 0 2px;
    }
    
    .shinwa-checkbox-label a:hover {
      text-decoration: underline;
    }
    
    .shinwa-forgot-link {
      font-size: 14px;
      color: ${primary};
      text-decoration: none;
      background: none;
      border: none;
      cursor: pointer;
      padding: 0;
      font-weight: 500;
    }
    
    .shinwa-forgot-link:hover {
      text-decoration: underline;
    }
    
    /* ====== 登录/提交按钮 - MUI Button Contained 风格 ====== */
    .shinwa-login-button,
    .shinwa-submit-button {
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
    
    .shinwa-login-button:hover:not(:disabled),
    .shinwa-submit-button:hover:not(:disabled) {
      background: #1565c0;
      box-shadow: 0 4px 8px -2px rgba(0,0,0,0.2), 
                  0 8px 10px 1px rgba(0,0,0,0.14), 
                  0 3px 14px 2px rgba(0,0,0,0.12);
    }
    
    .shinwa-login-button:active:not(:disabled),
    .shinwa-submit-button:active:not(:disabled) {
      background: #0d47a1;
    }
    
    .shinwa-login-button:disabled,
    .shinwa-submit-button:disabled {
      background: rgba(0, 0, 0, 0.12);
      color: rgba(0, 0, 0, 0.26);
      cursor: not-allowed;
      box-shadow: none;
    }
    
    /* 加载动画 */
    .shinwa-loading-spinner {
      display: inline-block;
      width: 16px;
      height: 16px;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: white;
      border-radius: 50%;
      animation: shinwa-spin 1s linear infinite;
    }
    
    @keyframes shinwa-spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }
    
    /* ====== 社交登录分隔线====== */
    .shinwa-social-divider {
      display: flex;
      align-items: center;
      gap: 16px;
      margin: 4px 0;
    }
    
    .shinwa-social-divider-line {
      flex: 1;
      height: 1px;
      background: rgba(0, 0, 0, 0.12);
    }
    
    .shinwa-social-divider-text {
      font-size: 13px;
      color: rgba(0, 0, 0, 0.6);
      white-space: nowrap;
      font-weight: 400;
    }
    
    /* ====== 社交登录按钮 - MUI Button Outlined 风格 ====== */
    .shinwa-social-buttons {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    
    .shinwa-social-button {
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
    
    .shinwa-social-button:hover {
      background: rgba(0, 0, 0, 0.04);
      border-color: rgba(0, 0, 0, 0.87);
    }
    
    .shinwa-social-button:active {
      background: rgba(0, 0, 0, 0.08);
    }
    
    .shinwa-social-button:disabled {
      opacity: 0.38;
      cursor: not-allowed;
    }
    
    .shinwa-social-icon {
      width: 20px;
      height: 20px;
      flex-shrink: 0;
    }
    
    .shinwa-social-icon-emoji {
      font-size: 20px;
      line-height: 1;
      flex-shrink: 0;
    }
    
    .shinwa-social-icon-svg {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 20px;
      height: 20px;
      flex-shrink: 0;
    }
    
    .shinwa-social-icon-svg svg {
      width: 20px;
      height: 20px;
      display: block;
    }
    
    .shinwa-social-button span:last-child {
      flex: 1;
      text-align: center;
    }
    
    /* ====== 注册链接区域 ====== */
    .shinwa-register-link {
      text-align: center;
      margin-top: 8px;
      font-size: 14px;
      color: rgba(0, 0, 0, 0.6);
    }
    
    .shinwa-register-link a,
    .shinwa-register-link button {
      color: ${primary};
      text-decoration: none;
      font-weight: 500;
      background: none;
      border: none;
      cursor: pointer;
      padding: 0;
      font-size: inherit;
    }
    
    .shinwa-register-link a:hover,
    .shinwa-register-link button:hover {
      text-decoration: underline;
    }
    
    /* ====== 页脚 ====== */
    .shinwa-login-footer {
      margin-top: 24px;
      text-align: center;
      padding-top: 16px;
      border-top: 1px solid rgba(0, 0, 0, 0.08);
    }
    
    .shinwa-login-footer p {
      margin: 0;
      font-size: 12px;
      color: rgba(0, 0, 0, 0.6);
    }
    
    /* ====== 响应式====== */
    @media (max-width: 480px) {
      .shinwa-login-page {
        padding: 16px;
      }
      
      .shinwa-login-container {
        padding: 32px 24px;
        border-radius: 12px;
      }
      
      .shinwa-login-title {
        font-size: 22px;
      }
      
      .shinwa-login-logo {
        width: 48px;
        height: 48px;
        border-radius: 10px;
      }
      
      .shinwa-login-logo-icon {
        font-size: 22px;
      }
    }
    
    /* ====== 深色模式支持 - MUI Dark Theme ====== */
    @media (prefers-color-scheme: dark) {
      .shinwa-login-page {
        background: #121212;
      }
      
      .shinwa-login-container {
        background: #1e1e1e;
        box-shadow: 
          0px 5px 5px -3px rgba(0,0,0,0.3),
          0px 8px 10px 1px rgba(0,0,0,0.21),
          0px 3px 14px 2px rgba(0,0,0,0.18);
      }
      
      .shinwa-login-title {
        color: rgba(255, 255, 255, 0.87);
      }
      
      .shinwa-login-subtitle {
        color: rgba(255, 255, 255, 0.6);
      }
      
      .shinwa-form-label {
        color: rgba(255, 255, 255, 0.87);
      }
      
      .shinwa-form-input {
        background: #1e1e1e;
        border-color: rgba(255, 255, 255, 0.23);
        color: rgba(255, 255, 255, 0.87);
      }
      
      .shinwa-form-input:hover {
        border-color: rgba(255, 255, 255, 0.87);
      }
      
      .shinwa-form-input::placeholder {
        color: rgba(255, 255, 255, 0.42);
      }
      
      .shinwa-checkbox-label {
        color: rgba(255, 255, 255, 0.87);
      }
      
      .shinwa-social-button {
        background: transparent;
        border-color: rgba(255, 255, 255, 0.23);
        color: rgba(255, 255, 255, 0.87);
      }
      
      .shinwa-social-button:hover {
        background: rgba(255, 255, 255, 0.08);
        border-color: rgba(255, 255, 255, 0.87);
      }
      
      .shinwa-social-divider-line {
        background: rgba(255, 255, 255, 0.12);
      }
      
      .shinwa-social-divider-text {
        color: rgba(255, 255, 255, 0.6);
      }
      
      .shinwa-register-link {
        color: rgba(255, 255, 255, 0.6);
      }
      
      .shinwa-login-footer {
        border-top-color: rgba(255, 255, 255, 0.08);
      }
      
      .shinwa-login-footer p {
        color: rgba(255, 255, 255, 0.6);
      }
      
      .shinwa-login-error {
        background: #2c1f1f;
        color: #f5c6cb;
        border-left-color: #ef5350;
      }
      
      .shinwa-login-button:disabled,
      .shinwa-submit-button:disabled {
        background: rgba(255, 255, 255, 0.12);
        color: rgba(255, 255, 255, 0.3);
      }
    }
  `;
}

/**
 * 注入登录页面样式DOM
 * 
 * @param primaryColor - 主题
 * @param secondaryColor - 第二颜色
 * @param tertiaryColor - 第三颜色
 */
export function injectLoginStyles(
  primaryColor?: string,
  secondaryColor?: string,
  tertiaryColor?: string
): void {
  const styleId = 'shinwa-login-styles';
  
  // 如果已存在则先移
  const existingStyle = document.getElementById(styleId);
  if (existingStyle) {
    existingStyle.remove();
  }
  
  // 创建新的 style 元素
  const style = document.createElement('style');
  style.id = styleId;
  style.textContent = generateLoginStyles({
    primaryColor: primaryColor || DEFAULT_PRIMARY_COLOR,
    secondaryColor: secondaryColor || DEFAULT_SECONDARY_COLOR,
    tertiaryColor: tertiaryColor || DEFAULT_TERTIARY_COLOR,
  });
  
  document.head.appendChild(style);
}
