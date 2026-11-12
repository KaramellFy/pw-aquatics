import { QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { createQueryClient } from '@/app/providers';
import { MotionProvider } from '@/lib/motion/MotionProvider';

/** Renders a component inside a memory router and a fresh QueryClient. */
export function renderWithProviders(ui: ReactElement, { path = '/' }: { path?: string } = {}) {
  return renderRoutes([{ path: '*', element: ui }], { path });
}

export function renderRoutes(routes: RouteObject[], { path = '/' }: { path?: string } = {}) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const queryClient = createQueryClient();
  return {
    router,
    ...render(
      <QueryClientProvider client={queryClient}>
        <MotionProvider>
          <RouterProvider router={router} />
        </MotionProvider>
      </QueryClientProvider>,
    ),
  };
}
