import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

/**
 * Brix Framework Documentation Site Configuration
 * 
 * This configuration follows the v3.0.7 Runtime Shell Architecture Blueprint,
 * documenting the Layer 0-4 architecture model, Capability Contract patterns,
 * and Plugin development guidelines.
 * 
 * @see https://docs.brix.dev
 */
const config: Config = {
  title: 'Brix Framework',
  tagline: 'Runtime Shell for Modular Enterprise Applications',
  favicon: 'img/favicon.ico',

  // Production URL configuration
  url: 'https://docs.brix.dev',
  baseUrl: '/',

  // GitHub Pages deployment configuration
  organizationName: 'brix-framework',
  projectName: 'brix',
  deploymentBranch: 'gh-pages',
  trailingSlash: false,

  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',

  // Internationalization configuration
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  // Mermaid diagram support for architecture diagrams
  markdown: {
    mermaid: true,
  },
  themes: ['@docusaurus/theme-mermaid'],

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          editUrl: 'https://github.com/brix-framework/brix/tree/main/website/',
          showLastUpdateAuthor: true,
          showLastUpdateTime: true,
          // Version management for future releases
          versions: {
            current: {
              label: '3.x (Current)',
              path: '',
            },
          },
        },
        blog: {
          showReadingTime: true,
          editUrl: 'https://github.com/brix-framework/brix/tree/main/website/',
          blogSidebarTitle: 'All posts',
          blogSidebarCount: 'ALL',
        },
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    // Social card for sharing
    image: 'img/brix-social-card.png',
    
    // Announcement bar for important notices
    announcementBar: {
      id: 'v3_release',
      content: '🎉 Brix v3.0 with Runtime Shell Architecture is now available! <a href="/docs/getting-started/quick-start">Get started →</a>',
      backgroundColor: '#6366F1',
      textColor: '#ffffff',
      isCloseable: true,
    },

    navbar: {
      title: 'Brix',
      logo: {
        alt: 'Brix Logo',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docsSidebar',
          position: 'left',
          label: 'Docs',
        },
        {
          type: 'docSidebar',
          sidebarId: 'apiSidebar',
          position: 'left',
          label: 'API',
        },
        {to: '/blog', label: 'Blog', position: 'left'},
        // Version selector
        {
          type: 'docsVersionDropdown',
          position: 'right',
          dropdownActiveClassDisabled: true,
        },
        {
          href: 'https://github.com/brix-framework/brix',
          label: 'GitHub',
          position: 'right',
        },
        {
          href: 'https://discord.gg/brix-framework',
          label: 'Discord',
          position: 'right',
        },
      ],
    },

    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {label: 'Getting Started', to: '/docs/getting-started/introduction'},
            {label: 'Concepts', to: '/docs/concepts/runtime-shell'},
            {label: 'Guides', to: '/docs/guides/plugin-development'},
            {label: 'API Reference', to: '/docs/api/overview'},
          ],
        },
        {
          title: 'Community',
          items: [
            {label: 'Discord', href: 'https://discord.gg/brix-framework'},
            {label: 'GitHub Discussions', href: 'https://github.com/brix-framework/brix/discussions'},
            {label: 'Twitter', href: 'https://twitter.com/brix_framework'},
          ],
        },
        {
          title: 'More',
          items: [
            {label: 'Blog', to: '/blog'},
            {label: 'GitHub', href: 'https://github.com/brix-framework/brix'},
            {label: 'npm Registry', href: 'https://www.npmjs.com/org/brix'},
            {label: 'Maven Central', href: 'https://search.maven.org/search?q=g:io.brix'},
          ],
        },
        {
          title: 'Legal',
          items: [
            {label: 'License', href: 'https://github.com/brix-framework/brix/blob/main/LICENSE'},
            {label: 'Code of Conduct', href: 'https://github.com/brix-framework/brix/blob/main/CODE_OF_CONDUCT.md'},
            {label: 'Security Policy', href: 'https://github.com/brix-framework/brix/blob/main/SECURITY.md'},
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Brix Framework Contributors. Built with Docusaurus.`,
    },

    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java', 'bash', 'json', 'yaml', 'typescript'],
    },

    // Mermaid configuration for architecture diagrams
    mermaid: {
      theme: {light: 'neutral', dark: 'dark'},
    },

    // Algolia DocSearch configuration (P1+-17)
    // Note: Apply for free DocSearch at https://docsearch.algolia.com/apply/
    algolia: {
      appId: 'BRIX_DOCS_APP_ID',
      apiKey: 'BRIX_DOCS_API_KEY',
      indexName: 'brix',
      contextualSearch: true,
      searchPagePath: 'search',
      // Optional: see doc section https://docusaurus.io/docs/search#connecting-algolia
      insights: false,
    },

    // Table of contents configuration
    tableOfContents: {
      minHeadingLevel: 2,
      maxHeadingLevel: 4,
    },
  } satisfies Preset.ThemeConfig,

  // Plugin configuration
  plugins: [
    // TypeDoc integration for TypeScript API docs (P1+-14)
    [
      'docusaurus-plugin-typedoc',
      {
        id: 'runtime-sdk-api',
        entryPoints: ['../packages/@brix/runtime-sdk'],
        tsconfig: '../tsconfig.base.json',
        out: 'api/typescript/runtime-sdk',
        sidebar: {
          categoryLabel: 'Runtime SDK',
          position: 0,
        },
      },
    ],
  ],
};

export default config;
