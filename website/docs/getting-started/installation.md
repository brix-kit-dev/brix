---
id: installation
title: Installation
sidebar_label: Installation
sidebar_position: 2
---

# Installation

This guide covers setting up your development environment for Brix Framework.

## Prerequisites

### Required

| Tool | Version | Purpose |
|------|---------|---------|
| **Node.js** | >= 18.0.0 | Frontend runtime |
| **pnpm** | >= 8.0.0 | Package manager |
| **Java** | 17+ | Backend runtime |
| **Maven** | 3.8+ | Java build tool |

### Optional

| Tool | Version | Purpose |
|------|---------|---------|
| Docker | 20+ | Local infrastructure (Kafka, Redis) |
| VS Code | Latest | Recommended IDE |

## Step 1: Verify Prerequisites

```bash
# Check Node.js
node --version
# Expected: v18.x.x or higher

# Check pnpm
pnpm --version
# Expected: 8.x.x or higher

# Check Java
java --version
# Expected: openjdk 17.x.x or higher

# Check Maven
mvn --version
# Expected: Apache Maven 3.8.x or higher
```

## Step 2: Install pnpm (if needed)

```bash
# Using npm
npm install -g pnpm

# Or using Corepack (Node.js 16.13+)
corepack enable
corepack prepare pnpm@latest --activate
```

## Step 3: Install Brix CLI

```bash
# Install the scaffold CLI globally
pnpm add -g @brix/create-brix
```

Verify installation:

```bash
create-brix --version
# Expected: 3.x.x
```

## Step 4: Configure IDE

### VS Code (Recommended)

Install these extensions:
- **ESLint** - JavaScript/TypeScript linting
- **Prettier** - Code formatting
- **Extension Pack for Java** - Java development
- **Spring Boot Extension Pack** - Spring Boot support

Workspace settings (`.vscode/settings.json`):

```json
{
  "editor.formatOnSave": true,
  "editor.defaultFormatter": "esbenp.prettier-vscode",
  "[java]": {
    "editor.defaultFormatter": "redhat.java"
  },
  "typescript.preferences.importModuleSpecifier": "relative"
}
```

### IntelliJ IDEA

1. Install Lombok plugin
2. Enable annotation processing: Settings → Build → Compiler → Annotation Processors
3. Import as Maven project

## Step 5: Set Up Local Infrastructure (Optional)

For development with actual infrastructure:

```bash
# Clone the brix repository
git clone https://github.com/brix-framework/brix.git
cd brix

# Start local infrastructure
docker-compose -f docker/docker-compose.dev.yml up -d
```

This starts:
- Kafka (localhost:9092)
- Redis (localhost:6379)
- PostgreSQL (localhost:5432)

:::tip
You can develop plugins without local infrastructure by using the `simple` adapter, which provides in-memory implementations of all capabilities.
:::

## Troubleshooting

### pnpm install fails with peer dependency errors

```bash
# Use --shamefully-hoist for compatibility
pnpm install --shamefully-hoist
```

### Java version mismatch

Ensure `JAVA_HOME` points to Java 17+:

```bash
# Windows
set JAVA_HOME=C:\Program Files\Java\jdk-17

# macOS/Linux
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
```

### Maven cannot find dependencies

Add Brix Maven repository if packages aren't on Maven Central yet:

```xml
<repositories>
  <repository>
    <id>brix-releases</id>
    <url>https://maven.pkg.github.com/brix-framework/brix</url>
  </repository>
</repositories>
```

## Next Steps

Now that your environment is set up:

1. [Quick Start](./quick-start) - Create your first plugin
2. [Create First Plugin](./create-first-plugin) - Detailed tutorial
