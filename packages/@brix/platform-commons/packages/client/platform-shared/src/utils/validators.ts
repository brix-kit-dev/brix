/**
 * @file validators.ts
 * @description 验证工具函数集
 * @module @brix/utils/validators
 * @version 3.0.0
 * 
 * 【模块说明】
 * 提供各种数据验证工具函数，包括基础格式验证、中国特有证件验证、密码强度检查等。
 * 验证函数均返回布尔值，不抛出异常，便于条件判断使用。
 * 
 * 【使用场景】
 * - 表单验证：邮箱、手机号、密码等
 * - 证件验证：身份证、统一社会信用代码、银行卡等
 * - 数据校验：URL、IP、JSON 格式等
 * - 类型检查：空值、数字、范围等
 * 
 * @license Apache-2.0
 */

// ============================================================
// 基础验证
// ============================================================

/**
 * 验证邮箱格式
 * 
 * 【功能说明】
 * 验证字符串是否为有效的邮箱地址格式。
 * 使用 RFC 5322 简化版正则表达式。
 * 
 * @param email 邮箱地址
 * @returns 是否为有效邮箱格式
 * 
 * @example
 * ```typescript
 * isValidEmail('user@example.com');     // true
 * isValidEmail('user.name@domain.cn');  // true
 * isValidEmail('invalid-email');        // false
 * isValidEmail('user@');                // false
 * isValidEmail('');                     // false
 * ```
 */
export function isValidEmail(email: string): boolean {
  if (!email || typeof email !== 'string') return false;
  // RFC 5322 简化版
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email.trim());
}

/**
 * 验证中国大陆手机号
 * 
 * 【功能说明】
 * 验证字符串是否为有效的中国大陆手机号码。
 * 支持目前所有运营商号段（13x、14x、15x、16x、17x、18x、19x）。
 * 
 * 【验证规则】
 * - 11位数字
 * - 以1开头
 * - 第二位为3-9
 * 
 * @param phone 手机号
 * @returns 是否为有效手机号
 * 
 * @example
 * ```typescript
 * isValidPhone('13800138000');  // true
 * isValidPhone('19912345678');  // true
 * isValidPhone('12345678901');  // false（第二位不符合）
 * isValidPhone('1380013800');   // false（位数不足）
 * isValidPhone('138 0013 8000'); // false（含空格）
 * ```
 */
export function isValidPhone(phone: string): boolean {
  if (!phone || typeof phone !== 'string') return false;
  // 中国大陆手机号：1开头，第二位3-9，共11位
  const phoneRegex = /^1[3-9]\d{9}$/;
  return phoneRegex.test(phone.trim());
}

/**
 * 验证 URL 格式
 * 
 * 【功能说明】
 * 验证字符串是否为有效的 HTTP/HTTPS URL。
 * 使用原生 URL API 进行解析验证。
 * 
 * @param url URL 字符串
 * @returns 是否为有效 URL
 * 
 * @example
 * ```typescript
 * isValidUrl('https://example.com');        // true
 * isValidUrl('http://example.com/path');    // true
 * isValidUrl('ftp://example.com');          // false（不支持 FTP）
 * isValidUrl('not-a-url');                  // false
 * isValidUrl('');                           // false
 * ```
 */
export function isValidUrl(url: string): boolean {
  if (!url || typeof url !== 'string') return false;
  try {
    const parsed = new URL(url);
    return ['http:', 'https:'].includes(parsed.protocol);
  } catch {
    return false;
  }
}

/**
 * 验证 IPv4 地址
 * 
 * 【功能说明】
 * 验证字符串是否为有效的 IPv4 地址格式。
 * 
 * 【验证规则】
 * - 四段数字，用点分隔
 * - 每段范围 0-255
 * - 不允许前导零（如 01.02.03.04）
 * 
 * @param ip IP 地址字符串
 * @returns 是否为有效 IPv4 地址
 * 
 * @example
 * ```typescript
 * isValidIPv4('192.168.1.1');   // true
 * isValidIPv4('0.0.0.0');       // true
 * isValidIPv4('255.255.255.255'); // true
 * isValidIPv4('256.1.1.1');     // false（超出范围）
 * isValidIPv4('01.02.03.04');   // false（前导零）
 * isValidIPv4('192.168.1');     // false（不完整）
 * ```
 */
