import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';

import styles from './index.module.css';

/**
 * Homepage Hero Section
 * 
 * Highlights the core value proposition of Brix Framework:
 * Runtime Shell Architecture for modular enterprise applications.
 */
function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className="container">
        <Heading as="h1" className="hero__title">
          {siteConfig.title}
        </Heading>
        <p className="hero__subtitle">{siteConfig.tagline}</p>
        <p className={styles.heroDescription}>
          Build enterprise applications with <strong>zero infrastructure dependencies</strong>.
          <br />
          Plugins depend only on <strong>Capability Contracts</strong>, not Kafka, Redis, or databases.
        </p>
        <div className={styles.buttons}>
          <Link
            className="button button--secondary button--lg"
            to="/docs/getting-started/quick-start">
            Quick Start - 5 min ⏱️
          </Link>
          <Link
            className="button button--outline button--secondary button--lg"
            to="/docs/concepts/runtime-shell">
            Learn Concepts
          </Link>
        </div>
      </div>
    </header>
  );
}

/**
 * Feature data based on v3.0.7 Runtime Shell Architecture
 */
const FeatureList = [
  {
    title: '🔌 Plugin Architecture',
    description: (
      <>
        Build self-contained business modules that work in any environment.
        Plugins are the smallest deployable and sellable units.
      </>
    ),
    layer: 'Layer 1',
  },
  {
    title: '🎯 Capability Contract',
    description: (
      <>
        Zero infrastructure dependencies in your plugins. Access EventBus,
        StateStore, and more through runtime-provided contracts.
      </>
    ),
    layer: 'Layer 2A',
  },
  {
    title: '📦 Module Federation',
    description: (
      <>
        First-class micro-frontend support with shared runtime. No React
        multi-instance issues, no Context fragmentation.
      </>
    ),
    layer: 'Layer 2B',
  },
  {
    title: '🚀 Ultra-Thin Host',
    description: (
      <>
        Pure configuration-driven assembly shell with zero business logic.
        Just pom.xml + YAML + Boot class (&lt;30 lines).
      </>
    ),
    layer: 'Layer 3',
  },
  {
    title: '🔄 Event-Driven',
    description: (
      <>
        Loose coupling through governed event bus. Domain Events for internal,
        Integration Events for cross-plugin communication.
      </>
    ),
    layer: 'Cross-cutting',
  },
  {
    title: '📱 Multi-Platform',
    description: (
      <>
        Web, Mobile (React Native), and Backend (Java/Spring) support.
        Single architecture, multiple deployment targets.
      </>
    ),
    layer: 'All Layers',
  },
];

/**
 * Feature Card Component
 */
function Feature({title, description, layer}: {
  title: string;
  description: React.ReactNode;
  layer: string;
}) {
  return (
    <div className={clsx('col col--4')}>
      <div className={clsx('card', styles.featureCard)}>
        <div className="card__header">
          <Heading as="h3">{title}</Heading>
          <span className={styles.layerBadge}>{layer}</span>
        </div>
        <div className="card__body">
          <p>{description}</p>
        </div>
      </div>
    </div>
  );
}

/**
 * Architecture Overview Section
 */
function ArchitectureOverview() {
  return (
    <section className={styles.architectureSection}>
      <div className="container">
        <Heading as="h2" className="text--center margin-bottom--lg">
          🏗️ Runtime Shell Architecture
        </Heading>
        <div className={styles.architectureDiagram}>
          <pre className={styles.asciiDiagram}>
{`┌─────────────────────────────────────────────────────────────────────┐
│  Layer 3: Host (Ultra-Thin Assembly Shell)                          │
│  └── Pure configuration: pom.xml + YAML + Boot class (< 30 lines)  │
├─────────────────────────────────────────────────────────────────────┤
│  Layer 2: Capability Layer                                          │
│  ├── 2A: Contracts (runtime-sdk-api) — Pure interfaces             │
│  ├── 2B: Shared Runtime (@brix/shared-runtime-web)                  │
│  └── 2C: Implementations (infra-adapters, platform-commons)         │
├─────────────────────────────────────────────────────────────────────┤
│  Layer 1: Plugins (Business Modules)                                │
│  └── Only depends on Layer 2A Capability Contracts                  │
├─────────────────────────────────────────────────────────────────────┤
│  Layer 0: Infrastructure (Hidden from plugins)                      │
│  └── Kafka, Redis, PostgreSQL, MinIO, etc.                          │
└─────────────────────────────────────────────────────────────────────┘`}
          </pre>
        </div>
        <div className="text--center margin-top--lg">
          <Link
            className="button button--primary button--lg"
            to="/docs/concepts/architecture-layers">
            Explore Architecture →
          </Link>
        </div>
      </div>
    </section>
  );
}

/**
 * Quick Start Code Example
 */
function QuickStartSection() {
  return (
    <section className={styles.quickStartSection}>
      <div className="container">
        <div className="row">
          <div className="col col--6">
            <Heading as="h2">🚀 Create Your First Plugin</Heading>
            <p>
              Get started in under 5 minutes. The Brix CLI scaffolds a complete
              plugin with frontend, backend, and architecture tests.
            </p>
            <pre className={styles.codeBlock}>
{`# Create a new plugin
pnpm create @brix/brix plugin my-plugin

# Navigate and start development
cd my-plugin
pnpm dev`}
            </pre>
          </div>
          <div className="col col--6">
            <Heading as="h2">🎯 Capability-First Development</Heading>
            <p>
              Your plugin code never touches infrastructure directly.
              Everything goes through Runtime Capability Contracts.
            </p>
            <pre className={styles.codeBlock}>
{`// Get capabilities - no infrastructure deps!
const eventBus = context.getCapability(EventBusCapability);
const http = context.getCapability(HttpCapability);

// Subscribe to events from other plugins
eventBus.subscribe('user.created', handler);

// Publish events for others to consume
eventBus.publish('order.completed', data);`}
            </pre>
          </div>
        </div>
      </div>
    </section>
  );
}

/**
 * Main Homepage Component
 */
export default function Home(): JSX.Element {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title={`${siteConfig.title} - Documentation`}
      description="Brix is a Runtime Shell framework for building modular enterprise applications with zero infrastructure dependencies."
    >
      <HomepageHeader />
      <main>
        <section className={styles.features}>
          <div className="container">
            <Heading as="h2" className="text--center margin-bottom--lg">
              ✨ Why Brix?
            </Heading>
            <div className="row">
              {FeatureList.map((props, idx) => (
                <Feature key={idx} {...props} />
              ))}
            </div>
          </div>
        </section>
        <ArchitectureOverview />
        <QuickStartSection />
      </main>
    </Layout>
  );
}
