/** Central TanStack Query keys, so invalidation stays consistent. */
export const queryKeys = {
  session: ['auth', 'session'] as const,
  cart: ['cart'] as const,
  /** Prefix of everything that belongs to the logged-in user (cleared on logout). */
  account: ['account'] as const,
  profile: ['account', 'profile'] as const,
  addresses: ['account', 'addresses'] as const,
  orders: (page: number) => ['account', 'orders', 'list', page] as const,
  order: (orderNumber: string) => ['account', 'orders', 'detail', orderNumber] as const,
  /** Prefix of all order queries. */
  allOrders: ['account', 'orders'] as const,
  /** Shipping options and price quotes; they depend on the cart, the address and the method. */
  checkout: ['account', 'checkout'] as const,
  shippingMethods: (country: string) => ['account', 'checkout', 'shipping', country] as const,
  quote: (addressId: string, methodCode: string) =>
    ['account', 'checkout', 'quote', addressId, methodCode] as const,
  /** The Stripe payment form of an order (client secret and publishable key). */
  cardPayment: (orderNumber: string) => ['account', 'payments', 'card', orderNumber] as const,
  /** Prefix of everything in the admin area. */
  admin: ['admin'] as const,
  adminProducts: (query: Record<string, string>) => ['admin', 'products', 'list', query] as const,
  adminProduct: (id: string) => ['admin', 'products', 'detail', id] as const,
  adminCategories: ['admin', 'categories'] as const,
  adminSpecies: ['admin', 'species'] as const,
  adminColours: ['admin', 'colours'] as const,
  adminGrades: ['admin', 'grades'] as const,
  adminMovements: (productId: string, page: number) => ['admin', 'movements', productId, page] as const,
  adminDashboard: ['admin', 'dashboard'] as const,
  adminStats: (kind: 'sales' | 'products' | 'stock', from: string, to: string) =>
    ['admin', 'stats', kind, from, to] as const,
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
