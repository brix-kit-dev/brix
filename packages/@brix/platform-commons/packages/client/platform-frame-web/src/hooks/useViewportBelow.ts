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
 * @file useViewportBelow Hook
 * @description Tracks whether the viewport is narrower than a breakpoint
 * @module @brix-sdk/platform-frame-web/hooks/useViewportBelow
 *
 * Shell layouts use it with a design token breakpoint (for example
 * `tokens.breakpoints.md`) to switch to their compact phone layout.
 * Responsive folding is a Shell concern (frontend 1.2 §3.4), so the hook
 * works from props and tokens only and needs no layout capability.
 *
 * @since 3.2.0
 */

import { useEffect, useState } from 'react';

function mediaQuery(widthPx: number): string {
  return `(max-width: ${Math.max(widthPx - 1, 0)}px)`;
}

function matches(query: string): boolean {
  return typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia(query).matches;
}

/**
 * Returns whether the viewport is narrower than `widthPx`.
 *
 * Environments without `matchMedia` (server rendering, some test
 * environments) report `false`, which keeps the desktop layout.
 *
 * @param widthPx - breakpoint width in pixels; the viewport is "below" when it is narrower
 * @returns `true` while the viewport is narrower than the breakpoint
 *
 * @since 3.2.0
 */
export function useViewportBelow(widthPx: number): boolean {
  const query = mediaQuery(widthPx);
  const [below, setBelow] = useState(() => matches(query));

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return undefined;
    }
    const media = window.matchMedia(query);
    const update = () => setBelow(media.matches);
    update();
    media.addEventListener('change', update);
    return () => media.removeEventListener('change', update);
  }, [query]);

  return below;
}

export default useViewportBelow;
