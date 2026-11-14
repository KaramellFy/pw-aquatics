import { Truck } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { formatCalendarDate } from '@/lib/format/date';
import { useIntlLocale } from '@/lib/i18n';
import { useShopInfo } from '../api/shopInfo';

/**
 * A notice above the header while the owner has paused shipping of live animals (heat wave,
 * frost). Whether a pause is in force is decided by the server.
 */
export function ShippingPauseBanner() {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const info = useShopInfo();
  const pause = info.data?.shippingPause;
  if (!pause) return null;
  return (
    <aside aria-label={t('shippingPause.label')} className="border-b border-brand-200 bg-brand-50 text-ink">
      <div className="mx-auto flex max-w-[90rem] items-start gap-3 px-5 py-3 text-sm sm:px-8">
        <Truck aria-hidden className="mt-0.5 size-4 shrink-0 text-brand-700" />
        <p>
          <span className="font-medium">
            {pause.until
              ? t('shippingPause.until', { date: formatCalendarDate(pause.until, locale, 'long') })
              : t('shippingPause.open')}
          </span>
          {pause.message && <> {pause.message}</>}
        </p>
      </div>
    </aside>
  );
}
