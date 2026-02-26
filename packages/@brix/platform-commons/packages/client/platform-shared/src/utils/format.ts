/**
 * @file format.ts
 * @description 格式化工具函数集
 * @module @brix/utils/format
 * @version 3.0.0
 * 
 * 【模块说明】
 * 提供各种数据格式化工具，包括文件大小、日期时间、数字、货币、字符串等。
 * 这些函数遵循中国大陆的常用格式习惯，同时支持国际化配置。
 * 
 * 【使用场景】
 * - 文件大小：上传下载、存储空间显示
 * - 日期格式化：时间显示、相对时间、倒计时
 * - 数字格式化：金额、百分比、统计数据
 * - 字符串处理：截断、大小写转换
 * 
 * @license Apache-2.0
 */

// ============================================================
// 文件大小格式化
// ============================================================

/**
 * 格式化文件大小
 * 
 * 【功能说明】
 * 将字节数转换为人类可读的文件大小格式。
 * 自动选择合适的单位（B、KB、MB、GB 等）。
 * 
 * @param bytes 字节数
 * @param decimals 小数位数（默认 2）
 * @returns 格式化后的字符串
 * 
 * @example
 * ```typescript
 * formatFileSize(0);          // '0 B'
 * formatFileSize(1024);       // '1 KB'
 * formatFileSize(1048576);    // '1 MB'
 * formatFileSize(1073741824); // '1 GB'
 * formatFileSize(1536, 1);    // '1.5 KB'
 * ```
 */
export function formatFileSize(bytes: number, decimals: number = 2): string {
  if (bytes === 0) return '0 B';
  if (bytes < 0) return '-' + formatFileSize(-bytes, decimals);

  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const index = Math.min(i, sizes.length - 1);

  return `${parseFloat((bytes / Math.pow(k, index)).toFixed(decimals))} ${sizes[index]}`;
}

/**
 * 解析文件大小字符串为字节数
 * 
 * 【功能说明】
 * 将文件大小字符串（如 '1 MB', '500KB'）解析为字节数。
 * 支持各种常见格式，大小写不敏感。
 * 
 * @param sizeStr 文件大小字符串
 * @returns 字节数（解析失败返回 0）
 * 
 * @example
 * ```typescript
 * parseFileSize('1 KB');   // 1024
 * parseFileSize('1.5MB');  // 1572864
 * parseFileSize('2 GB');   // 2147483648
 * parseFileSize('invalid'); // 0
 * ```
 */
export function parseFileSize(sizeStr: string): number {
  const units: Record<string, number> = {
    B: 1,
    KB: 1024,
    MB: 1024 ** 2,
    GB: 1024 ** 3,
    TB: 1024 ** 4,
    PB: 1024 ** 5,
  };

  const match = sizeStr.trim().toUpperCase().match(/^([\d.]+)\s*([A-Z]+)$/);
  if (!match) return 0;

  const [, value, unit] = match;
  return parseFloat(value) * (units[unit] || 1);
}

// ============================================================
// 日期格式化
// ============================================================

/**
 * 格式化日期
 * 
 * 【功能说明】
 * 将日期格式化为指定格式的字符串。
 * 支持常用的占位符，如 YYYY、MM、DD、HH、mm、ss 等。
 * 
 * 【支持的占位符】
 * - YYYY: 四位年份
 * - YY: 两位年份
 * - MM: 两位月份（01-12）
 * - M: 月份（1-12）
 * - DD: 两位日期（01-31）
 * - D: 日期（1-31）
 * - HH: 两位小时（00-23）
 * - H: 小时（0-23）
 * - mm: 两位分钟（00-59）
 * - m: 分钟（0-59）
 * - ss: 两位秒（00-59）
 * - s: 秒（0-59）
 * - SSS: 毫秒
 * 
 * @param date 日期（Date 对象、时间戳或日期字符串）
 * @param format 格式字符串（默认 'YYYY-MM-DD HH:mm:ss'）
 * @returns 格式化后的日期字符串
 * 
 * @example
 * ```typescript
 * formatDate(new Date(), 'YYYY-MM-DD');          // '2024-01-01'
 * formatDate(Date.now(), 'YYYY-MM-DD HH:mm:ss'); // '2024-01-01 12:00:00'
 * formatDate(new Date(), 'YYYY年MM月DD日');      // '2024年01月01日'
 * formatDate(new Date(), 'MM/DD/YYYY');          // '01/01/2024'
 * ```
 */
