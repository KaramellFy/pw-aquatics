import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';
import type { Availability } from '../api/catalog';

const dot: Record<Availability, string> = {
  IN_STOCK: 'bg-success',
  LOW_STOCK: 'bg-warning',
  COMING_SOON: 'bg-brand-500',
  OUT_OF_STOCK: 'bg-line-strong',
};

const text: Record<Availability, string> = {
  IN_STOCK: 'text-success',
  LOW_STOCK: 'text-warning',
  COMING_SOON: 'text-brand-700',
  OUT_OF_STOCK: 'text-muted',
};

/** Availability as dot + label (never colour alone). The value comes from the API. */
export function AvailabilityBadge({
  availability,
  className,
}: {
  availability: Availability;
  className?: string;
}) {
  const { t } = useTranslation();
  return (
    <span className={cn('inline-flex items-center gap-2 text-sm font-medium', text[availability], className)}>
      <span aria-hidden className={cn('size-2 rounded-full', dot[availability])} />
      {t(`product.availability.${availability}`)}
    </span>
  );
}
