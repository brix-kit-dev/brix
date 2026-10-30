# @brix-sdk/create-brix

> Brix Platform Scaffold CLI - Create governed plugin, operational and UI modules

## Installation

```bash
# Global install
pnpm add -g @brix-sdk/create-brix
```

The package installs the `create-brix` command, also available as `brix`.

In the Brix repository, build the CLI and run it with Node:

```bash
pnpm install
pnpm --filter @brix-sdk/create-brix build
node packages/@brix/platform-devtools/@brix/create-brix/dist/cli.js --help
```

## Usage

### Create a Module

```bash
create-brix phase7 <kind> <name> [options]
```

| Kind | Creates | Declared in |
|------|---------|-------------|
| `plugin` | `app-<name>/`: a backend plugin as three Maven modules, `app-<name>-api`, `-core` and `-server` | `app-<name>-server/src/main/resources/META-INF/brix/plugin-manifest.yaml` |
| `operational` | `platform-<name>/`: a platform operational module | `src/main/resources/META-INF/brix/platform-operational.yaml` |
| `ui` | `<name>-ui-web/`: a web UI module | `ui-manifest.yaml` |

`<name>` is kebab-case and starts with a lowercase letter. A name that already starts with `app-` or `platform-`, or
already ends with `-ui-web`, keeps it and does not get it twice.

For example, a plugin for runtime 3.2.0, the version this CLI targets:

```bash
create-brix phase7 plugin orders
```

#### Options

| Parameter | Short | Description | Default |
|-----------|-------|-------------|---------|
| `--output-dir <dir>` | `-o` | Directory the module is created in | Current dir |
| `--display-name <name>` | | Display name | `<name>` in PascalCase, without `app-`, `platform-` or `-ui-web` |
| `--description <text>` | | First paragraph of the generated README | `<module id> governed Runtime Shell module` |
| `--owner <owner>` | | Owning team, written to the README, the migration plan and the `operational` and `ui` descriptors | `architecture-governance` |
| `--vendor <vendor>` | | Vendor id in the `plugin` manifest | `brix` |
| `--license <license>` | | SPDX license id in the `plugin` manifest | `Apache-2.0` |
| `--runtime-version <version>` | | Exact runtime version the module is built against | `3.2.0`, the runtime this CLI targets |
| `--runtime-range <range>` | | Runtime versions the module supports | `>=3.2.0 <4.0.0` |
| `--version <version>` | | Version of the generated module | `0.1.0` |
| `--permission <id>` | | Permission id in the `plugin` manifest and the `ui` manifest | `<module id>:read` |
| `--endpoint-path <path>` | | Path of the status endpoint the `ui` module's repository calls; it must start with `/` | `/api/v1/<name>/status` |
| `--no-migration-plan` | | Do not write `migration/phase7-migration-plan.yaml` | The plan is written |

`--display-name`, `--description`, `--owner`, `--vendor`, `--license` and `--runtime-version` may hold letters,
digits, spaces and `. , ( ) _ + / -` only. `--runtime-range` must not contain quotes, backslashes or control
characters.

Options after `phase7` belong to the command, so `--version` there sets the module's version; `create-brix --version`
prints the CLI's own. The generated Maven modules build with Java 17.

## Generated Structure

### Plugin

```text
app-<name>/
├── pom.xml                       # Parent of the three modules
├── README.md
├── migration/phase7-migration-plan.yaml
├── app-<name>-api/pom.xml        # plugin-api
├── app-<name>-core/pom.xml       # plugin-core
└── app-<name>-server/            # plugin-server
    ├── pom.xml                   # Depends on io.brix:runtime-sdk-api
    └── src/main/
        ├── java/io/brix/app/<name>/server/<Name>Plugin.java
        └── resources/META-INF/
            ├── brix/plugin-manifest.yaml
            └── services/io.runtime.sdk.plugin.BrixPlugin
```

The manifest lists the three modules and declares no endpoints and no capabilities. The plugin class binds nothing
and reports `UP`. Add endpoints, events and handlers as the Backend Development Guide in the Brix documentation
describes. Dashes in `<name>` become dots in the Java package, `io.brix.app.<name>`.

### Operational

```text
platform-<name>/
├── pom.xml
├── README.md
├── migration/phase7-migration-plan.yaml
└── src/main/
    ├── java/io/brix/platform/<name>/operational/<Name>OperationalModule.java
    └── resources/META-INF/
        ├── brix/platform-operational.yaml
        └── services/io.runtime.orchestrator.operational.PlatformOperationalModule
```

### UI

```text
<name>-ui-web/
├── package.json
├── tsconfig.json
├── ui-manifest.yaml
├── README.md
├── migration/phase7-migration-plan.yaml
└── src/
    ├── hooks/use<Name>Status.ts
    ├── pages/<Name>StatusPage.tsx
    ├── repositories/<Name>Repository.ts
    └── routes.ts
```

## Scan Legacy Scaffolds

```bash
create-brix legacy-scan <path> [--output <file>]
```

`legacy-scan` reads every file under `<path>`, skipping `node_modules`, `dist` and `target`, and writes a JSON report
to `--output` (`-o`, default `phase7-legacy-scan-report.json`). It exits with status 2 when it finds anything: every
finding is blocking.

| Rule | Finds, in file paths and contents |
|------|-----------------------------------|
| `PH7-C-001` | `module-manifest.yaml` or `.yml`, `startup-order`, `startupOrder` |
| `PH7-C-002` | `@RestController`, `@RequestMapping`, `@Controller`, `ComponentScan`, `EnableJpaRepositories`, `EntityScan` |
| `PH7-C-003` | `KafkaTemplate`, `KafkaProducer`, `RabbitTemplate`, `DataSource`, `JdbcTemplate`, `EntityManager`, `fetch(`, `axios` |
| `PH7-C-004` | `@mui/material`, `@mui/icons-material`, `antd`, `element-plus`, `sx=` |
| `PH7-C-005` | `TODO`, `FIXME`, `placeholder`, `fake`, `mock`, in any case |

Because `PH7-C-005` ignores case, a module whose name contains the word `todo`, such as `app-todo`, is reported too.

## Legacy Commands

`plugin`, `service`, `app` and `add-module` are frozen by Runtime Shell v3.0.10 Phase 0: they fail without
generating anything. Their templates are kept as migration inventory only; see `templates/LEGACY_MIGRATION_ONLY.md`.

## Development

```bash
pnpm --filter @brix-sdk/create-brix build        # tsup, into dist/
pnpm --filter @brix-sdk/create-brix test:phase7  # generates each kind and checks the output
```

## License

Apache-2.0
