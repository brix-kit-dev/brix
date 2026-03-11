---
id: index
title: Brix Framework Documentation
sidebar_label: Overview
slug: /
---

# Brix Framework Documentation

Welcome to the Brix Framework documentation. Brix is a **Runtime Shell framework** for building modular, pluggable enterprise applications with **zero infrastructure dependencies**.

## What is Brix?

Brix implements the **Runtime Shell Architecture** (v3.0.7), a design pattern that separates your business logic (plugins) from infrastructure concerns through **Capability Contracts**.

```
┌─────────────────────────────────────────────────────────────────────┐
│  Layer 3: Host (Ultra-Thin Assembly Shell)                          │
├─────────────────────────────────────────────────────────────────────┤
│  Layer 2: Capability Layer (Contracts + Implementations)            │
├─────────────────────────────────────────────────────────────────────┤
│  Layer 1: Plugins (Your Business Modules)                           │
├─────────────────────────────────────────────────────────────────────┤
│  Layer 0: Infrastructure (Hidden - Kafka, Redis, PostgreSQL)        │
└─────────────────────────────────────────────────────────────────────┘
```

## Key Benefits

| Benefit | Description |
|---------|-------------|
| **Infrastructure Agnostic** | Plugins never import Kafka, Redis, or database clients |
| **Portable** | Same plugin runs in Standalone or Embedded mode |
| **Testable** | Mock capabilities easily, no infrastructure needed |
| **Governable** | Architecture Guard enforces 13 red-line rules |

## Quick Links

### Getting Started
- [Introduction](./getting-started/introduction) - Understand the core concepts
- [Installation](./getting-started/installation) - Set up your development environment
- [Quick Start](./getting-started/quick-start) - Create your first plugin in 5 minutes
- [Create First Plugin](./getting-started/create-first-plugin) - Step-by-step tutorial

### Core Concepts
- [Runtime Shell](./concepts/runtime-shell) - The core abstraction
- [Capability Contract](./concepts/capability-contract) - Infrastructure-agnostic interfaces
- [Plugin Model](./concepts/plugin-model) - Business module design
- [Event Model](./concepts/event-model) - Domain and Integration events

### Development Guides
- [Plugin Development](./guides/plugin-development) - Complete plugin tutorial
- [Frontend Development](./guides/frontend-development) - Web and Mobile UI
- [Backend Development](./guides/backend-development) - Java/Spring services
- [Architecture Guard](./guides/architecture-guard) - Enforce design constraints

## Version

This documentation covers **Brix Framework v3.x** based on the v3.0.7 Runtime Shell Architecture Blueprint.

## Getting Help

- **GitHub Discussions**: [Ask questions](https://github.com/brix-framework/brix/discussions)
- **Discord**: [Join our community](https://discord.gg/brix-framework)
- **Bug Reports**: [File an issue](https://github.com/brix-framework/brix/issues/new?template=bug_report.md)
