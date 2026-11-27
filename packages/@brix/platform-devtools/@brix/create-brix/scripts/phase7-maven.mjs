// Builds what the governed templates generate (standard-link queue item 22, 22e, D184): a plugin and an operational
// module, each with `mvn verify`, against the brix modules in the local Maven repository, which must be installed
// first. Then reads their test reports: the plugin's api and core modules have no classes, so the guard's rules are
// reported as skipped; the plugin's server module and the operational module pass every rule; the api module's contract
// lock passes. test:phase7 only reads the generated files; a template that does not compile, or that breaks a guard
// rule, shows up here. PHASE7_MAVEN_ARGS adds arguments to each Maven run, such as `-s <settings.xml>`.
import { mkdtemp, readFile, readdir, rm } from 'node:fs/promises';
import { basename, join } from 'node:path';
import { tmpdir } from 'node:os';
import { spawnSync } from 'node:child_process';
import assert from 'node:assert/strict';
import { createDefaultGovernedScaffoldConfig, generateGovernedScaffold } from '../dist/index.js';

const extra = (process.env.PHASE7_MAVEN_ARGS ?? '').split(/\s+/).filter(Boolean);
const root = await mkdtemp(join(tmpdir(), 'brix-phase7-maven-'));

try {
  for (const kind of ['plugin', 'operational']) {
    await generateGovernedScaffold(createDefaultGovernedScaffoldConfig(kind, `phase7-${kind}`, root));
  }
  // A failing test does not stop the build, so that its report says which rule failed.
  for (const project of ['app-phase7-plugin', 'platform-phase7-operational']) {
    const run = spawnSync('mvn', ['-B', '-ntp', ...extra, '-Dmaven.test.failure.ignore=true', 'verify'],
      { cwd: join(root, project), stdio: 'inherit' });
    assert.equal(run.status, 0, `mvn verify of the generated ${project} failed`);
  }
  const plugin = join(root, 'app-phase7-plugin');
  await expectRules(join(plugin, 'app-phase7-plugin-api'), 'skipped');
  await expectRules(join(plugin, 'app-phase7-plugin-core'), 'skipped');
  await expectRules(join(plugin, 'app-phase7-plugin-server'), 'passed');
  await expectRules(join(root, 'platform-phase7-operational'), 'passed');
  const lock = await testcases(join(plugin, 'app-phase7-plugin-api'), 'ContractSourcesTest');
  assert(lock.length === 1 && lock[0].state === 'passed',
    `app-phase7-plugin-api ContractSourcesTest: expected one passed test, found ${describe(lock)}`);
  console.log('The generated plugin and operational module build, and the guard ran in each of their modules.');
} finally {
  await rm(root, { recursive: true, force: true });
}

/** Every rule of the module's ArchitectureTest ended in the given state, and there was at least one. */
async function expectRules(module, state) {
  const cases = await testcases(module, 'ArchitectureTest');
  assert(cases.length > 0, `${basename(module)} ran no ArchitectureTest`);
  const others = cases.filter((testcase) => testcase.state !== state);
  assert.equal(others.length, 0,
    `${basename(module)} ArchitectureTest: expected every rule ${state}, found ${describe(others)}`);
  console.log(`${basename(module)} ArchitectureTest: ${cases.length} rules ${state}`);
}

/** The test cases of one test class in a module's Surefire XML reports, with their state. */
async function testcases(module, testClass) {
  const directory = join(module, 'target', 'surefire-reports');
  const reports = (await readdir(directory).catch(() => []))
    .filter((name) => name.startsWith('TEST-') && name.endsWith(`.${testClass}.xml`));
  const cases = [];
  for (const report of reports) {
    const xml = await readFile(join(directory, report), 'utf-8');
    for (const match of xml.matchAll(/<testcase\b([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g)) {
      const name = xmlText(/\bname="([^"]*)"/.exec(match[1])?.[1] ?? '?');
      const body = match[3] ?? '';
      const state = /<(failure|error)\b/.test(body) ? 'failed' : /<skipped\b/.test(body) ? 'skipped' : 'passed';
      cases.push({ name, state });
    }
  }
  return cases;
}

/** An XML attribute value as text: ArchUnit names a profile's rule "PluginProfile > rule", written with &gt;. */
function xmlText(value) {
  const entities = { lt: '<', gt: '>', quot: '"', apos: "'", amp: '&' };
  return value.replace(/&(lt|gt|quot|apos|amp);/g, (_, entity) => entities[entity]);
}

function describe(cases) {
  return cases.length === 0 ? 'none' : cases.map((testcase) => `${testcase.name} ${testcase.state}`).join(', ');
}
