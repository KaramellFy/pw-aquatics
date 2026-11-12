import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { ApiError } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import { MotionProvider } from '@/lib/motion/MotionProvider';

export function createQueryClient(): QueryClient {
  // When the server says the session is gone (expired, or ended on another device),
  // mark the user as signed out; route guards then send them to the login page.
  const handleError = (error: unknown) => {
    if (error instanceof ApiError && error.status === 401 && error.code === 'UNAUTHENTICATED') {
      client.setQueryData(queryKeys.session, { authenticated: false, user: null });
    }
  };
  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({ onError: handleError }),
    mutationCache: new MutationCache({ onError: handleError }),
    defaultOptions: {
      queries: {
        staleTime: 60_000,
        refetchOnWindowFocus: false,
        // Retry network hiccups and server errors once, never client errors (4xx).
        retry: (failureCount, error) =>
          failureCount < 1 && !(error instanceof ApiError && error.status >= 400 && error.status < 500),
      },
      mutations: { retry: false },
    },
  });
  return client;
}

export function AppProviders({ children }: { children: ReactNode }) {
  const [queryClient] = useState(createQueryClient);
  return (
    <QueryClientProvider client={queryClient}>
      <MotionProvider>{children}</MotionProvider>
    </QueryClientProvider>
  );
}
