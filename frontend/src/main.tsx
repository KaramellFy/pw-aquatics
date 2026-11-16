import '@fontsource-variable/outfit';
import '@fontsource-variable/inter';
import '@fontsource-variable/inter/wght-italic.css';
import './styles/index.css';
import './lib/i18n';

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import { createQueryClient } from './app/providers';
import { router } from './app/router';
import { takeOverServerPage, whenRouterReady } from './app/serverPage';

const container = document.getElementById('root');
if (!container) throw new Error('Root element #root not found');

const queryClient = createQueryClient();
takeOverServerPage(queryClient);

// The backend's text of this page stays visible until the app can show the page itself.
void whenRouterReady(router).then(() => {
  createRoot(container).render(
    <StrictMode>
      <App queryClient={queryClient} />
    </StrictMode>,
  );
});
