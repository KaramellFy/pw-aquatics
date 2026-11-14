import { ExternalLink } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { formatInstantDate } from '@/lib/format/date';
import { useIntlLocale } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import type { Order } from '../api/orders';

/** The parcels of an order with tracking number and link, as entered by the shop. */
export function ShipmentList({
  shipments,
  className,
}: {
  shipments: Order['shipments'];
  className?: string;
}) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  return (
    <ul className={cn('space-y-4', className)}>
      {shipments.map((shipment, index) => (
        <li key={shipment.id} className="rounded-xl border border-line bg-surface p-4">
          {shipments.length > 1 && (
            <p className="text-xs font-medium tracking-wide text-muted uppercase">
              {t('orders.tracking.parcel', { number: index + 1 })}
            </p>
          )}
          <p className="text-ink">
            {shipment.carrier} · <span className="tabular-nums">{shipment.trackingNumber}</span>
          </p>
          <p className="mt-1 text-sm text-ink-soft">
            {shipment.deliveredAt
              ? t('orders.tracking.deliveredOn', { date: formatInstantDate(shipment.deliveredAt, locale) })
              : t('orders.tracking.shippedOn', { date: formatInstantDate(shipment.shippedAt, locale) })}
          </p>
          {shipment.trackingUrl && (
            <a
              href={shipment.trackingUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-2 inline-flex min-h-11 items-center gap-2 text-sm font-medium text-brand-700 underline-offset-4 hover:underline"
            >
              {t('orders.tracking.track')}
              <ExternalLink aria-hidden className="size-4" />
              <span className="sr-only">{t('common.opensInNewTab')}</span>
            </a>
          )}
        </li>
      ))}
    </ul>
  );
}
