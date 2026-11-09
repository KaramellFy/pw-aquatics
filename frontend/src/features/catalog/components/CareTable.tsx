import { useTranslation } from 'react-i18next';
import { useIntlLocale } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import type { SpeciesDetail } from '../api/catalog';

type Care = SpeciesDetail['care'];

/** Species care parameters as a definition list; rows without data are left out. */
export function CareTable({ species, className }: { species: SpeciesDetail; className?: string }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const care: Care = species.care;
  const decimal = new Intl.NumberFormat(locale, { maximumFractionDigits: 1 });
  const range = (min: number | null, max: number | null, unit = '') => {
    if (min === null && max === null) return null;
    const text =
      min !== null && max !== null && min !== max
        ? `${decimal.format(min)}–${decimal.format(max)}`
        : decimal.format((min ?? max) as number);
    return unit ? `${text} ${unit}` : text;
  };

  const rows: [string, string | null][] = [
    [t('care.temperature'), range(care.tempMinC, care.tempMaxC, '°C')],
    [t('care.ph'), range(care.phMin, care.phMax)],
    [t('care.gh'), range(care.ghMin, care.ghMax, '°dGH')],
    [t('care.kh'), range(care.khMin, care.khMax, '°dKH')],
    [t('care.tds'), range(care.tdsMin, care.tdsMax, 'ppm')],
    [t('care.size'), care.adultSizeMm !== null ? t('care.millimetres', { count: care.adultSizeMm }) : null],
    [
      t('care.lifespan'),
      care.lifespanMonths !== null ? t('care.months', { count: care.lifespanMonths }) : null,
    ],
    [t('care.difficulty'), care.difficulty ? t(`care.levels.${care.difficulty}`) : null],
    [t('care.origin'), species.origin],
  ];

  return (
    <dl className={cn('divide-y divide-line border-y border-line', className)}>
      {rows
        .filter((row): row is [string, string] => row[1] !== null)
        .map(([label, value]) => (
          <div key={label} className="grid grid-cols-[minmax(8rem,40%)_1fr] gap-4 py-3 text-[0.9375rem]">
            <dt className="text-muted">{label}</dt>
            <dd className="tabular-nums">{value}</dd>
          </div>
        ))}
    </dl>
  );
}
