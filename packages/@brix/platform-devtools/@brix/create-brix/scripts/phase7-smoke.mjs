import { mkdtemp, readFile, readdir, rm } from 'node:fs/promises';
import { join, relative } from 'node:path';
import { tmpdir } from 'node:os';
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
