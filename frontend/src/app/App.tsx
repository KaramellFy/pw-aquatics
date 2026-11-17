import type { QueryClient } from '@tanstack/react-query';
import { RouterProvider } from 'react-router/dom';
import { AppProviders } from './providers';
import { router } from './router';

/** @param queryClient the cache, already holding the data of a page the backend filled in */
export function App({ queryClient }: { queryClient?: QueryClient }) {
  return (
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>
  );
}
