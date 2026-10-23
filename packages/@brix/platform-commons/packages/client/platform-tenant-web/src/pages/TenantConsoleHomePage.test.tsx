/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { type ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TenantConsoleHomePage } from './TenantConsoleHomePage';

const authCapability = {
  getCurrentTenant: () => ({ id: '42', name: '信和医疗中心' }),
  getVerifiedActorContext: () => ({
    kind: 'actor',
    subjectId: '21',
    sessionId: 'ctx-1',
    tenantId: '42',
    permissions: ['tenant:profile:read', 'app-booking:template:read'],
  }),
};

vi.mock('@brix-sdk/runtime-sdk-react', () => ({
  useAuth: () => ({
    user: { id: '21', username: 'staff', email: 'staff@example.test', roles: ['MEMBER'], permissions: [] },
    isAuthenticated: true,
  }),
  useRuntimeContext: () => ({ getCapability: () => authCapability }),
  useUI: () => ({
    Card: ({ children }: { children: ReactNode }) => <section>{children}</section>,
    Stack: ({ children }: { children: ReactNode }) => <div>{children}</div>,
    Typography: ({ children }: { children: ReactNode }) => <p>{children}</p>,
    Tag: ({ children }: { children: ReactNode }) => <span data-testid="permission">{children}</span>,
  }),
}));

describe('TenantConsoleHomePage', () => {
  it('shows the tenant, identity and effective permissions of the BFF session', () => {
    render(<TenantConsoleHomePage />);

    expect(screen.getByText('信和医疗中心')).toBeTruthy();
    expect(screen.getByText('staff@example.test')).toBeTruthy();
    expect(screen.getByText('成员类型：MEMBER')).toBeTruthy();
    expect(screen.getAllByTestId('permission').map(tag => tag.textContent))
      .toEqual(['app-booking:template:read', 'tenant:profile:read']);
  });

  it('has no credential field (root BRX-RS-UI-018, identity-auth IA-2)', () => {
    const { container } = render(<TenantConsoleHomePage />);

    expect(container.querySelector('input, textarea, form')).toBeNull();
  });
});
