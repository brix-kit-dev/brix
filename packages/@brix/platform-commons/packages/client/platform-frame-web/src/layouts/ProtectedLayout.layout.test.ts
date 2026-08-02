/* @vitest-environment jsdom */

import React, { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { describe, expect, it, vi } from 'vitest';
import { ProtectedLayout } from './ProtectedLayout';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean })
  .IS_REACT_ACT_ENVIRONMENT = true;

vi.mock('@brix-sdk/runtime-sdk-react', () => ({
  useTheme: () => ({
    tokens: {
      colors: {
        brand: {
          primary: '#2563eb',
          primaryContrast: '#ffffff',
        },
        surface: {
          page: '#f8fafc',
          card: '#ffffff',
        },
        text: {
          primary: '#0f172a',
          secondary: '#475569',
        },
        border: {
          subtle: '#e2e8f0',
          default: '#cbd5e1',
        },
        layout: {
          headerText: '#0f172a',
        },
        status: {
          error: '#dc2626',
        },
      },
      typography: {
        fontFamily: 'Inter, sans-serif',
      },
      shape: {
        lg: '12px',
        full: '999px',
      },
      space: {
        xs: '4px',
        sm: '8px',
      },
      motion: {
        durationShort: '120ms',
        easing: 'ease',
      },
    },
  }),
  useUIOptional: () => undefined,
}));

describe('ProtectedLayout geometry', () => {
  it('uses one configured frame gap for sidebar, header and content alignment', () => {
    const host = document.createElement('div');
    document.body.appendChild(host);
    let root: Root | null = null;

    act(() => {
      root = createRoot(host);
      root.render(
        React.createElement(
          ProtectedLayout,
          {
            dimensions: {
              sidebarWidth: 256,
              sidebarCollapsedWidth: 80,
              headerHeight: 64,
              contentMargin: 8,
            },
            branding: { appName: 'Brix' },
            user: { name: 'Platform User' },
            menuItems: [
              {
                key: 'platform',
                label: 'Platform',
                path: '/platform',
              },
            ],
            currentPath: '/platform',
            onNavigate: vi.fn(),
            onLogout: vi.fn(),
          },
          React.createElement('section', null, 'content'),
        ),
      );
    });

    const sidebar = host.querySelector('aside') as HTMLElement;
    const header = host.querySelector('header') as HTMLElement;
    const content = host.querySelector('main') as HTMLElement;

    expect(sidebar.style.top).toBe('8px');
    expect(sidebar.style.left).toBe('8px');
    expect(sidebar.style.bottom).toBe('8px');
    expect(header.style.top).toBe('8px');
    expect(header.style.left).toBe('272px');
    expect(header.style.right).toBe('8px');
    expect(content.style.top).toBe('80px');
    expect(content.style.left).toBe('272px');
    expect(content.style.right).toBe('8px');
    expect(content.style.bottom).toBe('8px');

    act(() => {
      root?.unmount();
    });
    host.remove();
  });
});
