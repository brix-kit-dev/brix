import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RuntimeContextProvider } from '@brix-sdk/runtime-sdk-react';
import { HostedLoginApp } from './pages/HostedLoginApp';
import { createHostedLoginRepository } from './repositories/HostedLoginRepository';
import { createHostedLoginRuntime } from './runtime';
import './styles.css';

const root = document.getElementById('root');
if (root) {
  createRoot(root).render(
    <StrictMode>
      <RuntimeContextProvider value={createHostedLoginRuntime(navigator.languages ?? [navigator.language])}>
        <HostedLoginApp
          repository={createHostedLoginRepository()}
          location={window.location}
          navigate={(url) => window.location.assign(url)}
        />
      </RuntimeContextProvider>
    </StrictMode>,
  );
}
