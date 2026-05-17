/**
 * @file validators.ts
 * @description Validation utility functions
 * @module @brix-sdk/utils/validators
 * @version 3.0.0
 * 
 * ## Module Overview
 * Provides various data validation utilities including basic format validation,
 * China-specific document verification, password strength checking, and more.
 * All validation functions return boolean values without throwing exceptions,
 * making them convenient for conditional logic.
 * 
 * ## Use Cases
 * - Form validation: email, phone, password, etc.
 * - Document validation: ID card, unified social credit code, bank card
 * - Data validation: URL, IP, JSON format
 * - Type checking: empty values, numbers, ranges
 * 
 * @license Apache-2.0
 */

// ============================================================
// Basic Validation
// ============================================================

/**
 * Validate email format
 * 
 * Validates whether a string is a valid email address format
 * using a simplified RFC 5322 regular expression.
 * 
 * @param email - Email address
 * @returns Whether the email format is valid
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
  // Simplified RFC 5322
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email.trim());
}

/**
 * Validate China mainland phone number
 * 
 * Validates whether a string is a valid China mainland mobile phone number.
 * Supports all current carrier number segments (13x, 14x, 15x, 16x, 17x, 18x, 19x).
 * 
 * Validation rules:
 * - 11 digits
 * - Starts with 1
 * - Second digit is 3-9
 * 
 * @param phone - Phone number
 * @returns Whether the phone number is valid
 * 
 * @example
 * ```typescript
 * isValidPhone('13800138000');  // true
 * isValidPhone('19912345678');  // true
 * isValidPhone('12345678901');  // false (second digit invalid)
 * isValidPhone('1380013800');   // false (insufficient digits)
 * isValidPhone('138 0013 8000'); // false (contains spaces)
 * ```
 */
export function isValidPhone(phone: string): boolean {
  if (!phone || typeof phone !== 'string') return false;
  // China mainland mobile: starts with 1, second digit 3-9, total 11 digits
  const phoneRegex = /^1[3-9]\d{9}$/;
  return phoneRegex.test(phone.trim());
}

/**
 * Validate URL format
 * 
 * Validates whether a string is a valid HTTP/HTTPS URL
 * using the native URL API for parsing validation.
 * 
 * @param url - URL string
 * @returns Whether the URL is valid
 * 
 * @example
 * ```typescript
 * isValidUrl('https://example.com');        // true
 * isValidUrl('http://example.com/path');    // true
 * isValidUrl('ftp://example.com');          // false (FTP not supported)
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
 * Validate IPv4 address
 * 
 * Validates whether a string is a valid IPv4 address format.
 * 
 * Validation rules:
 * - Four numeric segments separated by dots
 * - Each segment ranges from 0-255
 * - Leading zeros are not allowed (e.g., 01.02.03.04)
 * 
 * @param ip - IP address string
 * @returns Whether the IPv4 address is valid
 * 
 * @example
 * ```typescript
 * isValidIPv4('192.168.1.1');     // true
 * isValidIPv4('0.0.0.0');         // true
 * isValidIPv4('255.255.255.255'); // true
 * isValidIPv4('256.1.1.1');       // false (out of range)
 * isValidIPv4('01.02.03.04');     // false (leading zeros)
 * isValidIPv4('192.168.1');       // false (incomplete)
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
 * Validate JSON string
 * 
 * Validates whether a string is in valid JSON format.
 * 
 * @param str - String to validate
 * @returns Whether it is valid JSON
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
// China-Specific Validation
// ============================================================

/**
 * Validate China mainland resident ID card number
 * 
 * Validates whether a string is a valid China mainland resident ID card number.
 * Supports both 18-digit (current) and 15-digit (legacy) formats.
 * The 18-digit ID card undergoes check digit verification.
 * 
 * 18-digit ID card structure:
 * - Digits 1-6: Region code
 * - Digits 7-14: Date of birth (YYYYMMDD)
 * - Digits 15-17: Sequence code (17th digit: odd=male, even=female)
 * - Digit 18: Check digit (0-9 or X)
 * 
 * @param idCard - ID card number
 * @returns Whether the ID card number is valid
 * 
 * @example
 * ```typescript
 * isValidIdCard('110101199001011234');  // check digit verification required
 * isValidIdCard('110101900101123');     // 15-digit legacy ID
 * isValidIdCard('12345678901234567');   // false (check digit error)
 * isValidIdCard('');                    // false
 * ```
 */
