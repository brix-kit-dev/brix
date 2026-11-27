import { mkdtemp, readFile, readdir, rm } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { tmpdir } from 'node:os';
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
import {
  createDefaultGovernedScaffoldConfig,
  generateGovernedScaffold,
  scanLegacyScaffold,
} from '../dist/index.js';

const forbidden = [
  /module-manifest\.ya?ml/,
  /startup-order|startupOrder/,
  /@RestController|@RequestMapping|@Controller\b/,
  /ComponentScan|EnableJpaRepositories|EntityScan/,
  /KafkaTemplate|KafkaProducer|RabbitTemplate/,
  /DataSource|JdbcTemplate|EntityManager/,
  /fetch\s*\(|axios\b/,
  /@mui\/material|@mui\/icons-material|antd\b|element-plus|sx\s*=/,
  /\bTODO\b|\bFIXME\b|\bplaceholder\b|\bfake\b|\bmock\b/i,
];

const require = createRequire(import.meta.url);
const { Linter } = require('eslint');
const architecture = require('@brix-sdk/eslint-config-architecture');

const root = await mkdtemp(join(tmpdir(), 'brix-phase7-'));

try {
  for (const kind of ['plugin', 'operational', 'ui']) {
    const config = createDefaultGovernedScaffoldConfig(kind, `phase7-${kind}`, root);
    config.displayName = `Phase7 ${kind}`;
    config.owner = 'architecture-governance';
    await generateGovernedScaffold(config);
  }

  const files = await listFiles(root);
  assert(files.some((file) => file.endsWith('META-INF/brix/plugin-manifest.yaml')));
  assert(files.some((file) => file.endsWith('META-INF/brix/platform-operational.yaml')));
  assert(files.some((file) => file.endsWith('ui-manifest.yaml')));
  assert(files.some((file) => file.endsWith('phase7-migration-plan.yaml')));

  for (const file of files) {
    const content = await readFile(file, 'utf-8');
    const rel = relative(root, file);
    for (const pattern of forbidden) {
      assert(!pattern.test(`${rel}\n${content}`), `${rel} matched ${pattern}`);
    }
  }

  // The UI module lints with the platform's UI preset (standard-link queue item 19, 19e, D167): it ships the
  // configuration, a lint script and the dependencies, and its generated sources pass the preset as they are.
  const uiDir = dirname(files.find((file) => file.endsWith('ui-manifest.yaml')));
  const uiManifest = JSON.parse(await readFile(join(uiDir, 'package.json'), 'utf-8'));
  assert.equal(uiManifest.scripts?.lint, 'eslint src', 'the UI module has a lint script');
  assert(uiManifest.devDependencies?.['@brix-sdk/eslint-config-architecture'], 'the UI module depends on the rule package');
  assert(uiManifest.devDependencies?.eslint, 'the UI module depends on ESLint');
  assert((await readFile(join(uiDir, 'eslint.config.js'), 'utf-8')).includes('...architecture.configs.ui'),
    'the UI module lints with the UI preset');
  const linter = new Linter({ configType: 'flat' });
  const uiSources = files.filter((file) => file.startsWith(join(uiDir, 'src')) && /\.(ts|tsx)$/.test(file));
  assert(uiSources.length > 0, 'the UI module has sources');
  for (const file of uiSources) {
    const problems = linter
      .verify(await readFile(file, 'utf-8'), architecture.configs.ui, { filename: relative(uiDir, file) })
      .filter((message) => message.fatal || message.severity === 2);
    assert.equal(problems.length, 0, `${relative(root, file)}: ${problems
      .map((m) => `${m.line}:${m.column} ${m.ruleId ?? 'parse'} ${m.message}`).join('; ')}`);
  }

  // Values reach each file as they are: XML escapes them, YAML, JSON, Java, TypeScript and Markdown do not.
  const manifest = await readFile(files.find((file) => file.endsWith('META-INF/brix/plugin-manifest.yaml')), 'utf-8');
  assert(manifest.includes('supportedRange: ">=3.2.0 <4.0.0"'), 'the runtime range is written unescaped');
  for (const file of files.filter((file) => !file.endsWith('.xml'))) {
    const content = await readFile(file, 'utf-8');
    assert(!/&(gt|lt|amp|quot|#39);/.test(content), `${relative(root, file)} holds an HTML entity`);
  }
  // The generated poms name the SDK and the runtime by their real coordinates.
  for (const file of files.filter((file) => file.endsWith('pom.xml'))) {
    const content = await readFile(file, 'utf-8');
    assert(!content.includes('<groupId>io.runtime</groupId>'), `${relative(root, file)} names the io.runtime group`);
  }
  // A plugin's api module locks its contracts from the start (standard-link plan queue item 3, D115): its test pins
  // every schema version the module ships, with the runtime's contract generator.
  const lockTest = files.find((file) => file.endsWith('-api/src/test/java/io/brix/app/phase7/plugin/api/ContractSourcesTest.java'));
  assert(lockTest, 'the plugin api module has a ContractSourcesTest');
  assert((await readFile(lockTest, 'utf-8')).includes('ContractSources.verify(Path.of(""), "io.brix.app.phase7.plugin.api");'),
    'the lock test verifies the api module with the package of its generated types');
  const apiPom = await readFile(files.find((file) => file.endsWith('phase7-plugin-api/pom.xml')), 'utf-8');
  assert(/<artifactId>runtime-contract-generator<\/artifactId>\s*<version>3\.2\.0<\/version>\s*<scope>test<\/scope>/.test(apiPom),
    'the api module runs the lock with the contract generator of the runtime it compiles against');
  // Each backend module of a plugin runs the guard's plugin rules on its own classes (standard-link queue item 22, 22b,
  // D182): its ArchitectureTest reads the module's target/classes, the guard comes at test scope in the version of the
  // runtime the plugin compiles against, and the parent takes JUnit from the BOM of the version the platform builds with.
  for (const kind of ['api', 'core', 'server']) {
    const guardTest = files.find((file) =>
      file.endsWith(`-${kind}/src/test/java/io/brix/app/phase7/plugin/${kind}/ArchitectureTest.java`));
    assert(guardTest, `the plugin ${kind} module has an ArchitectureTest`);
    const source = await readFile(guardTest, 'utf-8');
    assert(source.includes(`package io.brix.app.phase7.plugin.${kind};`), `the ${kind} ArchitectureTest is in the module's package`);
    assert(source.includes('@AnalyzeClasses(locations = ModuleClasses.class)'),
      `the ${kind} ArchitectureTest reads the module's own classes`);
    assert(source.includes('BrixArchitectureRules.pluginProfile()'), `the ${kind} ArchitectureTest runs the plugin profile`);
    const modulePom = await readFile(files.find((file) => file.endsWith(`phase7-plugin-${kind}/pom.xml`)), 'utf-8');
    assert(/<artifactId>architecture-guard<\/artifactId>\s*<version>3\.2\.0<\/version>\s*<scope>test<\/scope>/.test(modulePom),
      `the ${kind} module has the guard of the runtime it compiles against, at test scope`);
  }
  const parentPom = await readFile(files.find((file) => file.endsWith('app-phase7-plugin/pom.xml')), 'utf-8');
  assert(/<artifactId>junit-bom<\/artifactId>\s*<version>6\.0\.3<\/version>\s*<type>pom<\/type>\s*<scope>import<\/scope>/
    .test(parentPom), 'the plugin parent takes JUnit from the BOM of the version the platform builds with');
  // The operational module runs the guard's rules for operational modules on its own classes (standard-link queue item
  // 22, 22c, D183), with the guard at test scope in the runtime's version and JUnit from the platform's BOM.
  const operationalTest = files.find((file) => file.endsWith(
    'platform-phase7-operational/src/test/java/io/brix/platform/phase7/operational/operational/ArchitectureTest.java'));
  assert(operationalTest, 'the operational module has an ArchitectureTest');
  const operationalSource = await readFile(operationalTest, 'utf-8');
  assert(operationalSource.includes('@AnalyzeClasses(locations = ModuleClasses.class)'),
    "the operational ArchitectureTest reads the module's own classes");
  assert(operationalSource.includes('BrixArchitectureRules.operationalProfile()'),
    'the operational ArchitectureTest runs the operational profile');
  const operationalPom = await readFile(files.find((file) => file.endsWith('platform-phase7-operational/pom.xml')), 'utf-8');
  assert(/<artifactId>architecture-guard<\/artifactId>\s*<version>3\.2\.0<\/version>\s*<scope>test<\/scope>/.test(operationalPom),
    'the operational module has the guard of the runtime it compiles against, at test scope');
  assert(/<artifactId>junit-bom<\/artifactId>\s*<version>6\.0\.3<\/version>\s*<type>pom<\/type>\s*<scope>import<\/scope>/
    .test(operationalPom), 'the operational module takes JUnit from the BOM of the version the platform builds with');
  // Free text that would break or change a generated file is refused.
  const unsafe = createDefaultGovernedScaffoldConfig('plugin', 'phase7-unsafe', join(root, 'unsafe'));
  unsafe.displayName = 'Orders: "v2"';
  await assert.rejects(generateGovernedScaffold(unsafe), /displayName may hold/);
  const quotedRange = createDefaultGovernedScaffoldConfig('plugin', 'phase7-range', join(root, 'range'));
  quotedRange.runtimeRange = '>=3.2.0" <4.0.0';
  await assert.rejects(generateGovernedScaffold(quotedRange), /runtimeRange must not contain quotes/);
  const named = createDefaultGovernedScaffoldConfig('plugin', 'phase7-named', join(root, 'named'));
  named.displayName = '订单 Orders (v2)';
  await generateGovernedScaffold(named);

  const scan = await scanLegacyScaffold(root);
  assert.equal(scan.findings.length, 0);

  const legacyRoot = join(root, 'legacy');
  await generateLegacyFixture(legacyRoot);
  const legacyScan = await scanLegacyScaffold(legacyRoot);
  assert(legacyScan.findings.some((finding) => finding.id === 'PH7-C-001'));
  assert(legacyScan.findings.some((finding) => finding.id === 'PH7-C-002'));
  assert(legacyScan.findings.some((finding) => finding.id === 'PH7-C-004'));
} finally {
  await rm(root, { recursive: true, force: true });
}

async function listFiles(dir) {
  const result = [];
  const entries = await readdir(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      result.push(...await listFiles(fullPath));
    } else {
      result.push(fullPath);
    }
  }
  return result;
}

async function generateLegacyFixture(dir) {
  const { mkdir, writeFile } = await import('node:fs/promises');
  await mkdir(dir, { recursive: true });
  await writeFile(join(dir, 'module-manifest.yaml'), 'startup-order: 10\n', 'utf-8');
  await writeFile(join(dir, 'Controller.java'), '@RestController\nclass Controller {}\n', 'utf-8');
  await writeFile(join(dir, 'Page.tsx'), "import { Box } from '@mui/material';\n", 'utf-8');
}