export function formatDate(
  date: Date | number | string,
  format: string = 'YYYY-MM-DD HH:mm:ss'
): string {
  const d = new Date(date);

  if (isNaN(d.getTime())) {
    return 'Invalid Date';
  }

  const replacements: Record<string, string> = {
    YYYY: d.getFullYear().toString(),
    YY: d.getFullYear().toString().slice(-2),
    MM: (d.getMonth() + 1).toString().padStart(2, '0'),
    M: (d.getMonth() + 1).toString(),
    DD: d.getDate().toString().padStart(2, '0'),
    D: d.getDate().toString(),
    HH: d.getHours().toString().padStart(2, '0'),
    H: d.getHours().toString(),
    mm: d.getMinutes().toString().padStart(2, '0'),
    m: d.getMinutes().toString(),
    ss: d.getSeconds().toString().padStart(2, '0'),
    s: d.getSeconds().toString(),
    SSS: d.getMilliseconds().toString().padStart(3, '0'),
  };

  let result = format;
  // 按长度降序排列，避免 MM 被 M 替换
  const keys = Object.keys(replacements).sort((a, b) => b.length - a.length);
  for (const key of keys) {
    result = result.replace(new RegExp(key, 'g'), replacements[key]);
  }

  return result;
}

/**
 * 格式化相对时间
 * 
 * 【功能说明】
 * 将日期转换为相对于当前时间的描述，如"3分钟前"、"2天后"。
 * 使用中文描述，适合中国用户的阅读习惯。
 * 
 * @param date 日期（Date 对象、时间戳或日期字符串）
 * @param now 当前时间戳（默认 Date.now()）
 * @returns 相对时间字符串
 * 
 * @example
 * ```typescript
 * formatRelativeTime(Date.now() - 30000);     // '30秒前'
 * formatRelativeTime(Date.now() - 3600000);   // '1小时前'
 * formatRelativeTime(Date.now() - 86400000);  // '1天前'
 * formatRelativeTime(Date.now() + 86400000);  // '1天后'
 * formatRelativeTime(Date.now() - 5000);      // '刚刚'
 * ```
 */
export function formatRelativeTime(
  date: Date | number | string,
  now: number = Date.now()
): string {
  const d = new Date(date);
  const diff = now - d.getTime();
  const absDiff = Math.abs(diff);
  const suffix = diff > 0 ? '前' : '后';

  const seconds = Math.floor(absDiff / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);
  const months = Math.floor(days / 30);
  const years = Math.floor(days / 365);

  if (seconds < 60) {
    return seconds <= 10 ? '刚刚' : `${seconds}秒${suffix}`;
  }
  if (minutes < 60) {
    return `${minutes}分钟${suffix}`;
  }
  if (hours < 24) {
    return `${hours}小时${suffix}`;
  }
  if (days < 30) {
    return `${days}天${suffix}`;
  }
  if (months < 12) {
    return `${months}个月${suffix}`;
  }
  return `${years}年${suffix}`;
}

/**
 * 格式化时长
 * 
 * 【功能说明】
 * 将毫秒数格式化为时长字符串。
 * 支持短格式（1:01:01）和长格式（1小时1分钟1秒）两种风格。
 * 
 * @param ms 毫秒数
 * @param options 格式选项
 * @param options.style 格式风格：'short'（默认）或 'long'
 * @returns 格式化后的时长字符串
 * 
 * @example
 * ```typescript
 * formatDuration(3661000);                    // '1:01:01'
 * formatDuration(3661000, { style: 'long' }); // '1小时1分钟1秒'
 * formatDuration(65000);                      // '1:05'
 * formatDuration(90061000);                   // '1d 1:01:01'
 * ```
 */
