/**
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file ActorContextSwitcher
 * @description Actor-only context switcher using useTenant() Phase 3 contract.
 */

import { useState } from 'react';
import { useTenant, useUIOptional } from '@brix-sdk/runtime-sdk-react';

export interface ActorContextSwitcherProps {
  readonly onBeforeSwitch?: () => void | Promise<void>;
  readonly onAfterSwitch?: () => void | Promise<void>;
  readonly onError?: (error: Error) => void;
}

/**
 * Renders an actor-only context switcher.
 */
export function ActorContextSwitcher(props: ActorContextSwitcherProps): JSX.Element | null {
  const tenantState = useTenant();
  const ui = useUIOptional();
  const [switching, setSwitching] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  if (tenantState.role !== 'actor') {
    return null;
  }

  async function switchTo(contextId: string) {
    if (tenantState.role !== 'actor' || contextId === tenantState.currentContext.contextId) {
      return;
    }
    setSwitching(true);
    setError(null);
    try {
      await props.onBeforeSwitch?.();
      await tenantState.switchContext(contextId);
      await props.onAfterSwitch?.();
    } catch (caught) {
      const nextError = caught instanceof Error ? caught : new Error(String(caught));
      setError(nextError);
      props.onError?.(nextError);
    } finally {
      setSwitching(false);
    }
  }

  const options = tenantState.availableContexts.map((context) => ({
    value: context.contextId,
    label: `${context.displayName} / ${context.tenant.name}`,
  }));

  if (ui?.Select) {
    return (
      <div>
        <ui.Select
          label="访问上下文"
          value={tenantState.currentContext.contextId}
          options={options}
          onChange={(value) => void switchTo(String(value))}
          disabled={switching || options.length <= 1}
          fullWidth
        />
        {error ? <div role="alert">{error.message}</div> : null}
      </div>
    );
  }

  return (
    <label>
      访问上下文
      <select
        value={tenantState.currentContext.contextId}
        onChange={(event) => void switchTo(event.target.value)}
        disabled={switching || options.length <= 1}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {error ? <span role="alert">{error.message}</span> : null}
    </label>
  );
}
