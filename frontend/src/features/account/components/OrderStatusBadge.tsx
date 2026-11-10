import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';
import type { OrderStatus } from '../api/orders';

const tones: Record<OrderStatus, string> = {
  PENDING_PAYMENT: 'border-brand-200 bg-brand-50 text-brand-800',
  PAID: 'border-brand-200 bg-brand-50 text-brand-800',
  PROCESSING: 'border-brand-200 bg-brand-50 text-brand-800',
  SHIPPED: 'border-success/30 bg-success-soft text-ink',
  DELIVERED: 'border-success/30 bg-success-soft text-ink',
  CANCELLED: 'border-line-strong bg-mist text-ink-soft',
};

/** The order status in words (never colour alone). */
export function OrderStatusBadge({ status, className }: { status: OrderStatus; className?: string }) {
  const { t } = useTranslation();
  return (
    <span
      className={cn(
        'inline-flex h-7 items-center rounded-full border px-3 text-xs font-medium whitespace-nowrap',
        tones[status],
        className,
      )}
    >
      {t(`orders.status.${status}`)}
    </span>
  );
}
