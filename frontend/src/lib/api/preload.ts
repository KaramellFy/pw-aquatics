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
  const params = Object.fromEntries(new URLSearchParams(search));
  if (pathname === '/products') return queryKeys.products(params);
  if (pathname === '/products/facets') return queryKeys.facets(params);
  if (pathname === '/categories') return queryKeys.categories;
  if (pathname === '/species') return queryKeys.speciesList;
  if (pathname === '/colours') return queryKeys.colours;
  if (pathname === '/grades') return queryKeys.grades;
  const product = /^\/products\/([^/]+)$/.exec(pathname);
  if (product?.[1] && product[1] !== 'facets') return queryKeys.product(decodeURIComponent(product[1]));
  const species = /^\/species\/([^/]+)$/.exec(pathname);
  if (species?.[1]) return queryKeys.species(decodeURIComponent(species[1]));
  return null;
}

/**
 * The cache entry for a response: its query key and the data as the query keeps it. Lists the
 * app loads page by page (the gallery) keep their pages in `{ pages, pageParams }`.
 */
function cacheEntry(path: string, data: unknown): [QueryKey, unknown] | null {
  const [pathname = '', search = ''] = path.split('?');
  if (pathname === '/gallery') {
    const params = new URLSearchParams(search);
    if (params.get('page') !== '0') return null;
    return [queryKeys.gallery(Number(params.get('size'))), { pages: [data], pageParams: [0] }];
  }
  const key = queryKeyForApiPath(path);
  return key ? [key, data] : null;
}

/** Puts the page's API responses into the cache, so the first render already has them. */
export function preloadQueries(client: QueryClient, responses: Record<string, unknown>): void {
  for (const [path, data] of Object.entries(responses)) {
    const entry = cacheEntry(path, data);
    if (entry) client.setQueryData(entry[0], entry[1]);
  }
}
