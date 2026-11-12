import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';
import { formatBps } from '@/lib/format/number';

/**
 * A headline figure with its change against the previous period. The change comes from the
 * API (`changeBps`); colour says whether the direction is good, and an arrow plus words say
 * it too, so colour never carries the meaning alone.
 */
export function KpiTile({
  label,
  value,
  previous,
  changeBps,
  upIsGood = true,
  hint,
}: {
  label: string;
  value: string;
  /** The previous period's value, formatted. */
  previous?: string;
  changeBps?: number | null;
  upIsGood?: boolean;
  hint?: string;
}) {
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage ?? 'en';
  const direction =
    changeBps === undefined || changeBps === null
      ? null
      : changeBps > 0
        ? 'up'
        : changeBps < 0
          ? 'down'
          : 'flat';
  const good = direction === 'flat' || direction === null ? null : (direction === 'up') === upIsGood;
  const Icon = direction === 'up' ? ArrowUpRight : direction === 'down' ? ArrowDownRight : Minus;

  return (
    <div className="h-full rounded-card border border-line bg-surface p-4 sm:p-5">
      <p className="text-sm text-ink-soft">{label}</p>
      <p className="mt-2 text-xl leading-tight font-semibold break-words text-ink sm:text-[1.75rem]">
        {value}
      </p>
      {hint && <p className="mt-1 text-xs text-muted">{hint}</p>}
      {(direction !== null || previous) && (
        <p className="mt-3 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
          {direction !== null && changeBps !== undefined && changeBps !== null && (
            <span
              className={cn(
                'inline-flex items-center gap-0.5 font-medium',
                good === true && 'text-success',
                good === false && 'text-danger',
                good === null && 'text-ink-soft',
              )}
            >
              <Icon aria-hidden className="size-3.5" />
              {formatBps(changeBps, locale, { signed: true })}
              <span className="sr-only">
                {good === true
                  ? t('admin.dashboard.kpi.better')
                  : good === false
                    ? t('admin.dashboard.kpi.worse')
                    : ''}
              </span>
            </span>
          )}
          {previous && (
            <span className="text-muted">{t('admin.dashboard.kpi.previous', { value: previous })}</span>
          )}
        </p>
      )}
    </div>
  );
}