export function isValidIdCard(idCard: string): boolean {
  if (!idCard || typeof idCard !== 'string') return false;
  
  const id = idCard.trim().toUpperCase();
  
  // 18-digit ID card
  if (id.length === 18) {
    // First 17 digits must be numeric
    if (!/^\d{17}[\dX]$/.test(id)) return false;
    
    // Check digit verification
    const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
    const checkCodes = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'];
    
    let sum = 0;
    for (let i = 0; i < 17; i++) {
      sum += parseInt(id[i], 10) * weights[i];
    }
    
    return id[17] === checkCodes[sum % 11];
  }
  
  // 15-digit ID card (legacy format)
  if (id.length === 15) {
    return /^\d{15}$/.test(id);
  }
  
  return false;
}

/**
 * Validate China Unified Social Credit Code
 * 
 * Validates whether a string is a valid Unified Social Credit Code (enterprise credit code).
 * 
 * Code structure:
 * - Digit 1: Registration authority department code (1 digit)
 * - Digit 2: Organization category code (1 digit)
 * - Digits 3-8: Administrative region code (6 digits)
 * - Digits 9-17: Subject identifier code (organization code)
 * - Digit 18: Check digit
 * 
 * @param code - Unified Social Credit Code
 * @returns Whether the code is valid
 * 
 * @example
 * ```typescript
 * isValidUnifiedCreditCode('91110000MA0ABCDE12');  // format verification required
 * isValidUnifiedCreditCode('');                    // false
 * ```
 */
export function isValidUnifiedCreditCode(code: string): boolean {
  if (!code || typeof code !== 'string') return false;
  // 18 characters, digits and uppercase letters (excluding I, O, Z, S, V)
  const regex = /^[0-9A-HJ-NPQRTUWXY]{2}\d{6}[0-9A-HJ-NPQRTUWXY]{10}$/;
  return regex.test(code.trim().toUpperCase());
}

/**
 * Validate China mainland bank card number (Luhn algorithm)
 * 
 * Validates whether a string is a valid bank card number
 * using the Luhn algorithm for verification.
 * 
 * Luhn algorithm:
 * 1. From right to left, double every even-positioned digit; if result > 9, subtract 9
 * 2. Sum all digits
 * 3. Valid if divisible by 10
 * 
 * @param cardNumber - Bank card number
 * @returns Whether the bank card number is valid
 * 
 * @example
 * ```typescript
 * isValidBankCard('6222020200010001234');  // Luhn verification
 * isValidBankCard('1234 5678 9012 3456');  // supports spaces
 * isValidBankCard('123');                  // false (insufficient digits)
 * ```
 */
