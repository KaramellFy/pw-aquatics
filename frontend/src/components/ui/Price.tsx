import { useTranslation } from 'react-i18next';
import type { Money } from '@/lib/api/types';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import { cn } from '@/lib/cn';

interface PriceProps {
  value: Money;
  /** Previous price shown struck through (e.g. compare-at price). */
  compareAt?: Money | null;
  className?: string;
}

/** Displays a server-calculated price. Formatting only — never calculates. */
export function Price({ value, compareAt, className }: PriceProps) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  return (
    <span className={cn('inline-flex items-baseline gap-2 tabular-nums', className)}>
      <span>{formatMoney(value, locale)}</span>
      {compareAt && (
        <s className="text-sm text-muted">
          <span className="sr-only">{t('common.previousPrice')} </span>
          {formatMoney(compareAt, locale)}
        </s>
      )}
    </span>
  );
}
