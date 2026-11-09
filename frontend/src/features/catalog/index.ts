// Public API of the catalog feature for other features.
// Pages are imported by the router directly (see src/app/routes.tsx).
export { useProducts, type ProductSummary } from './api/catalog';
export { ProductGrid } from './components/ProductGrid';