export function isValidIPv4(ip: string): boolean {
  if (!ip || typeof ip !== 'string') return false;
  const parts = ip.split('.');
  if (parts.length !== 4) return false;
  return parts.every((part) => {
    const num = parseInt(part, 10);
    return num >= 0 && num <= 255 && part === num.toString();
  });
}

/**
 * 验证 JSON 字符串
 * 
 * 【功能说明】
 * 验证字符串是否为有效的 JSON 格式。
 * 
 * @param str 待验证字符串
 * @returns 是否为有效 JSON
 * 
 * @example
 * ```typescript
 * isValidJSON('{"name":"test"}');  // true
 * isValidJSON('[1, 2, 3]');        // true
 * isValidJSON('true');             // true
 * isValidJSON('{invalid}');        // false
 * isValidJSON('');                 // false
 * ```
 */
export function isValidJSON(str: string): boolean {
  try {
    JSON.parse(str);
    return true;
  } catch {
    return false;
  }
}

// ============================================================
// 中国特有验证
// ============================================================

/**
 * 验证中国大陆身份证号
 * 
 * 【功能说明】
 * 验证字符串是否为有效的中国大陆居民身份证号。
 * 支持18位（新版）和15位（老版）两种格式。
 * 18位身份证会进行校验码验证。
 * 
 * 【18位身份证结构】
 * - 前6位：地区码
 * - 7-14位：出生日期（YYYYMMDD）
 * - 15-17位：顺序码（第17位奇数为男，偶数为女）
 * - 第18位：校验码（0-9 或 X）
 * 
 * @param idCard 身份证号
 * @returns 是否为有效身份证号
 * 
 * @example
 * ```typescript
 * isValidIdCard('110101199001011234');  // 需验证校验码
 * isValidIdCard('110101900101123');     // 15位老版身份证
 * isValidIdCard('12345678901234567');   // false（校验码错误）
 * isValidIdCard('');                    // false
 * ```
 */
export function isValidIdCard(idCard: string): boolean {
  if (!idCard || typeof idCard !== 'string') return false;
  
  const id = idCard.trim().toUpperCase();
  
  // 18位身份证
  if (id.length === 18) {
    // 前17位必须是数字
    if (!/^\d{17}[\dX]$/.test(id)) return false;
    
    // 校验码验证
    const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
    const checkCodes = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'];
    
    let sum = 0;
    for (let i = 0; i < 17; i++) {
      sum += parseInt(id[i], 10) * weights[i];
    }
    
    return id[17] === checkCodes[sum % 11];
  }
  
  // 15位身份证（老版）
  if (id.length === 15) {
    return /^\d{15}$/.test(id);
  }
  
  return false;
}

/**
 * 验证中国大陆统一社会信用代码
 * 
 * 【功能说明】
 * 验证字符串是否为有效的统一社会信用代码（企业信用代码）。
 * 
 * 【代码结构】
 * - 第1位：登记管理部门代码（1位）
 * - 第2位：机构类别代码（1位）
 * - 第3-8位：登记管理机关行政区划码（6位数字）
 * - 第9-17位：主体标识码（组织机构代码）
 * - 第18位：校验码
 * 
 * @param code 统一社会信用代码
 * @returns 是否为有效代码
 * 
 * @example
 * ```typescript
 * isValidUnifiedCreditCode('91110000MA0ABCDE12');  // 需验证格式
 * isValidUnifiedCreditCode('');                    // false
 * ```
 */
export function isValidUnifiedCreditCode(code: string): boolean {
  if (!code || typeof code !== 'string') return false;
  // 18位，由数字和大写字母组成（不含I、O、Z、S、V）
  const regex = /^[0-9A-HJ-NPQRTUWXY]{2}\d{6}[0-9A-HJ-NPQRTUWXY]{10}$/;
  return regex.test(code.trim().toUpperCase());
}

