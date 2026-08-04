// @vitest-environment jsdom
/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import React, { type ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { FirstOwnerInvitationPage } from './FirstOwnerInvitationPage';

const postMock = vi.fn();
const requestMock = vi.fn();
const authState = {
  user: { username: 'owner@example.test' },
  isAuthenticated: true,
  isLoading: false,
  login: vi.fn(),
  logout: vi.fn(),
  refresh: vi.fn(),
  hasPermission: vi.fn(),
  hasAnyPermission: vi.fn(),
  hasAllPermissions: vi.fn(),
  hasRole: vi.fn(),
  hasAnyRole: vi.fn(),
};

vi.mock('@brix-sdk/runtime-sdk-react', () => ({
  useAuth: () => authState,
  useHttp: () => ({
    post: postMock,
    request: requestMock,
  }),
  useTheme: () => ({
    tokens: {
      space: {
        xs: '4px',
        sm: '8px',
        md: '16px',
        lg: '24px',
        xl: '32px',
      },
      shape: {
        md: '6px',
        lg: '8px',
      },
      shadows: {
        md: '0 8px 24px rgba(15, 23, 42, 0.12)',
        lg: '0 16px 40px rgba(15, 23, 42, 0.16)',
        sm: '0 2px 8px rgba(15, 23, 42, 0.10)',
      },
      colors: {
        brand: { primary: '#2563eb' },
        border: { default: '#d1d5db', subtle: '#e5e7eb' },
        surface: { page: '#f8fafc', card: '#ffffff' },
        text: { primary: '#111827', secondary: '#4b5563' },
      },
      typography: {
        fontFamily: 'Inter, sans-serif',
        headlineSmall: { fontSize: '24px', lineHeight: '32px' },
        titleMedium: { fontSize: '16px', lineHeight: '24px' },
        titleLarge: { fontSize: '20px', lineHeight: '28px' },
        bodyMedium: { fontSize: '14px', lineHeight: '22px' },
        bodySmall: { fontSize: '12px', lineHeight: '18px' },
      },
    },
  }),
  useUI: () => ({
    Button: ({ children, disabled, onClick, type }: {
      children: ReactNode;
      disabled?: boolean;
      onClick?: () => void;
      type?: 'button' | 'submit' | 'reset';
    }) => (
      <button type={type ?? 'button'} disabled={disabled} onClick={onClick}>
        {children}
      </button>
    ),
    Input: ({
      label,
      value,
      onChange,
      type,
      disabled,
      helperText,
      'data-testid': dataTestId,
    }: {
      label?: string;
      value?: string;
      onChange?: React.ChangeEventHandler<HTMLInputElement>;
      type?: string;
      disabled?: boolean;
      helperText?: string;
      'data-testid'?: string;
    }) => (
      <label>
        {label}
        <input
          data-testid={dataTestId}
          type={type}
          value={value}
          disabled={disabled}
          onChange={onChange}
        />
        {helperText ? <span>{helperText}</span> : null}
      </label>
    ),
    Card: ({ children }: { children: ReactNode }) => <section>{children}</section>,
    Alert: ({ children, severity, ...rest }: {
      children: ReactNode;
      severity?: string;
    }) => (
      <div role={severity === 'error' || severity === 'warning' ? 'alert' : 'status'} {...rest}>
        {children}
      </div>
    ),
    Icon: ({ name }: { name: string }) => <span aria-hidden="true">{name}</span>,
  }),
}));

beforeEach(() => {
  postMock.mockReset();
  requestMock.mockReset();
  postMock.mockResolvedValue({
    tenantId: 42,
    memberId: 7,
    profileId: 9,
    tenantStatus: 'ACTIVE',
  });
  authState.isAuthenticated = true;
  authState.isLoading = false;
  window.history.replaceState({}, '', '/platform/first-owner/accept?token=raw-token');
});

afterEach(() => {
  cleanup();
});

describe('FirstOwnerInvitationPage', () => {
  it('accepts with the in-memory invitation token and removes token from the URL', async () => {
    render(<FirstOwnerInvitationPage />);

    expect(window.location.search).toBe('');
    expect(screen.getByTestId<HTMLInputElement>('first-owner-invitation-token-input').value)
      .toBe('raw-token');

    fireEvent.click(screen.getByRole('button', { name: '接受并激活' }));

    await waitFor(() => {
      expect(postMock).toHaveBeenCalledWith(
        '/tenant/first-owner-invitations/accept',
        { invitationToken: 'raw-token' },
      );
    });
    expect(await screen.findByTestId('first-owner-accept-success')).toBeDefined();
  });

  it('logs in the invitee identity and accepts when the actor session is missing', async () => {
    authState.isAuthenticated = false;
    requestMock
      .mockResolvedValueOnce({
        data: {
          success: true,
          status: 'SELECT_TENANT',
          identityToken: 'identity-token',
          tenantOptions: [],
        },
        status: 200,
        statusText: 'OK',
        headers: {},
      })
      .mockResolvedValueOnce({
        data: {
          tenantId: 42,
          memberId: 7,
          profileId: 9,
          tenantStatus: 'ACTIVE',
        },
        status: 200,
        statusText: 'OK',
        headers: {},
      });

    render(<FirstOwnerInvitationPage />);

    expect(screen.getByTestId('first-owner-accept-auth-required')).toBeDefined();
    fireEvent.change(screen.getByTestId('first-owner-login-id-input'), {
      target: { value: 'owner@example.test' },
    });
    fireEvent.change(screen.getByTestId('first-owner-password-input'), {
      target: { value: 'owner-password' },
    });
    fireEvent.click(screen.getByRole('button', { name: '接受并激活' }));

    await waitFor(() => {
      expect(requestMock).toHaveBeenNthCalledWith(1, {
        url: '/auth/login/actor',
        method: 'POST',
        data: {
          loginId: 'owner@example.test',
          password: 'owner-password',
        },
      });
    });
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/tenant/first-owner-invitations/accept',
      method: 'POST',
      headers: {
        Authorization: 'Bearer identity-token',
      },
      data: { invitationToken: 'raw-token' },
    });
    expect(postMock).not.toHaveBeenCalled();
    expect(await screen.findByTestId('first-owner-accept-success')).toBeDefined();
  });

  it('does not submit unauthenticated acceptance without invitee credentials', () => {
    authState.isAuthenticated = false;

    render(<FirstOwnerInvitationPage />);

    fireEvent.click(screen.getByRole('button', { name: '接受并激活' }));

    expect(screen.getByTestId('first-owner-accept-error')).toBeDefined();
    expect(requestMock).not.toHaveBeenCalled();
    expect(postMock).not.toHaveBeenCalled();
  });

  it('shows a login-stage error when invitee credentials are invalid', async () => {
    authState.isAuthenticated = false;
    requestMock.mockRejectedValueOnce({
      name: 'HttpError',
      status: 401,
      response: {
        success: false,
        code: 'AUTH_INVALID_CREDENTIALS',
        message: 'Invalid credentials',
      },
    });

    render(<FirstOwnerInvitationPage />);

    fireEvent.change(screen.getByTestId('first-owner-login-id-input'), {
      target: { value: 'owner@example.test' },
    });
    fireEvent.change(screen.getByTestId('first-owner-password-input'), {
      target: { value: 'wrong-password' },
    });
    fireEvent.click(screen.getByRole('button', { name: '接受并激活' }));

    expect(await screen.findByText(/受邀账号或密码不正确/)).toBeDefined();
    expect(requestMock).toHaveBeenCalledTimes(1);
    expect(postMock).not.toHaveBeenCalled();
  });

  it('allows pasting a missing invitation token before acceptance', async () => {
    window.history.replaceState({}, '', '/platform/first-owner/accept');

    render(<FirstOwnerInvitationPage />);

    const tokenInput = screen.getByTestId<HTMLInputElement>('first-owner-invitation-token-input');
    fireEvent.change(tokenInput, { target: { value: 'manual-token' } });
    fireEvent.click(screen.getByRole('button', { name: '接受并激活' }));

    await waitFor(() => {
      expect(postMock).toHaveBeenCalledWith(
        '/tenant/first-owner-invitations/accept',
        { invitationToken: 'manual-token' },
      );
    });
  });
});
