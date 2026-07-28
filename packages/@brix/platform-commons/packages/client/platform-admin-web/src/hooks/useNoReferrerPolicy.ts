/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useEffect } from 'react';

/**
 * Applies a route-local no-referrer policy for setup and invitation flows.
 */
export function useNoReferrerPolicy(): void {
  useEffect(() => {
    let created = false;
    let meta = document.querySelector<HTMLMetaElement>('meta[name="referrer"]');
    const previousContent = meta?.getAttribute('content') ?? null;

    if (!meta) {
      meta = document.createElement('meta');
      meta.setAttribute('name', 'referrer');
      document.head.appendChild(meta);
      created = true;
    }

    meta.setAttribute('content', 'no-referrer');

    return () => {
      if (!meta) return;
      if (created) {
        meta.remove();
      } else if (previousContent === null) {
        meta.removeAttribute('content');
      } else {
        meta.setAttribute('content', previousContent);
      }
    };
  }, []);
}
