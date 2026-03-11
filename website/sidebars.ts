import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

/**
 * Brix Documentation Sidebar Configuration
 * 
 * Organized following the v3.0.7 Runtime Shell Architecture:
 * - Getting Started: Quick paths to productivity
 * - Concepts: Core architecture understanding
 * - Guides: Practical development tutorials
 * - API: Reference documentation
 */
const sidebars: SidebarsConfig = {
  // Main documentation sidebar
  docsSidebar: [
    // Introduction
    {
      type: 'doc',
      id: 'index',
      label: 'Introduction',
    },

    // Getting Started Section (P1+-11)
    {
      type: 'category',
      label: 'Getting Started',
      collapsed: false,
      items: [
        'getting-started/introduction',
        'getting-started/installation',
        'getting-started/quick-start',
        'getting-started/create-first-plugin',
      ],
    },

    // Core Concepts Section (P1+-12)
    {
      type: 'category',
      label: 'Concepts',
      collapsed: false,
      items: [
        'concepts/runtime-shell',
        'concepts/capability-contract',
        'concepts/plugin-model',
        'concepts/event-model',
        'concepts/host-assembly',
        'concepts/architecture-layers',
      ],
    },

    // Development Guides Section (P1+-13)
    {
      type: 'category',
      label: 'Guides',
      items: [
        'guides/plugin-development',
        'guides/frontend-development',
        'guides/backend-development',
        'guides/testing',
        'guides/deployment',
        'guides/architecture-guard',
      ],
    },

    // Examples
    {
      type: 'category',
      label: 'Examples',
      items: [
        'examples/hello-plugin',
        'examples/crud-plugin',
        'examples/event-driven-plugin',
      ],
    },

    // Migration
    {
      type: 'category',
      label: 'Migration',
      items: [
        'migration/from-v2',
        'migration/changelog',
      ],
    },

    // FAQ
    {
      type: 'doc',
      id: 'faq',
      label: 'FAQ',
    },
  ],

  // API Reference sidebar
  apiSidebar: [
    {
      type: 'doc',
      id: 'api/overview',
      label: 'API Overview',
    },
    {
      type: 'category',
      label: 'TypeScript API',
      items: [
        'api/typescript/runtime-sdk-api',
        'api/typescript/shared-runtime-web',
        'api/typescript/infra-adapters',
      ],
    },
    {
      type: 'category',
      label: 'Java API',
      items: [
        'api/java/runtime-sdk-api',
        'api/java/infra-adapters',
        'api/java/platform-commons',
      ],
    },
    {
      type: 'link',
      label: 'REST API (Swagger)',
      href: '/api/rest',
    },
  ],
};

export default sidebars;
