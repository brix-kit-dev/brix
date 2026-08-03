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
