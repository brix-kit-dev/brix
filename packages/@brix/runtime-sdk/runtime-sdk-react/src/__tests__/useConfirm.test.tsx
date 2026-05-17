/**
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * @file useConfirm — Smoke Tests (Stability Reform v1.0 — C-3.3)
 * @description Validates promise-based confirm/cancel resolution via a fake
 *              Modal that exposes its callback handles to the test.
 */

import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, act } from '@testing-library/react';
import type { ModalProps, UIAdapter } from '@brix-sdk/runtime-sdk-api-web';
import { RuntimeContextProvider } from '../context';
import { useConfirm, type ConfirmOptions, type UseConfirmResult } from '../hooks/useConfirm';

// ============================================================================
// Test Doubles
// ============================================================================

/**
 * Captures the most recent Modal props so tests can drive onConfirm / onCancel
 * imperatively without rendering a real DOM Modal.
 */
const lastModalProps: { current: ModalProps | null } = { current: null };

const FakeModal: React.FC<ModalProps> = (props) => {
  lastModalProps.current = props;
  return null;
};

function createTestUIAdapter(): UIAdapter {
  // Cast through unknown — we only exercise the `Modal` slot.
  return { Modal: FakeModal } as unknown as UIAdapter;
}

function createMockRuntimeContext(adapter: UIAdapter) {
  return {
    moduleId: 'test',
    tenantId: 'test',
    getCapability: <T,>(type: symbol): T | undefined => {
      if (type === Symbol.for('UICapability')) {
        return adapter as T;
      }
      return undefined;
    },
  };
}

/**
 * Mount a host component that exercises useConfirm and exposes its API to
 * the test through a ref-like handle. The host is responsible for rendering
 * `ConfirmModal` so the FakeModal effect runs.
 */
function renderUseConfirm(): { handle: { current: UseConfirmResult | null } } {
  const handle: { current: UseConfirmResult | null } = { current: null };
  const adapter = createTestUIAdapter();
  const ctx = createMockRuntimeContext(adapter);

  const Host: React.FC = () => {
    const api = useConfirm();
    handle.current = api;
    return api.ConfirmModal;
  };

  render(
    React.createElement(RuntimeContextProvider, { value: ctx }, React.createElement(Host)),
  );
  return { handle };
}

function callConfirm(
  handle: { current: UseConfirmResult | null },
  options?: ConfirmOptions,
): Promise<boolean> {
  let promise!: Promise<boolean>;
  act(() => {
    promise = handle.current!.confirm(options);
  });
  return promise;
}

// ============================================================================
// Tests
// ============================================================================

describe('useConfirm', () => {
  it('exposes a ConfirmModal element and a confirm() function', () => {
    const { handle } = renderUseConfirm();
    expect(typeof handle.current?.confirm).toBe('function');
    expect(handle.current?.ConfirmModal).not.toBeNull();
  });

  it('opens the modal when confirm() is called', () => {
    const { handle } = renderUseConfirm();
    void callConfirm(handle, { title: 'Delete?' });
    expect(lastModalProps.current?.open).toBe(true);
    expect(lastModalProps.current?.title).toBe('Delete?');
  });

  it('resolves with true when the user confirms', async () => {
    const { handle } = renderUseConfirm();
    const promise = callConfirm(handle, { title: 'Delete?' });

    await act(async () => {
      lastModalProps.current?.onConfirm?.();
    });

    await expect(promise).resolves.toBe(true);
  });

  it('resolves with false when the user cancels', async () => {
    const { handle } = renderUseConfirm();
    const promise = callConfirm(handle);

    await act(async () => {
      lastModalProps.current?.onCancel?.();
    });

    await expect(promise).resolves.toBe(false);
  });

  it('resolves with false when the modal is dismissed (onClose)', async () => {
    const { handle } = renderUseConfirm();
    const promise = callConfirm(handle);

    await act(async () => {
      lastModalProps.current?.onClose();
    });

    await expect(promise).resolves.toBe(false);
  });

  it('resolves the previous promise with false when confirm() is called twice', async () => {
    const { handle } = renderUseConfirm();
    const firstPromise = callConfirm(handle, { title: 'first' });
    const secondPromise = callConfirm(handle, { title: 'second' });

    await expect(firstPromise).resolves.toBe(false);

    await act(async () => {
      lastModalProps.current?.onConfirm?.();
    });
    await expect(secondPromise).resolves.toBe(true);
  });

  it('disables overlay/escape/close-button dismissal when blocking is true', () => {
    const { handle } = renderUseConfirm();
    void callConfirm(handle, { title: 'Blocking', blocking: true });

    expect(lastModalProps.current?.closeOnOverlayClick).toBe(false);
    expect(lastModalProps.current?.closeOnEscape).toBe(false);
    expect(lastModalProps.current?.showCloseButton).toBe(false);
  });
});
