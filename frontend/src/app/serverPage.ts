import type { QueryClient } from '@tanstack/react-query';
import { preloadQueries } from '@/lib/api/preload';

/** The backend's structured data (schema.org JSON-LD); the app writes none of its own. */
const STRUCTURED_DATA = 'script[type="application/ld+json"][data-page]';

/**
 * Takes over a page the backend filled in (docs/adr/013-pages-filled-in-by-the-backend.md): its
 * API responses go into the query cache and its head tags are removed, because the app's
 * `PageMeta` writes them from now on. Its structured data stays (see `dropStructuredDataOnLeave`).
 * The page's text stays until the app renders over it.
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
  doc.head
    .querySelectorAll('[data-page]')
    .forEach((element) => element.matches(STRUCTURED_DATA) || element.remove());
}

interface NavigatingRouter {
  state: { location: { pathname: string } };
  subscribe(listener: (state: { location: { pathname: string } }) => void): () => void;
}

/**
 * Removes the backend's structured data once the app shows another page, since it describes only
 * the page that was loaded. Search engines load every page on its own, so they always find it.
 */
export function dropStructuredDataOnLeave(router: NavigatingRouter, doc: Document = document): void {
  if (!doc.head.querySelector(STRUCTURED_DATA)) return;
  const first = router.state.location.pathname;
  const unsubscribe = router.subscribe((state) => {
    if (state.location.pathname === first) return;
    unsubscribe();
    doc.head.querySelectorAll(STRUCTURED_DATA).forEach((element) => element.remove());
  });
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
