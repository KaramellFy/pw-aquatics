import { useTranslation } from 'react-i18next';
import { formatInstantDateTime } from '@/lib/format/date';
import { useIntlLocale } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import type { Order } from '../api/orders';

/** The steps an order has gone through, oldest first, as recorded by the server. */
export function OrderStatusTimeline({ entries }: { entries: Order['statusTimeline'] }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  return (
    <ol className="space-y-5">
      {entries.map((entry, index) => {
        const latest = index === entries.length - 1;
        return (
          <li key={`${entry.status}-${entry.at}`} className="relative flex gap-4">
            {!latest && (
              <span aria-hidden className="absolute top-5 bottom-[-1.25rem] left-[0.4375rem] w-px bg-line" />
            )}
            <span
              aria-hidden
              className={cn(
                'mt-1 size-4 shrink-0 rounded-full border-2',
                latest ? 'border-brand-700 bg-brand-400' : 'border-line-strong bg-surface',
              )}
            />
            <div>
              <p className={cn('leading-snug', latest ? 'font-medium text-ink' : 'text-ink-soft')}>
                {t(`orders.status.${entry.status}`)}
              </p>
              <p className="text-sm text-muted">
                <time dateTime={entry.at}>{formatInstantDateTime(entry.at, locale)}</time>
              </p>
              {entry.message && <p className="mt-1 text-sm text-ink-soft">{entry.message}</p>}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
