import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RuntimeContextProvider } from '@brix-sdk/runtime-sdk-react';
import { HostedLoginApp } from './pages/HostedLoginApp';
import { createHostedLoginRepository } from './repositories/HostedLoginRepository';
import { createHostedSetupRepository } from './repositories/HostedSetupRepository';
import { createHostedLoginRuntime } from './runtime';
import { takeLinkToken } from './linkToken';
import './styles.css';

// Before anything renders: the one-time token of a setup or invitation link moves from the URL
// fragment into memory and leaves the address bar and history (frontend BRX-FE-ROUTE-004).
const linkToken = takeLinkToken(window.location, window.history);

const root = document.getElementById('root');
if (root) {
  createRoot(root).render(
    <StrictMode>
      <RuntimeContextProvider value={createHostedLoginRuntime(navigator.languages ?? [navigator.language])}>
        <HostedLoginApp
          repository={createHostedLoginRepository()}
          setupRepository={createHostedSetupRepository()}
          location={window.location}
          navigate={(url) => window.location.assign(url)}
          linkToken={linkToken}
        />
      </RuntimeContextProvider>
    </StrictMode>,
  );
}
