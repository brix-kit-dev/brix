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

const RETIRED_PERMISSION_IDS_TEST =
  'packages/@brix/platform-commons/packages/server/platform-auth-api/src/test/java/io/brix/platform/auth/api/PinnedIdentifiersTest.java';

// A check without allowedIn holds in every scanned file.
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
  {
    // No permission passes the Dispatcher's check: platform:bypass is a retired permission ID that never comes back
    // (super-admin §3.3 and Δ7; standard-link plan queue item 18, D158, D160). Migrations may name it, as V1 seeded it
    // and V5 removed it, and so may the contract test's list of retired IDs, which keeps it out of PermissionCode.
    id: 'platform-bypass-permission',
    tokens: ['platform:bypass'],
    allowedIn: (relativePath) =>
      relativePath.includes('/db/migration/') || relativePath === RETIRED_PERMISSION_IDS_TEST,
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
  // On GitHub each finding is also an annotation: a run's annotations can be read through the API without signing in,
  // its log cannot (standard-link plan queue item 16, 16a). GitHub keeps the first 10 of a step.
  if (process.env.GITHUB_ACTIONS === 'true') {
    for (const finding of findings) {
      const where = `file=${annotationProperty(finding.file)},line=${finding.line}`;
      console.log(`::error ${where},title=${annotationProperty(finding.check)}::${annotationData(finding.token)}`);
    }
  }
  process.exit(1);
}

console.log('[banned-tokens] OK');

function annotationData(text) {
  return String(text).replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A');
}

function annotationProperty(text) {
  return annotationData(text).replace(/:/g, '%3A').replace(/,/g, '%2C');
}

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
    if (isExactTokenAllowed(check, relativePath)) {
      continue;
    }
    for (const token of check.tokens) {
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
    if (isAuditTargetType(match[2], line)) {
      continue;
    }
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
  return false;
}

// PLATFORM_ADMIN is also a platform audit target type (super-admin blueprint), which is no role.
function isAuditTargetType(token, line) {
  return token === 'PLATFORM_ADMIN' && /\btargetTypes?\b/.test(line);
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

function isExactTokenAllowed(check, relativePath) {
  return check.allowedIn?.(relativePath) ?? false;
}