/**
 * 验证中国大陆银行卡号（Luhn算法）
 * 
 * 【功能说明】
 * 验证字符串是否为有效的银行卡号。
 * 使用 Luhn 算法进行校验。
 * 
 * 【Luhn 算法】
 * 1. 从右向左，偶数位乘2，若结果大于9则减9
 * 2. 所有位数求和
 * 3. 能被10整除则有效
 * 
 * @param cardNumber 银行卡号
 * @returns 是否为有效银行卡号
 * 
 * @example
 * ```typescript
 * isValidBankCard('6222020200010001234');  // 需验证Luhn算法
 * isValidBankCard('1234 5678 9012 3456');  // 支持带空格
 * isValidBankCard('123');                  // false（位数不足）
 * ```
 */
export function isValidBankCard(cardNumber: string): boolean {
  if (!cardNumber || typeof cardNumber !== 'string') return false;
  
  const cleaned = cardNumber.replace(/\s/g, '');
  if (!/^\d{13,19}$/.test(cleaned)) return false;
  
  // Luhn 算法
  let sum = 0;
  let isEven = false;
  
  for (let i = cleaned.length - 1; i >= 0; i--) {
    let digit = parseInt(cleaned[i], 10);
    
    if (isEven) {
      digit *= 2;
      if (digit > 9) digit -= 9;
    }
    
    sum += digit;
    isEven = !isEven;
  }
  
  return sum % 10 === 0;
}

// ============================================================
// 密码强度验证
// ============================================================

/**
 * 密码强度等级
 */
export type PasswordStrength = 'weak' | 'medium' | 'strong' | 'very-strong';

/**
 * 密码强度检查结果
 */
export interface PasswordStrengthResult {
  /** 强度等级 */
  strength: PasswordStrength;
  /** 强度分数（0-10） */
  score: number;
  /** 改进建议列表 */
  suggestions: string[];
}

/**
 * 检查密码强度
 * 
 * 【功能说明】
 * 综合评估密码强度，返回强度等级、分数和改进建议。
 * 
 * 【评分规则】
 * - 长度：8位及以上 +1，12位及以上 +1，16位及以上 +1
 * - 小写字母：+1
 * - 大写字母：+1
 * - 数字：+1
 * - 特殊字符：+2
 * - 常见模式：-2
 * 
 * 【强度等级】
 * - weak：分数 <= 2
 * - medium：分数 3-4
 * - strong：分数 5-6
 * - very-strong：分数 >= 7
 * 
 * @param password 密码
 * @returns 强度检查结果
 * 
 * @example
 * ```typescript
 * checkPasswordStrength('123456');
 * // { strength: 'weak', score: 0, suggestions: ['密码长度至少8位', ...] }
 * 
 * checkPasswordStrength('MyP@ssw0rd');
 * // { strength: 'strong', score: 7, suggestions: [] }
 * 
 * checkPasswordStrength('VeryStr0ng!P@ssw0rd');
 * // { strength: 'very-strong', score: 9, suggestions: [] }
 * ```
 */
