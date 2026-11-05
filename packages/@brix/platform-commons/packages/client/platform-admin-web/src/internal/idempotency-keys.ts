/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file The key of a write the backend replays (root BRX-RS-MANIFEST-018).
 *
 * A retry of the same content keeps its key, so the backend gives the first
 * answer again and nothing happens twice. Other content is another change and
 * gets a key of its own: the backend refuses a key reused with other content.
 */

/** Keys of the writes of one form. */
export interface IdempotencyKeys {
  /** Returns the key of this content: the same one until the content changes. */
  keyFor(content: unknown): string;
  /** Forgets the key once its write went through. */
  done(): void;
}

function randomKey(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}

export function createIdempotencyKeys(random: () => string = randomKey): IdempotencyKeys {
  let current: { content: string; key: string } | null = null;
  return {
    keyFor(content) {
      const text = JSON.stringify(content);
      if (current?.content !== text) {
        current = { content: text, key: random() };
      }
      return current.key;
    },
    done() {
      current = null;
    },
  };
}
