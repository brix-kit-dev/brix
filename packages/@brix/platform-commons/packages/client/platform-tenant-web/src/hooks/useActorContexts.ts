/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file useActorContexts
 * @description ViewModel of the staff console's tenant switch entry (multi-tenant 5.1 §11.3):
 * the identity's other Actor contexts and a guarded switch through the BFF (T-26). The switch
 * leaves the page; the console comes back with a new session, so nothing of the previous
 * context survives.
 */

import { useCallback, useState } from 'react';
import { useSubmitGuard, useTenant } from '@brix-sdk/runtime-sdk-react';
import type { ActorTenantAccessContext } from '@brix-sdk/runtime-sdk-api-web';

export interface UseActorContextsResult {
  readonly current: ActorTenantAccessContext | null;
  /** Other Actor contexts of the same identity. */
  readonly others: readonly ActorTenantAccessContext[];
  readonly switching: boolean;
  readonly error: string | null;
  switchTo(contextId: string): Promise<void>;
}

export function useActorContexts(): UseActorContextsResult {
  const tenant = useTenant();
  const { submitting, guard } = useSubmitGuard();
  const [error, setError] = useState<string | null>(null);
  const actor = tenant.role === 'actor' ? tenant : null;
  const current = actor?.currentContext ?? null;
  const others = (actor?.availableContexts ?? []).filter((context) => context.contextId !== current?.contextId);

  const switchTo = useCallback(async (contextId: string) => {
    if (!actor) {
      return;
    }
    setError(null);
    await guard(async () => {
      try {
        await actor.switchContext(contextId);
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : 'context_switch_failed');
      }
    });
  }, [actor, guard]);

  return { current, others, switching: submitting, error, switchTo };
}
