/**
 * @file One-time link tokens
 * @description Setup and invitation links carry their one-time token in the URL fragment
 * (`#token=…`), which browsers never send to a server or in a Referer. The page takes it into
 * memory before anything renders and removes it from the address bar and the history entry
 * (frontend BRX-FE-ROUTE-004, FE-7; identity-auth BRX-IA-ONBOARD-003).
 */

const TOKEN = /^[A-Za-z0-9_-]{16,512}$/;

/** The token of a `#token=…` fragment, or `null` when there is none or it is malformed. */
export function readLinkToken(hash: string): string | null {
  if (!hash || hash.length < 2) {
    return null;
  }
  const value = new URLSearchParams(hash.slice(1)).get('token');
  return value && TOKEN.test(value) ? value : null;
}

/** Reads the fragment token and drops the fragment from the current history entry. */
export function takeLinkToken(
  location: Pick<Location, 'hash' | 'pathname' | 'search'>,
  history: Pick<History, 'replaceState' | 'state'>,
): string | null {
  const token = readLinkToken(location.hash);
  if (location.hash) {
    history.replaceState(history.state, '', `${location.pathname}${location.search}`);
  }
  return token;
}
