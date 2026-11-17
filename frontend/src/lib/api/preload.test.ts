import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';
import { preloadQueries, queryKeyForApiPath } from './preload';
import { queryKeys } from './queryKeys';

describe('API responses that come with the page', () => {
  it('belong to the queries the app runs first', () => {
    expect(queryKeyForApiPath('/products/blue-dream')).toEqual(queryKeys.product('blue-dream'));
    expect(queryKeyForApiPath('/species/neocaridina-davidi')).toEqual(
      queryKeys.species('neocaridina-davidi'),
    );
    expect(queryKeyForApiPath('/categories')).toEqual(queryKeys.categories);
    expect(queryKeyForApiPath('/products?category=shrimp&size=24')).toEqual(
      queryKeys.products({ category: 'shrimp', size: '24' }),
    );
    expect(queryKeyForApiPath('/products/facets')).toBeNull();
    expect(queryKeyForApiPath('/cart')).toBeNull();
  });

  it('fill the cache, whatever the order of the query parameters', () => {
    const client = new QueryClient();
    const list = { items: [], page: 0, size: 24, totalItems: 0, totalPages: 0 };
    preloadQueries(client, { '/products?size=24&category=shrimp': list, '/account/profile': { name: 'x' } });
    expect(client.getQueryData(queryKeys.products({ category: 'shrimp', size: '24' }))).toEqual(list);
    expect(client.getQueryCache().getAll()).toHaveLength(1);
  });
});
