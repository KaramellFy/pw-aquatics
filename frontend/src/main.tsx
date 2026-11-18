import '@fontsource-variable/outfit';
import '@fontsource-variable/inter';
import '@fontsource-variable/inter/wght-italic.css';
import './styles/index.css';
import { i18nReady } from './lib/i18n';

import { StrictMode, startTransition } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import { createQueryClient } from './app/providers';
import { router } from './app/router';
import { dropStructuredDataOnLeave, takeOverServerPage, whenRouterReady } from './app/serverPage';

const container = document.getElementById('root');
if (!container) throw new Error('Root element #root not found');

const queryClient = createQueryClient();
takeOverServerPage(queryClient);
dropStructuredDataOnLeave(router);

// The backend's text of this page stays visible until the app can show the page itself.
// The first render is a transition, so React renders in small pieces and the page stays
// responsive (one long task would block taps and scrolling meanwhile).
void Promise.all([whenRouterReady(router), i18nReady]).then(() => {
  const root = createRoot(container);
  startTransition(() => {
    root.render(
      <StrictMode>
        <App queryClient={queryClient} />
      </StrictMode>,
    );
  });
});
