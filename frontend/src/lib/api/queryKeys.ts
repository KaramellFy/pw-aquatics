/** Central TanStack Query keys, so invalidation stays consistent. */
export const queryKeys = {
  session: ['auth', 'session'] as const,
  cart: ['cart'] as const,
  /** Prefix of everything that belongs to the logged-in user (cleared on logout). */
  account: ['account'] as const,
  profile: ['account', 'profile'] as const,
  addresses: ['account', 'addresses'] as const,
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
