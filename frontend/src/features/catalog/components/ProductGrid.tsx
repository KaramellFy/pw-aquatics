import { AnimatePresence, m } from 'motion/react';
import { cn } from '@/lib/cn';
import { duration, ease, exitDuration, STAGGER } from '@/lib/motion/tokens';
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
  // When filters change, cards that stay glide to their new place, new ones arrive one after
  // another (30 ms apart, at most the first 12) and removed ones fade out quickly.
  return (
    <ul className={cn(grid, loading && 'opacity-60 transition-opacity')}>
      <AnimatePresence mode="popLayout">
        {products?.map((product, index) => (
          <m.li
            key={product.id}
            layout="position"
            initial={{ opacity: 0, y: 12 }}
            animate={{
              opacity: 1,
              y: 0,
              transition: { duration: duration.base, ease: ease.out, delay: Math.min(index, 12) * STAGGER },
            }}
            exit={{
              opacity: 0,
              scale: 0.97,
              transition: { duration: exitDuration(duration.fast), ease: ease.out },
            }}
            transition={{ layout: { duration: 0.3, ease: ease.inOut } }}
          >
            <ProductCard product={product} priority={index < 3} />
          </m.li>
        ))}
      </AnimatePresence>
    </ul>
  );
}
