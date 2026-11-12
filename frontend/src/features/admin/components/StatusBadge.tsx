import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';
import type { ProductStatus } from '../api/products';

const tones: Record<ProductStatus, string> = {
  DRAFT: 'border-line-strong bg-mist text-ink-soft',
  ACTIVE: 'border-success/30 bg-success-soft text-ink',
  ARCHIVED: 'border-line bg-surface text-muted',
};

/** A product's publication status in words. */
export function StatusBadge({ status, className }: { status: ProductStatus; className?: string }) {
  const { t } = useTranslation();
  return (
    <span
      className={cn(
        'inline-flex h-6 items-center rounded-full border px-2.5 text-xs font-medium whitespace-nowrap',
        tones[status],
        className,
      )}
    >
      {t(`admin.status.${status}`)}
    </span>
  );
}

/** A small neutral or warning pill, e.g. "Hidden" or "Trade restricted". */
export function Pill({
  children,
  tone = 'neutral',
}: {
  children: string;
  tone?: 'neutral' | 'warning' | 'ok';
}) {
  return (
    <span
      className={cn(
        'inline-flex h-6 items-center rounded-full border px-2.5 text-xs font-medium whitespace-nowrap',
        tone === 'warning' && 'border-danger/30 bg-danger-soft text-ink',
        tone === 'ok' && 'border-success/30 bg-success-soft text-ink',
        tone === 'neutral' && 'border-line-strong bg-mist text-ink-soft',
      )}
    >
      {children}
    </span>
  );
}
