#!/usr/bin/env node
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const root = process.cwd();
const scanRoots = ['packages', 'e2e-tests'].map((path) => join(root, path));
const ignoredDirectories = new Set([
  '.git',
  'node_modules',
  'dist',
  'target',
  'coverage',
  '.turbo',
  '.next',
]);
const scannedExtensions = new Set([
  '.java',
  '.ts',
  '.tsx',
  '.js',
  '.jsx',
  '.mjs',
  '.cjs',
  '.json',
  '.xml',
  '.yml',
  '.yaml',
  '.sql',
]);

const exactTokenChecks = [
  {
    id: 'platform-admin-v2-sensitive-flow-token',
    tokens: [
      'tempPassword',
      'platformAdminMode',
      'force_password_change',
      'MemberStatus.PENDING',
      'setupUrlMasked',
      'temp_password_expires_at',
      'PasswordGeneratorService',
    ],
  },
];

const legacyRoleLiteralPattern = /(["'])(PLATFORM_ADMIN|PLATFORM_OPERATOR|PLATFORM_AUDITOR|SUPPORT_ADMIN)\1/g;
const findings = [];

for (const scanRoot of scanRoots) {
  if (existsDirectory(scanRoot)) {
    walk(scanRoot);
  }
}

if (findings.length > 0) {
  console.error('[banned-tokens] Forbidden platform-admin redline tokens found:');
  for (const finding of findings) {
    console.error(`  ${finding.file}:${finding.line}: ${finding.check}: ${finding.token}`);
  }
  process.exit(1);
}

console.log('[banned-tokens] OK');

function walk(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (!ignoredDirectories.has(entry.name)) {
        walk(join(directory, entry.name));
      }
      continue;
    }

    if (!entry.isFile() || !shouldScan(entry.name)) {
      continue;
    }

    scanFile(join(directory, entry.name));
  }
}

function scanFile(filePath) {
  const relativePath = relative(root, filePath).replaceAll('\\', '/');
  const content = readFileSync(filePath, 'utf8');
  const lines = content.split(/\r?\n/);
  lines.forEach((line, index) => {
    scanExactTokens(relativePath, line, index + 1);
    scanLegacyRoleLiterals(relativePath, line, index + 1);
  });
}

function scanExactTokens(relativePath, line, lineNumber) {
  for (const check of exactTokenChecks) {
    for (const token of check.tokens) {
      if (isExactTokenAllowed(relativePath, line, token)) {
        continue;
      }
      if (line.includes(token)) {
        findings.push({ file: relativePath, line: lineNumber, check: check.id, token });
      }
    }
  }
}

function scanLegacyRoleLiterals(relativePath, line, lineNumber) {
  if (isLegacyRoleLiteralAllowed(relativePath, line)) {
    return;
  }
  legacyRoleLiteralPattern.lastIndex = 0;
  let match;
  while ((match = legacyRoleLiteralPattern.exec(line)) !== null) {
    findings.push({
      file: relativePath,
      line: lineNumber,
      check: 'platform-admin-v2-legacy-role-literal',
      token: match[2],
    });
  }
}

function isLegacyRoleLiteralAllowed(relativePath, line) {
  const normalized = relativePath.replaceAll('\\', '/');
  if (normalized.includes('/db/migration/')) {
    return true;
  }
  if (normalized.endsWith('/RoleCode.java')) {
    return true;
  }
  if (normalized.includes('/src/__tests__/') || /\.(test|spec)\.[jt]sx?$/.test(normalized)) {
    return true;
  }
  if (normalized.includes('/view-mode') || normalized.includes('ViewMode')) {
    return true;
  }
  if (line.includes('TENANT_ACTOR') && line.includes('TENANT_SUBJECT')) {
    return true;
  }
  if (line.includes('resourceType("PLATFORM_ADMIN")')) {
    return true;
  }
  return false;
}

function shouldScan(fileName) {
  const dot = fileName.lastIndexOf('.');
  return dot >= 0 && scannedExtensions.has(fileName.slice(dot));
}

function existsDirectory(path) {
  try {
    return statSync(path).isDirectory();
  } catch {
    return false;
  }
}

function isExactTokenAllowed(relativePath, line, token) {
  void relativePath;
  void line;
  void token;
  return false;
}
