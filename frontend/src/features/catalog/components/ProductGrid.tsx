import { cn } from '@/lib/cn';
import type { ProductSummary } from '../api/catalog';
import { ProductCard, ProductCardSkeleton } from './ProductCard';

interface ProductGridProps {
  products: ProductSummary[] | undefined;
  loading?: boolean;
  /** Number of skeleton cards while loading. */
  skeletons?: number;
  columns?: 3 | 4;
  className?: string;
}

export function ProductGrid({
  products,
  loading = false,
  skeletons = 6,
  columns = 3,
  className,
}: ProductGridProps) {
  const grid = cn(
    'grid grid-cols-1 gap-x-6 gap-y-12 sm:grid-cols-2',
    columns === 3 ? 'xl:grid-cols-3' : 'lg:grid-cols-4',
    className,
  );
  if (loading && !products) {
    return (
      <div className={grid}>
        {Array.from({ length: skeletons }, (_, index) => (
          <ProductCardSkeleton key={index} />
        ))}
      </div>
    );
  }
  return (
    <ul className={cn(grid, loading && 'opacity-60 transition-opacity')}>
      {products?.map((product, index) => (
        <li key={product.id}>
          <ProductCard product={product} priority={index < 3} />
        </li>
      ))}
    </ul>
  );
}
