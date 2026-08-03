/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/* @vitest-environment jsdom */

import { render, screen } from '@testing-library/react';
import type { ChangeEventHandler, ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { FirstOwnerInvitationDialog } from './FirstOwnerInvitationDialog';
import type { PlatformTenantDto } from '../types';

const invitationMocks = vi.hoisted(() => ({
  loadCurrent: vi.fn<[], Promise<null>>(),
  create: vi.fn(),
  resend: vi.fn(),
  revoke: vi.fn(),
}));

vi.mock('@brix-sdk/runtime-sdk-react', () => ({
  useTheme: () => ({ tokens: designTokens() }),
}));

vi.mock('../hooks/useFirstOwnerInvitation', () => ({
  useFirstOwnerInvitation: () => ({
    loading: false,
    error: null,
    current: null,
    loadCurrent: invitationMocks.loadCurrent,
    create: invitationMocks.create,
    resend: invitationMocks.resend,
    revoke: invitationMocks.revoke,
  }),
}));

vi.mock('../internal/ui-kit', () => ({
  useUIStrict: () => ({
    Modal: ({ open, title, children }: ModalProps) =>
      open ? (
        <section aria-label={title} role="dialog">
          {children}
        </section>
      ) : null,
    Stack: ({ children }: ChildrenProps) => <div>{children}</div>,
    Input: ({ label, value, onChange, type = 'text' }: InputProps) => (
      <label>
        {label}
        <input
          aria-label={label}
          type={type}
          value={value}
          onChange={onChange}
        />
      </label>
    ),
    Alert: ({ children }: ChildrenProps) => <div>{children}</div>,
    Button: ({ children }: ChildrenProps) => <button type="button">{children}</button>,
    message: {
      success: vi.fn(),
      error: vi.fn(),
    },
  }),
}));

describe('FirstOwnerInvitationDialog', () => {
  it('does not ask platform super admins to enter an invitation URL', () => {
    invitationMocks.loadCurrent.mockResolvedValueOnce(null);

    render(
      <FirstOwnerInvitationDialog
        open
        tenant={tenant()}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByLabelText('Invitee email')).toBeTruthy();
    expect(screen.getByLabelText('Locale')).toBeTruthy();
    expect(screen.queryByLabelText('Invitation entry URL')).toBeNull();
  });
});

interface ChildrenProps {
  children?: ReactNode;
}

interface ModalProps extends ChildrenProps {
  open: boolean;
  title: string;
}

interface InputProps {
  label: string;
  value: string;
  type?: string;
  onChange: ChangeEventHandler<HTMLInputElement>;
}

function tenant(): PlatformTenantDto {
  return {
    id: 'tenant-42',
    code: 'shinwa-medical',
    name: '信和医疗中心',
    status: 'PENDING_ACTIVATION',
    createdAt: '2026-08-03T00:00:00Z',
    updatedAt: '2026-08-03T00:00:00Z',
    ownerIdentityId: null,
    memberCount: 0,
    quotaUsed: null,
    quotaLimit: null,
    licenseStatus: null,
    defaultLocale: 'zh-CN',
    defaultTimezone: 'Asia/Tokyo',
    defaultTheme: null,
  };
}

function designTokens() {
  return {
    space: { md: 12 },
    colors: {
      text: {
        secondary: '#53616f',
      },
    },
  };
}
