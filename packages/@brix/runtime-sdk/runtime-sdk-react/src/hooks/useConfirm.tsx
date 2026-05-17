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
 * @file useConfirm
 * @description Imperative confirmation dialog hook backed by the UIAdapter
 *              `Modal` component.
 * @module @brix-sdk/runtime-sdk-react/hooks/useConfirm
 * @version 3.3.0
 *
 * [Frontend Stability Reform Plan v1.0 — C-3.3]
 * Replaces ad-hoc `useState(open)` + manual modal JSX boilerplate that
 * historically swallowed the cancel path and forgot to clean up state.
 */

import { useCallback, useMemo, useRef, useState, type ReactNode, type ReactElement } from 'react';
import { useUI } from './useUI';

// ============================================================================
// Confirm Options
// ============================================================================

/** Default confirm button label when caller does not override. */
export const CONFIRM_DEFAULT_OK_TEXT = 'OK' as const;
/** Default cancel button label when caller does not override. */
export const CONFIRM_DEFAULT_CANCEL_TEXT = 'Cancel' as const;

export interface ConfirmOptions {
  /** Modal title shown in the header. */
  title?: ReactNode;
  /** Body content (string, JSX, or element). */
  content?: ReactNode;
  /** Confirm button label. Defaults to {@link CONFIRM_DEFAULT_OK_TEXT}. */
  okText?: string;
  /** Cancel button label. Defaults to {@link CONFIRM_DEFAULT_CANCEL_TEXT}. */
  cancelText?: string;
  /** When true, the modal cannot be dismissed by overlay/escape. */
  blocking?: boolean;
}

// ============================================================================
// Hook Result
// ============================================================================

export interface UseConfirmResult {
  /**
   * Show a confirmation dialog. Resolves with `true` if the user confirms,
   * `false` if they cancel or dismiss.
   */
  confirm(options?: ConfirmOptions): Promise<boolean>;
  /**
   * The modal element. **Must be rendered** somewhere in the component tree
   * for `confirm()` to work.
   */
  ConfirmModal: ReactElement | null;
}

interface InternalState {
  open: boolean;
  options: ConfirmOptions;
}

const INITIAL_STATE: InternalState = Object.freeze({
  open: false,
  options: Object.freeze({}),
});

// ============================================================================
// Implementation
// ============================================================================

/**
 * useConfirm
 *
 * @example
 * ```tsx
 * const { confirm, ConfirmModal } = useConfirm();
 *
 * const onDelete = async () => {
 *   const ok = await confirm({ title: 'Delete user?', content: 'This cannot be undone.' });
 *   if (ok) await http.delete(`/api/users/${id}`);
 * };
 *
 * return (
 *   <>
 *     {ConfirmModal}
 *     <Button onClick={onDelete}>Delete</Button>
 *   </>
 * );
 * ```
 */
export function useConfirm(): UseConfirmResult {
  const { Modal } = useUI();

  const [state, setState] = useState<InternalState>(INITIAL_STATE);
  const resolverRef = useRef<((value: boolean) => void) | null>(null);

  const settle = useCallback((value: boolean): void => {
    const resolver = resolverRef.current;
    resolverRef.current = null;
    setState(INITIAL_STATE);
    if (resolver !== null) {
      resolver(value);
    }
  }, []);

  const confirm = useCallback(
    (options: ConfirmOptions = {}): Promise<boolean> => {
      // If a previous confirm is still open, treat it as cancelled before
      // opening the new one — prevents resolver leaks under rapid invocation.
      if (resolverRef.current !== null) {
        const prev = resolverRef.current;
        resolverRef.current = null;
        prev(false);
      }
      return new Promise<boolean>((resolve) => {
        resolverRef.current = resolve;
        setState({ open: true, options });
      });
    },
    [],
  );

  const handleConfirm = useCallback((): void => settle(true), [settle]);
  const handleCancel = useCallback((): void => settle(false), [settle]);
  const handleClose = useCallback((): void => settle(false), [settle]);

  const ConfirmModal = useMemo<ReactElement | null>(() => {
    const { open, options } = state;
    return (
      <Modal
        open={open}
        title={options.title}
        confirmText={options.okText ?? CONFIRM_DEFAULT_OK_TEXT}
        cancelText={options.cancelText ?? CONFIRM_DEFAULT_CANCEL_TEXT}
        closeOnOverlayClick={!options.blocking}
        closeOnEscape={!options.blocking}
        showCloseButton={!options.blocking}
        onClose={handleClose}
        onConfirm={handleConfirm}
        onCancel={handleCancel}
      >
        {options.content}
      </Modal>
    );
  }, [Modal, state, handleClose, handleConfirm, handleCancel]);

  return { confirm, ConfirmModal };
}
