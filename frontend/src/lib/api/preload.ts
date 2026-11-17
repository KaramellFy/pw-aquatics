import type { QueryClient, QueryKey } from '@tanstack/react-query';
import { queryKeys } from './queryKeys';

/**
 * The query an API response belongs to, by the path the app requests it with
 * ("/products/blue-dream" → `queryKeys.product('blue-dream')`); null for paths the app does
 * not preload. Pages the backend fills in carry the responses the app asks for first
 * (docs/adr/013-pages-filled-in-by-the-backend.md).
 */
export function queryKeyForApiPath(path: string): QueryKey | null {
  const [pathname = '', search = ''] = path.split('?');
  if (pathname === '/products') return queryKeys.products(Object.fromEntries(new URLSearchParams(search)));
  if (pathname === '/categories') return queryKeys.categories;
  const product = /^\/products\/([^/]+)$/.exec(pathname);
  if (product?.[1] && product[1] !== 'facets') return queryKeys.product(decodeURIComponent(product[1]));
  const species = /^\/species\/([^/]+)$/.exec(pathname);
  if (species?.[1]) return queryKeys.species(decodeURIComponent(species[1]));
  return null;
}

/** Puts the page's API responses into the cache, so the first render already has them. */
export function preloadQueries(client: QueryClient, responses: Record<string, unknown>): void {
  for (const [path, data] of Object.entries(responses)) {
    const key = queryKeyForApiPath(path);
    if (key) client.setQueryData(key, data);
  }
}
