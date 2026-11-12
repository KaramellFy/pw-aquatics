import { useTranslation } from 'react-i18next';
import { Container } from '@/components/ui/Container';
import { shopConfig } from '@/config/shop';
import { useIntlLocale } from '@/lib/i18n';
import { cn } from '@/lib/cn';

const points = ['quarantine', 'grading', 'weather', 'species'] as const;

/**
 * How live animals travel, shown as the week itself: the days parcels leave are filled, the
 * rest stay empty. Below it the few rules behind it, as plain text rather than icon cards.
 */
export function ShippingWeek() {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  // 2024-01-01 was a Monday; only the weekday names are used.
  const dayName = (weekday: number, style: 'short' | 'long') =>
    new Intl.DateTimeFormat(locale, { weekday: style, timeZone: 'UTC' }).format(
      new Date(Date.UTC(2024, 0, weekday)),
    );
  const dispatch = new Set<number>(shopConfig.liveDispatchWeekdays);

  return (
    <section className="py-20 sm:py-28">
      <Container className="grid gap-12 lg:grid-cols-12 lg:gap-10">
        <div className="lg:col-span-5">
          <h2 className="text-headline text-balance">{t('home.shipping.title')}</h2>
          <p className="mt-6 max-w-md text-lg leading-relaxed text-pretty text-ink-soft">
            {t('home.shipping.text')}
          </p>
        </div>

        <div className="lg:col-span-7">
          <ol aria-label={t('home.shipping.weekLabel')} className="grid grid-cols-7 gap-1.5 sm:gap-2">
            {[1, 2, 3, 4, 5, 6, 7].map((weekday) => {
              const sends = dispatch.has(weekday);
              return (
                <li
                  key={weekday}
                  className={cn(
                    'flex aspect-[3/4] reveal-wipe flex-col justify-between rounded-lg p-2 sm:p-3',
                    sends ? 'bg-brand-800 text-white' : 'border border-line text-muted',
                  )}
                >
                  <span className="text-xs font-medium sm:text-sm">
                    <span aria-hidden>{dayName(weekday, 'short')}</span>
                    <span className="sr-only">{dayName(weekday, 'long')}</span>
                  </span>
                  <span
                    className={cn('text-[0.6875rem] leading-tight sm:text-xs', sends ? 'text-white/85' : '')}
                  >
                    {sends ? t('home.shipping.sends') : t('home.shipping.rests')}
                  </span>
                </li>
              );
            })}
          </ol>

          <dl className="mt-12 grid gap-x-10 gap-y-8 sm:grid-cols-2">
            {points.map((point) => (
              <div key={point}>
                <dt className="font-medium text-ink">{t(`home.shipping.points.${point}.title`)}</dt>
                <dd className="mt-1.5 leading-relaxed text-ink-soft">
                  {t(`home.shipping.points.${point}.text`)}
                </dd>
              </div>
            ))}
          </dl>
        </div>
      </Container>
    </section>
  );
}