export function checkPasswordStrength(password: string): PasswordStrengthResult {
  const suggestions: string[] = [];
  let score = 0;

  if (!password) {
    return { strength: 'weak', score: 0, suggestions: ['请输入密码'] };
  }

  // 长度检查
  if (password.length >= 8) score += 1;
  else suggestions.push('密码长度至少8位');
  
  if (password.length >= 12) score += 1;
  if (password.length >= 16) score += 1;

  // 包含小写字母
  if (/[a-z]/.test(password)) score += 1;
  else suggestions.push('建议包含小写字母');

  // 包含大写字母
  if (/[A-Z]/.test(password)) score += 1;
  else suggestions.push('建议包含大写字母');

  // 包含数字
  if (/\d/.test(password)) score += 1;
  else suggestions.push('建议包含数字');

  // 包含特殊字符
  if (/[!@#$%^&*(),.?":{}|<>]/.test(password)) score += 2;
  else suggestions.push('建议包含特殊字符');

  // 避免常见密码模式
  const commonPatterns = [
    /^123/,
    /password/i,
    /qwerty/i,
    /^abc/i,
  ];
  if (commonPatterns.some((p) => p.test(password))) {
    score -= 2;
    suggestions.push('避免使用常见密码模式');
  }

  // 确定强度等级
  let strength: PasswordStrength;
  if (score <= 2) strength = 'weak';
  else if (score <= 4) strength = 'medium';
  else if (score <= 6) strength = 'strong';
  else strength = 'very-strong';

  return { strength, score: Math.max(0, score), suggestions };
}

// ============================================================
// 类型检查
// ============================================================

/**
 * 检查是否为空值
 * 
 * 【功能说明】
 * 检查值是否为"空"。支持多种类型：
 * - null/undefined：空
 * - 字符串：空字符串或纯空白为空
 * - 数组：空数组为空
 * - 对象：空对象为空
 * 
 * @param value 待检查的值
 * @returns 是否为空
 * 
 * @example
 * ```typescript
 * isEmpty(null);       // true
 * isEmpty(undefined);  // true
 * isEmpty('');         // true
 * isEmpty('  ');       // true
 * isEmpty([]);         // true
 * isEmpty({});         // true
 * isEmpty(0);          // false
 * isEmpty(false);      // false
 * isEmpty('hello');    // false
 * ```
 */
export function isEmpty(value: unknown): boolean {
  if (value === null || value === undefined) return true;
  if (typeof value === 'string') return value.trim() === '';
  if (Array.isArray(value)) return value.length === 0;
  if (typeof value === 'object') return Object.keys(value).length === 0;
  return false;
}

/**
 * 检查是否为数字
 * 
 * 【功能说明】
 * 检查值是否为有效数字（包括数字类型和可解析的数字字符串）。
 * 排除 NaN 和 Infinity。
 * 
 * @param value 待检查的值
 * @returns 是否为有效数字
 * 
 * @example
 * ```typescript
 * isNumeric(123);      // true
 * isNumeric('123');    // true
 * isNumeric('12.34');  // true
 * isNumeric('-100');   // true
 * isNumeric(NaN);      // false
 * isNumeric(Infinity); // false
 * isNumeric('abc');    // false
 * isNumeric('');       // false
 * ```
 */
export function isNumeric(value: unknown): boolean {
  if (typeof value === 'number') return !isNaN(value) && isFinite(value);
  if (typeof value === 'string') return !isNaN(parseFloat(value)) && isFinite(parseFloat(value));
  return false;
}

/**
 * 检查是否为整数
 * 
 * @param value 待检查的值
 * @returns 是否为整数
 * 
 * @example
 * ```typescript
 * isInteger(123);    // true
 * isInteger(-100);   // true
 * isInteger(12.34);  // false
 * isInteger('123');  // false（需为数字类型）
 * ```
 */
export function isInteger(value: unknown): boolean {
  return Number.isInteger(value);
}

/**
 * 检查是否为正数
 * 
 * @param value 待检查的数字
 * @returns 是否为正数
 * 
 * @example
 * ```typescript
 * isPositive(1);     // true
 * isPositive(0.001); // true
 * isPositive(0);     // false
 * isPositive(-1);    // false
 * ```
 */
export function isPositive(value: number): boolean {
  return typeof value === 'number' && value > 0;
}

/**
 * 检查是否在范围内
 * 
 * @param value 待检查的数字
 * @param min 最小值（包含）
 * @param max 最大值（包含）
 * @returns 是否在范围内
 * 
 * @example
 * ```typescript
 * isInRange(5, 1, 10);   // true
 * isInRange(1, 1, 10);   // true（包含边界）
 * isInRange(10, 1, 10);  // true（包含边界）
 * isInRange(0, 1, 10);   // false
 * isInRange(11, 1, 10);  // false
 * ```
 */
export function isInRange(value: number, min: number, max: number): boolean {
  return typeof value === 'number' && value >= min && value <= max;
}
