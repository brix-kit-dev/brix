# @brix-sdk/create-brix

> Brix Platform Scaffold CLI - Create plugins and services

## Installation

```bash
# Global install
npm install -g @brix-sdk/create-brix

# Or use npm create
npm create @brix-sdk/brix
```

## Usage

### Create Plugin

```bash
# Interactive plugin creation
npm create @brix-sdk/brix plugin

# Quick creation with parameters
npm create @brix-sdk/brix plugin user \
  --flyway-prefix 001 \
  --with-web \
  --with-mobile \
  --output-dir ./plugins
```

#### Plugin Parameters

| Parameter | Short | Description | Default |
|-----------|-------|-------------|---------|
| `--flyway-prefix` | `-f` | Flyway version prefix (3 digits) | Interactive |
| `--with-web` | | Include web frontend module | `true` |
| `--with-mobile` | | Include mobile frontend module | `false` |
| `--with-api` | | Include API module | `true` |
| `--output-dir` | `-o` | Output directory | Current dir |

### Create Service

```bash
# Interactive service creation
npm create @brix-sdk/brix service

# Quick creation with parameters
npm create @brix-sdk/brix service platform \
  --port 8080 \
  --plugins user,contract,file-center \
  --output-dir ./services
```

#### Service Parameters

| Parameter | Short | Description | Default |
|-----------|-------|-------------|---------|
| `--port` | `-p` | Service port | Interactive |
| `--plugins` | | Plugin list (comma-separated) | Interactive |
| `--with-docker` | | Generate Docker config | `true` |
| `--with-k8s` | | Generate Kubernetes config | `false` |
| `--output-dir` | `-o` | Output directory | Current dir |

### Common Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `--skip-install` | Skip dependency installation | `false` |
| `--skip-git` | Skip git initialization | `false` |
| `--dry-run` | Preview only, no actual creation | `false` |

## Generated Structure

### Plugin Structure

```text
plugin-<name>/
├── pom.xml
├── README.md
├── plugin-<name>-api/           # API module (optional)
│   ├── pom.xml
│   └── src/
├── plugin-<name>-core/          # Core module
│   ├── pom.xml
│   └── src/
├── plugin-<name>-web/           # Web frontend (optional)
│   ├── package.json
│   └── src/
└── plugin-<name>-mobile/        # Mobile frontend (optional)
    ├── package.json
    └── src/
```

### Service Structure

```text
brix-service-<name>/
├── pom.xml
├── README.md
├── Dockerfile
├── docker-compose.yml
└── src/
    └── main/
        ├── java/
        │   └── brix/service/<name>/
        │       ├── Application.java
        │       └── config/
        └── resources/
            ├── application.yml
            └── db/migration/
```

## Flyway Prefix Allocation

| Plugin | Prefix |
|--------|--------|
| plugin-user | 001 |
| plugin-contract | 002 |
| plugin-file-center | 003 |
| plugin-notification | 004 |
| plugin-partner-catalog | 005 |
| plugin-service-package | 006 |
| plugin-case-engine | 010-019 |
| plugin-medical-* | 020-029 |

## License

Apache-2.0
