import type { QueryClient } from '@tanstack/react-query';
import { preloadQueries } from '@/lib/api/preload';

/**
 * Takes over a page the backend filled in (docs/adr/013-pages-filled-in-by-the-backend.md): its
 * API responses go into the query cache and its head tags are removed, because the app's
 * `PageMeta` writes them from now on. The page's text stays until the app renders over it.
 */
export function takeOverServerPage(client: QueryClient, doc: Document = document): void {
  const data = doc.getElementById('page-data');
  if (data) {
    try {
      preloadQueries(client, JSON.parse(data.textContent ?? '{}') as Record<string, unknown>);
    } catch {
      // Unreadable data only costs a request: the app then loads it itself.
    }
    data.remove();
  }
  doc.head.querySelectorAll('[data-page]').forEach((element) => element.remove());
}

interface StartingRouter {
  state: { initialized: boolean };
  subscribe(listener: (state: { initialized: boolean }) => void): () => void;
}

/**
 * Resolves once the router has loaded the code of the first page. Rendering before that would
 * replace the backend's text with an empty screen while the page's code loads.
 */
export function whenRouterReady(router: StartingRouter): Promise<void> {
  if (router.state.initialized) return Promise.resolve();
  return new Promise((resolve) => {
    const unsubscribe = router.subscribe((state) => {
      if (state.initialized) {
        unsubscribe();
        resolve();
      }
    });
  });
}
