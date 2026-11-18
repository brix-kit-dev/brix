#!/usr/bin/env node
// Writes a GitHub Actions error annotation for each failed or broken test in JUnit XML reports: the files given, and the
// Surefire reports under the directories given (the working directory when nothing is given). The annotations of a run
// can be read through the API without signing in; its log cannot (standard-link plan queue item 16, 16a). Prints
// nothing when no test failed and always exits 0: the test step has already failed the job.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

// GitHub keeps at most 10 error annotations per step.
const MAX_ANNOTATIONS = 10;
const MAX_MESSAGE = 900;

const roots = process.argv.length > 2 ? process.argv.slice(2) : ['.'];
const failures = [];
for (const root of roots) {
  for (const report of isFile(root) ? [root] : reports(root)) {
    failures.push(...failedTests(readFileSync(report, 'utf-8')));
  }
}

for (const failure of failures.slice(0, MAX_ANNOTATIONS)) {
  console.log(`::error title=${property(failure.title)}::${data(failure.message)}`);
}
if (failures.length > MAX_ANNOTATIONS) {
  console.log(`${failures.length - MAX_ANNOTATIONS} more failed tests are in the log.`);
}

function isFile(path) {
  try {
    return statSync(path).isFile();
  } catch {
    return false;
  }
}

/** The Surefire XML reports under a directory, skipping dependency and VCS directories. */
function reports(directory) {
  let entries;
  try {
    entries = readdirSync(directory, { withFileTypes: true });
  } catch {
    return [];
  }
  const found = [];
  for (const entry of entries) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      if (entry.name !== 'node_modules' && entry.name !== '.git') {
        found.push(...reports(path));
      }
    } else if (/^TEST-.*\.xml$/.test(entry.name) && directory.endsWith('surefire-reports')) {
      found.push(path);
    }
  }
  return found.sort();
}

/** Each test case of a report that holds a failure or an error. */
function failedTests(xml) {
  const failed = [];
  // Attribute values may hold '>', so the patterns step over quoted values.
  const testcase = /<testcase\b((?:[^>"]|"[^"]*")*?)(\/>|>([\s\S]*?)<\/testcase>)/g;
  let match;
  while ((match = testcase.exec(xml)) !== null) {
    const body = match[3] ?? '';
    const problem = /<(failure|error)\b((?:[^>"]|"[^"]*")*?)\/?>/.exec(body);
    if (!problem) {
      continue;
    }
    const attributes = match[1];
    const className = attribute(attributes, 'classname') ?? '';
    const name = attribute(attributes, 'name') ?? '';
    // Surefire names a Java class; Vitest names the test file.
    const where = className.includes('/') ? `${className} ` : `${className.slice(className.lastIndexOf('.') + 1)}.`;
    const message = attribute(problem[2], 'message') ?? attribute(problem[2], 'type') ?? problem[1];
    failed.push({
      title: `${where}${name}`,
      message: message.length > MAX_MESSAGE ? `${message.slice(0, MAX_MESSAGE)}…` : message,
    });
  }
  return failed;
}

function attribute(attributes, name) {
  const match = new RegExp(`\\b${name}="([^"]*)"`).exec(attributes);
  return match ? unescapeXml(match[1]) : undefined;
}

function unescapeXml(text) {
  return text.replace(/&(#x[0-9a-fA-F]+|#[0-9]+|lt|gt|amp|quot|apos);/g, (entity, code) => {
    switch (code) {
      case 'lt': return '<';
      case 'gt': return '>';
      case 'amp': return '&';
      case 'quot': return '"';
      case 'apos': return "'";
      default:
        return String.fromCodePoint(code[1] === 'x' ? parseInt(code.slice(2), 16) : parseInt(code.slice(1), 10));
    }
  });
}

/** Escapes the message of a workflow command. */
function data(text) {
  return text.replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A');
}

/** Escapes a property of a workflow command. */
function property(text) {
  return data(text).replace(/:/g, '%3A').replace(/,/g, '%2C');
}
