/**
 * @file LoginForm 类型定义
 * @description 登录表单组件的类型定
 * @module @brix/platform-auth-web/components/LoginForm/types
 * @version 3.0.0
 */

import type { ReactNode, CSSProperties } from 'react';

/**
 * 登录表单数据
 */
export interface LoginFormData {
  /**
   * 用户邮箱/手机
   */
  username: string;
  
  /**
   * 密码
   */
  password: string;
  
  /**
   * 记住
   */
  rememberMe: boolean;
}

/**
 * 登录结果
 */
export interface LoginFormResult {
  /**
   * 是否成功
   */
  success: boolean;
  
  /**
   * 错误消息
   */
  error?: string;
  
  /**
   * 重定向地址
   */
  redirectTo?: string;
}

/**
 * 社交登录提供商配
 */
export interface SocialProvider {
  /**
   * 提供ID
   */
  id: string;
  
  /**
   * 显示名称
   */
  name: string;
  
  /**
   * 图标（可以是 URL React 节点
   */
  icon?: string | ReactNode;
  
  /**
   * 背景
   */
  backgroundColor?: string;
  
  /**
   * 文字颜色
   */
  textColor?: string;
}

/**
 * 品牌配置
 */
export interface LoginFormBranding {
  /**
   * Logo URL React 节点
   */
  logo?: string | ReactNode;
  
  /**
   * 应用名称
   */
  appName?: string;
  
  /**
   * 欢迎
   */
  welcomeMessage?: string;
  
  /**
   * 副标
   */
  subtitle?: string;
  
  /**
   * 主题
   */
  primaryColor?: string;
  
  /**
   * 第二颜色（背景辅助）
   */
  secondaryColor?: string;
  
  /**
   * 第三颜色（纯净底色
   */
  tertiaryColor?: string;
  
  /**
   * 渐变色（用于背景
   */
  gradientColors?: [string, string];
  
  /**
   * 页脚文案
   */
  footerText?: string;
}

/**
 * 表单文案配置
 */
export interface LoginFormLabels {
  /**
   * 用户邮箱/手机号标
   */
  usernameLabel?: string;
  
  /**
   * 用户邮箱/手机号占位符
   */
  usernamePlaceholder?: string;
  
  /**
   * 密码标签
   */
  passwordLabel?: string;
  
  /**
   * 密码占位
   */
  passwordPlaceholder?: string;
  
  /**
   * 记住我文
   */
  rememberMeLabel?: string;
  
  /**
   * 忘记密码文案
   */
  forgotPasswordLabel?: string;
  
  /**
   * 登录按钮文案
   */
  submitLabel?: string;
  
  /**
   * 加载中文
   */
  loadingLabel?: string;
  
  /**
   * 社交登录分隔文案
   */
  socialLoginDivider?: string;
  
  /**
   * 注册链接前缀文案
   */
  registerPrefix?: string;
  
  /**
   * 注册链接文案
   */
  registerLabel?: string;
}

/**
 * 功能开关配
 */
export interface LoginFormFeatures {
  /**
   * 是否显示记住
   * @default true
   */
  showRememberMe?: boolean;
  
  /**
   * 是否显示忘记密码
   * @default true
   */
  showForgotPassword?: boolean;
  
  /**
   * 是否启用社交登录
   * @default false
   */
  enableSocialLogin?: boolean;
  
  /**
   * 是否自动聚焦用户名输入框
   * @default true
   */
  autoFocus?: boolean;
  
  /**
   * 是否显示注册链接
   * @default false
   */
  showRegisterLink?: boolean;
}

/**
 * LoginForm 组件 Props
 */
export interface LoginFormProps {
  /**
   * 品牌配置
   */
  branding?: LoginFormBranding;
  
  /**
   * 文案配置
   */
  labels?: LoginFormLabels;
  
  /**
   * 功能开
   */
  features?: LoginFormFeatures;
  
  /**
   * 社交登录提供商列
   */
  socialProviders?: SocialProvider[];
  
  /**
   * 登录处理函数
   * 
   * Host 提供，处理实际的登录逻辑
   */
  onLogin: (data: LoginFormData) => Promise<LoginFormResult>;
  
  /**
   * 登录成功回调
   */
  onLoginSuccess?: (result: LoginFormResult) => void;
  
  /**
   * 登录失败回调
   */
  onLoginError?: (error: string) => void;
  
  /**
   * 忘记密码回调
   */
  onForgotPassword?: () => void;
  
  /**
   * 社交登录回调
   */
  onSocialLogin?: (providerId: string) => void;
  
  /**
   * 注册回调
   */
  onRegister?: () => void;
  
  /**
   * 容器样式
   */
  containerStyle?: CSSProperties;
  
  /**
   * 容器类名
   */
  containerClassName?: string;
  
  /**
   * 是否显示页面容器（包含背景）
   * @default true
   */
  showPageContainer?: boolean;
  
  /**
   * 初始
   */
  initialValues?: Partial<LoginFormData>;
}
