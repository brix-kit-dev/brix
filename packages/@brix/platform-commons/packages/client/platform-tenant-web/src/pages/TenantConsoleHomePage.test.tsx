/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { type ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
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

const switchContext = vi.fn(async () => undefined);
const clinic = { id: '42', code: 'clinic', name: '信和医疗中心', status: 'ACTIVE' };
const hospital = { id: '43', code: 'hospital', name: '第二医院', status: 'ACTIVE' };
const tenantState = {
  role: 'actor',
  currentContext: { role: 'actor', contextId: 'ctx-1', tenantId: '42', tenant: clinic, roleType: 'MEMBER', refId: '7', userId: '21', displayName: 'staff' },
  availableContexts: [
    { role: 'actor', contextId: 'ctx-1', tenantId: '42', tenant: clinic, roleType: 'MEMBER', refId: '7', userId: '21', displayName: 'staff' },
    { role: 'actor', contextId: 'ctx-2', tenantId: '43', tenant: hospital, roleType: 'OWNER', refId: '9', userId: '21', displayName: 'staff' },
  ],
  switchContext,
};

vi.mock('@brix-sdk/runtime-sdk-react', () => ({
  useTenant: () => tenantState,
  useSubmitGuard: () => ({ submitting: false, guard: async (action: () => Promise<unknown>) => action() }),
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
    Alert: ({ children }: { children: ReactNode }) => <div role="alert">{children}</div>,
    Button: ({ children, onClick }: { children: ReactNode; onClick?: () => void }) => (
      <button type="button" onClick={onClick}>{children}</button>
    ),
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

  it('offers the other Actor contexts of the same identity and switches through the runtime', async () => {
    render(<TenantConsoleHomePage />);

    expect(screen.getByText('第二医院（所有者）')).toBeTruthy();
    expect(screen.queryByText('信和医疗中心（成员）')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '切换' }));

    await vi.waitFor(() => expect(switchContext).toHaveBeenCalledWith('ctx-2'));
  });
});