export function isValidBankCard(cardNumber: string): boolean {
  if (!cardNumber || typeof cardNumber !== 'string') return false;
  
  const cleaned = cardNumber.replace(/\s/g, '');
  if (!/^\d{13,19}$/.test(cleaned)) return false;
  
  // Luhn algorithm
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
// Password Strength Validation
// ============================================================

/**
 * Password strength level
 */
export type PasswordStrength = 'weak' | 'medium' | 'strong' | 'very-strong';

/**
 * Password strength check result
 */
export interface PasswordStrengthResult {
  /** Strength level */
  strength: PasswordStrength;
  /** Strength score (0-10) */
  score: number;
  /** List of improvement suggestions */
  suggestions: string[];
}

/**
 * Check password strength
 * 
 * Comprehensively evaluates password strength and returns the strength level, score,
 * and improvement suggestions.
 * 
 * Scoring rules:
 * - Length: 8+ chars +1, 12+ chars +1, 16+ chars +1
 * - Lowercase letters: +1
 * - Uppercase letters: +1
 * - Digits: +1
 * - Special characters: +2
 * - Common patterns: -2
 * 
 * Strength levels:
 * - weak: score <= 2
 * - medium: score 3-4
 * - strong: score 5-6
 * - very-strong: score >= 7
 * 
 * @param password - Password to check
 * @returns Strength check result
 * 
 * @example
 * ```typescript
 * checkPasswordStrength('123456');
 * // { strength: 'weak', score: 0, suggestions: ['Password must be at least 8 characters', ...] }
 * 
 * checkPasswordStrength('MyP@ssw0rd');
 * // { strength: 'strong', score: 7, suggestions: [] }
 * ```
 */
export function checkPasswordStrength(password: string): PasswordStrengthResult {
  const suggestions: string[] = [];
  let score = 0;

  if (!password) {
    return { strength: 'weak', score: 0, suggestions: ['Please enter a password'] };
  }

  // Length check
  if (password.length >= 8) score += 1;
  else suggestions.push('Password must be at least 8 characters');
  
  if (password.length >= 12) score += 1;
  if (password.length >= 16) score += 1;

  // Contains lowercase letters
  if (/[a-z]/.test(password)) score += 1;
  else suggestions.push('Consider including lowercase letters');

  // Contains uppercase letters
  if (/[A-Z]/.test(password)) score += 1;
  else suggestions.push('Consider including uppercase letters');

  // Contains digits
  if (/\d/.test(password)) score += 1;
  else suggestions.push('Consider including digits');

  // Contains special characters
  if (/[!@#$%^&*(),.?":{}|<>]/.test(password)) score += 2;
  else suggestions.push('Consider including special characters');

  // Avoid common password patterns
  const commonPatterns = [
    /^123/,
    /password/i,
    /qwerty/i,
    /^abc/i,
  ];
  if (commonPatterns.some((p) => p.test(password))) {
    score -= 2;
    suggestions.push('Avoid using common password patterns');
  }

  // Determine strength level
  let strength: PasswordStrength;
  if (score <= 2) strength = 'weak';
  else if (score <= 4) strength = 'medium';
  else if (score <= 6) strength = 'strong';
  else strength = 'very-strong';

  return { strength, score: Math.max(0, score), suggestions };
}

// ============================================================
// Type Checking
// ============================================================

/**
 * Check if a value is empty
 * 
 * Checks whether a value is "empty". Supports multiple types:
 * - null/undefined: empty
 * - String: empty string or whitespace-only is empty
 * - Array: empty array is empty
 * - Object: empty object is empty
 * 
 * @param value - Value to check
 * @returns Whether the value is empty
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
 * Check if a value is numeric
 * 
 * Checks whether a value is a valid number (including numeric types and parseable numeric strings).
 * Excludes NaN and Infinity.
 * 
 * @param value - Value to check
 * @returns Whether the value is a valid number
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
 * Check if a value is an integer
 * 
 * @param value - Value to check
 * @returns Whether the value is an integer
 * 
 * @example
 * ```typescript
 * isInteger(123);    // true
 * isInteger(-100);   // true
 * isInteger(12.34);  // false
 * isInteger('123');  // false (must be number type)
 * ```
 */
export function isInteger(value: unknown): boolean {
  return Number.isInteger(value);
}

/**
 * Check if a value is positive
 * 
 * @param value - Number to check
 * @returns Whether the value is positive
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
 * Check if a value is within a range
 * 
 * @param value - Number to check
 * @param min - Minimum value (inclusive)
 * @param max - Maximum value (inclusive)
 * @returns Whether the value is within the range
 * 
 * @example
 * ```typescript
 * isInRange(5, 1, 10);   // true
 * isInRange(1, 1, 10);   // true (inclusive boundary)
 * isInRange(10, 1, 10);  // true (inclusive boundary)
 * isInRange(0, 1, 10);   // false
 * isInRange(11, 1, 10);  // false
 * ```
 */
export function isInRange(value: number, min: number, max: number): boolean {
  return typeof value === 'number' && value >= min && value <= max;
}