export function formatDuration(
  ms: number,
  options: { style?: 'short' | 'long' } = {}
): string {
  const { style = 'short' } = options;

  const seconds = Math.floor((ms / 1000) % 60);
  const minutes = Math.floor((ms / (1000 * 60)) % 60);
  const hours = Math.floor((ms / (1000 * 60 * 60)) % 24);
  const days = Math.floor(ms / (1000 * 60 * 60 * 24));

  if (style === 'long') {
    const parts: string[] = [];
    if (days > 0) parts.push(`${days}天`);
    if (hours > 0) parts.push(`${hours}小时`);
    if (minutes > 0) parts.push(`${minutes}分钟`);
    if (seconds > 0 || parts.length === 0) parts.push(`${seconds}秒`);
    return parts.join('');
  }

  if (days > 0) {
    return `${days}d ${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  }
  if (hours > 0) {
    return `${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  }
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

// ============================================================
// 数字格式化
// ============================================================

/**
 * 格式化数字为千分位
 * 
 * 【功能说明】
 * 将数字格式化为带千分位分隔符的字符串。
 * 可指定小数位数。
 * 
 * @param num 数字
 * @param decimals 小数位数（可选）
 * @returns 格式化后的字符串
 * 
 * @example
 * ```typescript
 * formatNumber(1234567);        // '1,234,567'
 * formatNumber(1234567.89, 2);  // '1,234,567.89'
 * formatNumber(1000, 2);        // '1,000.00'
 * formatNumber(NaN);            // 'NaN'
 * formatNumber(Infinity);       // '∞'
 * ```
 */
export function formatNumber(num: number, decimals?: number): string {
  if (isNaN(num)) return 'NaN';
  if (!isFinite(num)) return num > 0 ? '∞' : '-∞';

  const fixed = decimals !== undefined ? num.toFixed(decimals) : num.toString();
  const [intPart, decPart] = fixed.split('.');

  const formattedInt = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',');

  return decPart ? `${formattedInt}.${decPart}` : formattedInt;
}

/**
 * 格式化百分比
 * 
 * 【功能说明】
 * 将数值格式化为百分比字符串。
 * 支持将比率（0-1）或百分比值转换为显示格式。
 * 
 * @param value 数值
 * @param decimals 小数位数（默认 2）
 * @param asRatio 值是否为比率（0-1），默认 true
 * @returns 格式化后的百分比字符串
 * 
 * @example
 * ```typescript
 * formatPercent(0.1234);            // '12.34%'
 * formatPercent(0.1234, 1);         // '12.3%'
 * formatPercent(12.34, 2, false);   // '12.34%'
 * formatPercent(1);                 // '100.00%'
 * ```
 */
export function formatPercent(
  value: number,
  decimals: number = 2,
  asRatio: boolean = true
): string {
  const percent = asRatio ? value * 100 : value;
  return `${percent.toFixed(decimals)}%`;
}

/**
 * 格式化货币
 * 
 * 【功能说明】
 * 将金额格式化为货币字符串。
 * 使用 Intl.NumberFormat 实现，支持国际化。
 * 
 * @param amount 金额
 * @param currency 货币代码（默认 'CNY'）
 * @param locale 地区（默认 'zh-CN'）
 * @returns 格式化后的货币字符串
 * 
 * @example
 * ```typescript
 * formatCurrency(1234.56);                   // '¥1,234.56'
 * formatCurrency(1234.56, 'USD', 'en-US');   // '$1,234.56'
 * formatCurrency(1234.56, 'EUR', 'de-DE');   // '1.234,56 €'
 * formatCurrency(1234.56, 'JPY', 'ja-JP');   // '￥1,235'
 * ```
 */
export function formatCurrency(
  amount: number,
  currency: string = 'CNY',
  locale: string = 'zh-CN'
): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
  }).format(amount);
}

// ============================================================
// 字符串格式化
// ============================================================

/**
 * 截断字符串
 * 
 * 【功能说明】
 * 将超长字符串截断并添加后缀。
 * 常用于显示标题、描述等需要限制长度的文本。
 * 
 * @param str 原字符串
 * @param maxLength 最大长度
 * @param suffix 截断后缀（默认 '...'）
 * @returns 截断后的字符串
 * 
 * @example
 * ```typescript
 * truncate('这是一段很长的文字', 5);        // '这是...'
 * truncate('短文本', 10);                   // '短文本'
 * truncate('Hello World', 8, '…');          // 'Hello W…'
 * ```
 */
export function truncate(
  str: string,
  maxLength: number,
  suffix: string = '...'
): string {
  if (str.length <= maxLength) return str;
  return str.slice(0, maxLength - suffix.length) + suffix;
}

/**
 * 首字母大写
 * 
 * 【功能说明】
 * 将字符串的首字母转为大写，其余保持不变。
 * 
 * @param str 原字符串
 * @returns 首字母大写的字符串
 * 
 * @example
 * ```typescript
 * capitalize('hello');   // 'Hello'
 * capitalize('WORLD');   // 'WORLD'
 * capitalize('');        // ''
 * ```
 */
export function capitalize(str: string): string {
  return str.charAt(0).toUpperCase() + str.slice(1);
}

/**
 * 驼峰转短横线（kebab-case）
 * 
 * 【功能说明】
 * 将驼峰命名转换为短横线命名。
 * 常用于 CSS 类名、URL 路径等场景。
 * 
 * @param str 驼峰命名的字符串
 * @returns 短横线命名的字符串
 * 
 * @example
 * ```typescript
 * kebabCase('backgroundColor');  // 'background-color'
 * kebabCase('myComponent');      // 'my-component'
 * kebabCase('XMLHttpRequest');   // 'x-m-l-http-request'
 * ```
 */
export function kebabCase(str: string): string {
  return str
    .replace(/([a-z])([A-Z])/g, '$1-$2')
    .replace(/[\s_]+/g, '-')
    .toLowerCase();
}

/**
 * 短横线转驼峰（camelCase）
 * 
 * 【功能说明】
 * 将短横线命名转换为驼峰命名。
 * 常用于将 CSS 属性名转换为 JavaScript 属性名。
 * 
 * @param str 短横线命名的字符串
 * @returns 驼峰命名的字符串
 * 
 * @example
 * ```typescript
 * camelCase('background-color');   // 'backgroundColor'
 * camelCase('my-component');       // 'myComponent'
 * camelCase('font-size');          // 'fontSize'
 * ```
 */
export function camelCase(str: string): string {
  return str.replace(/-([a-z])/g, (_, c) => c.toUpperCase());
}
