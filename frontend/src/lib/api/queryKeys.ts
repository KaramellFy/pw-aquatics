/** Central TanStack Query keys, so invalidation stays consistent. */
export const queryKeys = {
  products: (query: Record<string, string>) => ['catalog', 'products', query] as const,
  facets: (query: Record<string, string>) => ['catalog', 'facets', query] as const,
  product: (slug: string) => ['catalog', 'product', slug] as const,
  related: (slug: string) => ['catalog', 'related', slug] as const,
  categories: ['catalog', 'categories'] as const,
  speciesList: ['catalog', 'species'] as const,
  species: (slug: string) => ['catalog', 'species', slug] as const,
  colours: ['catalog', 'colours'] as const,
  grades: ['catalog', 'grades'] as const,
};
