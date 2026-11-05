/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { describe, expect, it } from 'vitest';
import { createIdempotencyKeys } from './idempotency-keys';

describe('createIdempotencyKeys', () => {
  it('keeps the key of the same content, so a retry gets the first answer', () => {
    const keys = createIdempotencyKeys();

    expect(keys.keyFor({ code: 'acme' })).toBe(keys.keyFor({ code: 'acme' }));
  });

  it('gives other content a key of its own, which the backend would otherwise refuse', () => {
    const keys = createIdempotencyKeys();

    expect(keys.keyFor({ code: 'acme' })).not.toBe(keys.keyFor({ code: 'other' }));
  });

  it('gives the same content a new key once its write went through', () => {
    const keys = createIdempotencyKeys();
    const first = keys.keyFor({ code: 'acme' });

    keys.done();

    expect(keys.keyFor({ code: 'acme' })).not.toBe(first);
  });

  it('makes keys the backend accepts: 1 to 128 visible ASCII characters', () => {
    expect(createIdempotencyKeys().keyFor({})).toMatch(/^[\x21-\x7e]{1,128}$/);
  });
});
